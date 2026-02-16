# ZTicket - 대용량 선착순 좌석 티켓 구매 시스템

수백만 명 동시 접속 상황에서 선착순 좌석 티켓 구매를 처리하는 시스템입니다.
Redis 기반 대기열과 2-Phase 상태 전이 + 동기화 워커를 통해
**이중 판매 없는 정합성**과 **높은 처리량**을 동시에 달성합니다.

---

## 목차

1. [기술 스택](#기술-스택)
2. [실행 방법](#실행-방법)
3. [아키텍처 Overview](#아키텍처-overview)
4. [핵심 플로우](#핵심-플로우)
5. [Redis-DB 동기화 전략](#redis-db-동기화-전략)
6. [설계 결정과 트레이드오프](#설계-결정과-트레이드오프)
7. [패키지 구조](#패키지-구조)
8. [API 명세](#api-명세)
9. [Redis 키 설계](#redis-키-설계)
10. [설정값](#설정값)
11. [부하 테스트 (k6)](#부하-테스트-k6)
12. [모니터링 (Prometheus + Grafana)](#모니터링-prometheus--grafana)

---

## 기술 스택

| 구분 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Framework | Spring Boot | 4.0.2 |
| ORM | Spring Data JPA + Hibernate | - |
| Database | MySQL | 8.0 |
| Cache/Queue | Redis | 7 |
| Template | Thymeleaf | - |
| Build | Gradle | 9.3 |
| Container | Docker Compose | - |

---

## 실행 방법

### Docker Compose (권장)

```bash
# 전체 실행 (MySQL + Redis + App)
docker compose up -d

# 브라우저 접속
open http://localhost:8080
```

### 로컬 개발

```bash
# 1. 인프라만 실행 (MySQL + Redis)
docker compose up -d mysql redis

# 2. 애플리케이션 실행
./gradlew bootRun

# 3. 브라우저 접속
open http://localhost:8080
```

---

## 아키텍처 Overview

### 헥사고날 아키텍처 (Ports and Adapters)

```
                    ┌─────────────────────────────────┐
   HTTP Request ──▶ │  Adapter IN (Controller/Scheduler)│
                    └───────────┬─────────────────────┘
                                │ UseCase 인터페이스 호출
                                ▼
                    ┌─────────────────────────────────┐
                    │       Application Service         │
                    │   (유스케이스 오케스트레이션)       │
                    └───────────┬─────────────────────┘
                                │ Port OUT 인터페이스 호출
                                ▼
                    ┌─────────────────────────────────┐
                    │  Adapter OUT (Redis / JPA)        │
                    └───────────┬─────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
                 Redis                   MySQL
```

**핵심 원칙**: domain 패키지는 순수 Java로만 구성되며, Spring/JPA/Redis 등 프레임워크 의존성이 전혀 없습니다. 모든 외부 기술은 adapter 패키지에서 port 인터페이스를 구현하는 방식으로 연결됩니다.

---

## 핵심 플로우

### 1. 대기열 플로우

```
사용자                  QueueService              Redis
  │                        │                       │
  │── POST /api/queue/token ──▶ enter()             │
  │                        │── ZADD waiting_queue ──▶│
  │◀── { uuid, rank } ──── │◀── rank ───────────────│
  │                        │                       │
  │── GET /status/{uuid} ──▶ getStatus()            │
  │  (2초 폴링)            │── ZRANK ──────────────▶│
  │◀── WAITING/ACTIVE ──── │◀── rank ───────────────│
  │                        │                       │

        AdmissionScheduler (매 1초)
              │
              │── countActive() → 현재 active 유저 수 확인
              │── toAdmit = min(batchSize, maxActive - currentActive)
              │── admitBatch(toAdmit) ─▶ ZRANGE + ZREM + SET active_user EX 300
```

### 2. 구매 플로우 (5단계 2-Phase)

```
클라이언트         TicketService            Redis                MySQL
  │                    │                      │                     │
  │── purchase ───────▶│                      │                     │
  │                    │                      │                     │
  │                    │── 1. 활성 사용자 검증 │                     │
  │                    │   isActive(token) ──▶│                     │
  │                    │◀── true ─────────────│                     │
  │                    │                      │                     │
  │                    │── 2. holdSeat ──────▶│                     │
  │                    │   SET seat:N          │                     │
  │                    │   "held:{token}"      │                     │
  │                    │   NX EX 300           │                     │
  │                    │◀── 성공/실패 ─────────│                     │
  │                    │                      │                     │
  │                    │── 3. save(PAID) ─────│────────────────────▶│
  │                    │                      │   INSERT ticket     │
  │                    │                      │   (status=PAID)     │
  │                    │                      │                     │
  │                    │── 4. paySeat ───────▶│                     │
  │                    │   Lua: held→paid      │                     │
  │                    │                      │                     │
  │                    │── 5. sync() + save ──│────────────────────▶│
  │                    │                      │   UPDATE SYNCED     │
  │◀── 완료 ──────────│                      │                     │
```

### 3. 동기화 워커 플로우

```
SyncScheduler (매 1분)
  │
  ├── DB에서 status=PAID 티켓 조회
  │
  └── 각 PAID 티켓에 대해:
        │
        ├── Redis SET seat:{n} "paid:{token}"
        │   (held/null 어떤 상태든 paid로 덮어씀, SET이 기존 TTL 자동 제거)
        │
        └── DB UPDATE status=SYNCED
```

---

## Redis-DB 동기화 전략

### 좌석 상태 흐름

```
[키 없음 = available] ──▶ held:{token} (TTL 300초) ──▶ paid:{token} (TTL 없음, 영구)
```

이 시스템의 가장 어려운 문제는 **Redis와 DB 간의 상태 불일치**입니다. 두 저장소에 걸친 연산은 분산 트랜잭션이 불가능하므로, 장애 시나리오별 대응 전략을 설계했습니다.

### Case 1: Redis held 성공 → DB INSERT 실패

```
상태: Redis에 held:{token} (TTL 째깍째깍) + DB 레코드 없음
위험: 좌석이 5분간 불필요하게 점유됨
해결: catch 블록에서 즉시 DEL seat:{n} 롤백
안전망: 롤백마저 실패해도 TTL 5분이 자동 해제 → 일시적 불편일 뿐 이중 판매 없음
```

### Case 2: Redis held + DB PAID 성공 → Redis paid 전환 중 서버 사망

```
상태: DB에 PAID 레코드 있음 + Redis에 held:{token} (TTL 째깍째깍)
위험: TTL 만료 → 다른 사용자가 같은 좌석 hold 가능
해결: 동기화 워커가 1분마다 PAID 조회 → setPaidSeat으로 Redis에 paid:{token} 복원
안전망: TTL 만료 전에 동기화 워커가 처리. 설령 TTL 만료 후 다른 사용자가 hold해도
       DB tickets 테이블의 seatNumber UNIQUE 제약 때문에 해당 사용자의 DB 저장이 실패함.
       따라서 setPaidSeat으로 덮어써도 이중 판매 없음.
```

### Case 3: Redis paid + DB UPDATE SYNCED 중 서버 사망

```
상태: Redis에 paid:{token} (영구) + DB에 PAID 레코드
위험: DB만 PAID이지만 좌석은 이미 확보됨 → 이중 판매 없음
해결: 동기화 워커가 setPaidSeat 재실행 → 이미 paid이므로 동일한 결과 → DB SYNCED
```

### 왜 이 전략이 안전한가?

| 장애 시점 | Redis 상태 | DB 상태 | 이중 판매? | 자동 복구? |
|-----------|-----------|---------|-----------|-----------|
| 2단계 이후 | held (TTL) | 없음 | 불가능 (즉시 롤백 또는 TTL 만료) | TTL 자동 해제 |
| 3단계 이후 | held (TTL) | PAID | 불가능 (UNIQUE 제약) | 동기화 워커 |
| 4단계 이후 | paid | PAID | 불가능 (영구 점유) | 동기화 워커가 DB만 갱신 |
| 5단계 이후 | paid | SYNCED | 불가능 | 이미 완료 |

**핵심 불변식**:
- `동기화 워커 주기(1분) < hold TTL(5분)` → TTL 만료 전 동기화 완료
- `seatNumber UNIQUE 제약` → TTL 만료 후 다른 사용자가 hold해도 DB 저장 실패 → 이중 판매 원천 차단

---

## 설계 결정과 트레이드오프

### 1. 아키텍처: 헥사고날 vs 레이어드

#### 선택: 헥사고날 아키텍처 (Ports and Adapters) + 도메인 단위 패키지

```
queue/           → 대기열 도메인 (domain + application + adapter)
seat/            → 좌석 도메인 (domain + application + adapter)
ticket/          → 티켓 도메인 (domain + application + adapter)
각 도메인 내:
  domain/        → 순수 Java (QueueToken, Ticket, SeatStatus)
  application/   → 유스케이스 오케스트레이션 + Port 인터페이스
  adapter/       → Controller, Scheduler, Redis/JPA Adapter
```

**채택 이유**:
- **도메인 순수성**: `Ticket.java`에 `@Entity`, `@Column` 같은 JPA 어노테이션이 없습니다. 도메인 로직이 인프라 기술에 오염되지 않으므로, Redis를 Memcached로, MySQL을 PostgreSQL로 교체해도 domain 패키지는 한 줄도 수정하지 않습니다.
- **테스트 용이성**: `TicketService`는 `SeatHoldPort`와 `TicketPersistencePort` 인터페이스에만 의존합니다. 단위 테스트에서 이 포트를 Mock으로 교체하면 Redis/MySQL 없이도 구매 로직 5단계를 완벽히 테스트할 수 있습니다.
- **의존성 방향 제어**: 모든 의존성이 domain을 향합니다 (`adapter → application → domain`). domain은 어디에도 의존하지 않습니다.

**트레이드오프**:
- **파일 수 증가**: `SeatHoldPort`(인터페이스) + `SeatHoldRedisAdapter`(구현)처럼 인터페이스-구현 쌍이 반드시 필요합니다. 레이어드라면 `SeatHoldService` 하나로 끝납니다.
- **간접 참조 비용**: Controller → UseCase 인터페이스 → Service → Port 인터페이스 → Adapter. 호출 체인이 길어져 코드를 따라가기 어려울 수 있습니다.
- **JPA 엔티티 분리로 인한 매핑 코드**: `TicketJpaEntity.fromDomain()`과 `toDomain()` 같은 변환 코드가 추가됩니다.

---

### 2. 대기열: Redis Sorted Set vs 메시지 큐 (Kafka/RabbitMQ)

#### 선택: Redis Sorted Set (`ZADD`, `ZRANK`, `ZRANGE + ZREM`)

**채택 이유**:
- **실시간 순번 조회**: `ZRANK`는 O(log N)으로 즉시 현재 순번을 반환합니다. 클라이언트가 2초마다 폴링할 때 "현재 347번째"를 바로 응답할 수 있습니다. Kafka에서는 consumer offset으로 순번을 계산하는 것이 불가능에 가깝습니다.
- **배치 입장의 단순성**: `ZRANGE(0, 59)` + `ZREM`으로 상위 60명을 원자적으로 추출합니다.
- **인프라 단순성**: 이미 좌석 선점용으로 Redis를 사용하므로, 별도 인프라를 추가하지 않습니다.

**트레이드오프**:
- **메모리 한계**: 1,000만 명이면 멤버당 ~100bytes, 총 ~1GB. 1억 명이면 Redis Cluster 필요.
- **영속성 부재**: Redis 장애 시 대기열 유실. 다만 선착순 대기열은 일시적 데이터이므로 재진입이 합리적.
- **순서 보장 범위**: `System.currentTimeMillis()` 기반 score는 밀리초 내 동시 요청 시 순서가 불확실하지만, 밀리초 내 차이는 공정성 기준에서 무시 가능.

---

### 3. 1티켓-1좌석: 단일 좌석 vs 다중 좌석 선택

#### 선택: 1티켓 = 1좌석 (`seatNumber: int`)

```java
// Ticket.java
private final int seatNumber;  // List<Integer> seatNumbers가 아님
```

**채택 이유**:
- **본질에 집중**: 이 프로젝트의 핵심은 대용량 동시 접속 환경에서의 선착순 티켓 구매 시스템입니다. 다중 좌석 선택은 부가 기능이지 핵심 도메인이 아닙니다. 대기열 관리, Redis-DB 동기화, 이중 판매 방지 등 핵심 문제에 집중하기 위해 부가적인 기능을 최소화했습니다.

**트레이드오프**:
- **다중 좌석 UX 불가**: 가족석 4장을 한 번에 선택하는 UX가 불가능합니다. 하지만 여러 좌석이 필요하면 여러 번 구매하면 되고, 각 호출이 독립적이므로 병렬 처리도 가능합니다.

---

### 4. 좌석 선점: setIfAbsent vs Lua Script

#### 선택: Redis `SET NX EX` (Spring `setIfAbsent`) — 단일 좌석 선점

```java
// SeatHoldRedisAdapter.java - holdSeat
Boolean success = redisTemplate.opsForValue()
        .setIfAbsent(key, "held:" + uuid, ttlSeconds, TimeUnit.SECONDS);
```

**채택 이유**:
- **1티켓-1좌석이므로 Lua 불필요**: 단일 키에 대한 `SET NX EX`는 Redis가 원자적으로 보장합니다. 다중 좌석의 all-or-nothing을 보장하려면 Lua가 필요하지만, 단일 좌석이면 네이티브 명령으로 충분합니다.
- **구현 단순성**: 별도 `.lua` 파일과 `DefaultRedisScript` 빈 없이 Spring Data Redis API만으로 완성됩니다.
- **디버깅 용이성**: Lua 스크립트는 Redis 서버 내부에서 실행되어 Java 스택 트레이스로 추적이 어렵습니다. 네이티브 명령은 일반적인 Redis 에러로 처리됩니다.

**Lua Script는 어디에 사용하나?**

`pay-seat.lua`는 held→paid 전환에만 사용합니다. "현재 값이 정확히 `held:{token}`인 경우에만 `paid:{token}`으로 교체"라는 check-and-set은 단일 명령으로 불가능하기 때문입니다.

```lua
-- pay-seat.lua: held → paid 원자적 전환
local current = redis.call('GET', KEYS[1])
if current ~= ARGV[1] then return 1 end    -- 내 hold가 아님
redis.call('SET', KEYS[1], ARGV[2])         -- SET이 기존 TTL 자동 제거
return 0
```

**트레이드오프**:
- **Redis Cluster 호환**: 단일 키 연산이므로 Hash Tag 없이도 Cluster에서 동작합니다.
- **Lua 스크립트 1개 유지**: 선점(hold)은 네이티브, 결제 확정(pay)은 Lua. 두 가지 방식이 혼재하지만, 각각의 요구사항에 맞는 최적 선택입니다.

---

### 5. 상태 전이: 2-Phase (held → paid) + 동기화 워커

#### 선택: 2-Phase 상태 전이 + TTL 안전망 + DB UNIQUE 제약

```
① Redis SET seat:7 "held:{token}" NX EX 300    ← 임시 선점, TTL 5분
② DB INSERT ticket (status=PAID)                ← 결제 완료 기록
③ Redis SET seat:7 "paid:{token}"               ← 영구 확정 (SET이 TTL 자동 제거)
④ DB UPDATE ticket SET status=SYNCED            ← 동기화 완료
```

**채택 이유**:
- **장애 안전성**: ①~② 사이에 서버가 죽으면 DB 레코드가 없으므로 TTL 만료로 자동 롤백. ②~③ 사이에 죽으면 동기화 워커가 처리. 어떤 지점에서 죽어도 이중 판매가 불가능합니다.
- **TTL이 Safety Net**: held 상태에 TTL을 설정하면, 최악의 경우에도 5분 뒤 좌석이 자동으로 풀립니다.
- **DB UNIQUE가 최종 방어선**: TTL 만료 후 다른 사용자가 같은 좌석을 hold할 수 있지만, DB `seatNumber UNIQUE` 제약이 INSERT를 거부합니다. Redis TTL + DB UNIQUE의 이중 방어입니다.

**트레이드오프**:
- **구현 복잡도**: 5단계 오케스트레이션 + 동기화 워커가 필요합니다.
- **일시적 불일치 허용**: ②~④ 사이에 DB는 PAID인데 Redis는 held입니다. 이 간극에 좌석 현황 조회 시 "held"로 표시되지만, 실용적 문제는 없습니다.
- **동기화 워커 의존성**: 워커가 죽으면 PAID가 영원히 남습니다. 프로덕션에서는 워커 헬스체크가 필수입니다.
- **덮어쓰기 정책**: 동기화 워커의 `setPaidSeat`은 기존 Redis 값을 무조건 덮어씁니다. TTL 만료 후 다른 사용자가 hold한 키를 덮어쓸 수 있지만, 그 사용자는 어차피 DB UNIQUE 때문에 결제를 완료할 수 없으므로 문제없습니다.
- **최대 1분 지연**: 동기화 워커가 1분 주기이므로, 장애 발생 후 최대 ~1분간 PAID 상태가 유지됩니다. 사용자는 이미 구매 완료 응답을 받았으므로 체감 영향은 없습니다.

---

### 6. 영속화 패턴: OO `save(ticket)` vs 절차적 `updateStatus(uuid, status)`

#### 선택: 도메인 엔티티에서 상태 전이 후 save

```java
// TicketService.java - 상태 전이는 도메인 엔티티가 담당
ticket.sync();                       // Ticket 내부에서 PAID→SYNCED 전환 + 유효성 검증
ticketPersistencePort.save(ticket);  // 변경된 도메인 객체를 그대로 저장
```

```java
// TicketJpaAdapter.java - Upsert 패턴
public Ticket save(Ticket ticket) {
    TicketJpaEntity entity = ticket.getId() != null
            ? repository.findById(ticket.getId())
                    .map(existing -> { existing.update(ticket); return existing; })
                    .orElseThrow(() -> new IllegalStateException(
                            "티켓을 찾을 수 없습니다: id=" + ticket.getId()))
            : TicketJpaEntity.fromDomain(ticket);
    return repository.save(entity).toDomain();
}
```

**채택 이유**:
- **도메인 로직 캡슐화**: 상태 전이 규칙(PAID→SYNCED만 허용)이 `Ticket.sync()` 메서드 안에 있습니다. `updateStatus(uuid, SYNCED)`는 어디서든 아무 상태로나 변경할 수 있어 도메인 불변식이 깨질 수 있습니다.
- **Port 인터페이스 단순화**: `TicketPersistencePort`에 `save`, `findByUuid`, `findByStatus` 3개 메서드만 있습니다. `updateStatus`가 없으므로 포트가 더 범용적입니다.
- **Upsert 패턴**: 같은 `save()` 메서드로 INSERT(첫 저장)와 UPDATE(상태 변경)를 모두 처리합니다.

**트레이드오프**:
- **Upsert의 추가 SELECT**: 매 save마다 `findById`를 먼저 실행합니다. 직접 `UPDATE ... WHERE id = ?`보다 한 번의 SELECT가 추가됩니다. 하지만 PK 조회이므로 성능 차이는 미미합니다.
- **도메인 엔티티 외부 수정 불가**: `ticket.setStatus()`가 없으므로 테스트에서 임의 상태를 주입하려면 생성자를 사용해야 합니다.

---

### 7. DB 이중 판매 방어: seatNumber UNIQUE 제약

#### 선택: `tickets.seat_number` 컬럼에 UNIQUE 제약

```java
// TicketJpaEntity.java
@Column(nullable = false, unique = true)
private int seatNumber;
```

**채택 이유**:
- **DB 레벨 최종 방어선**: Redis TTL 만료 후 동기화 워커가 실행되기 전, 다른 사용자가 같은 좌석을 hold하고 DB INSERT를 시도하면 UNIQUE 위반으로 실패합니다. Redis의 TTL만으로는 방어할 수 없는 시간 갭을 DB가 막아줍니다.
- **데이터 정합성 보장**: 같은 좌석에 대해 두 개의 티켓이 DB에 존재하는 것이 물리적으로 불가능합니다. 애플리케이션 로직에 버그가 있어도 DB가 이중 판매를 차단합니다.

**트레이드오프**:
- **공연별 좌석 관리 불가**: 현재 단일 공연을 가정하므로 `seatNumber` UNIQUE만으로 충분합니다. 다중 공연이면 `(eventId, seatNumber)` 복합 UNIQUE가 필요합니다.
- **UNIQUE 위반 시 예외 처리**: DB에서 `DataIntegrityViolationException`이 발생하면 catch해서 적절한 에러 응답을 반환해야 합니다.

---

### 8. 대기열 입장: peek → activate → remove 3단계

#### 잠수 유저 처리

대기열에서 입장한 뒤 아무 행동도 하지 않는 잠수 유저는 `active_user:{uuid}` 키의 TTL(300초)로 자연 회수됩니다. 구매하지 않으면 5분 뒤 자동 만료되어 슬롯이 반환되므로, 별도 heartbeat 없이도 시스템이 자체 치유됩니다.

#### 입장 제어 (admitBatch)

```java
// QueueService.java
long currentActive = activeUserPort.countActive();
int slotsAvailable = (int) Math.max(0, maxActiveUsers - currentActive);
int toAdmit = Math.min(batchSize, slotsAvailable);

List<String> candidates = waitingQueuePort.peekBatch(toAdmit);   // 1. 조회만 (삭제 안 함)
for (String uuid : candidates) {
    activeUserPort.activate(uuid, activeTtlSeconds);              // 2. active_user 키 생성
}
waitingQueuePort.removeBatch(candidates);                         // 3. 큐에서 제거
```

**입장 제어**: 매 주기마다 현재 active 유저 수를 확인하고, `maxActiveUsers(500) - currentActive` 만큼만 입장시킵니다. 좌석 수 이상의 active 유저를 넣는 것은 의미가 없으므로, active 슬롯에 빈자리가 생길 때만 대기열에서 꺼냅니다. 잠수 유저가 TTL 만료로 빠지면 그만큼 다음 주기에 새로운 유저가 입장합니다.

**active 유저 카운트 — SCAN 사용 이유**: `active_user:{uuid}` 패턴의 키 수를 세야 하는데, Redis는 키를 해시 테이블에 저장하므로 접두사 기반 인덱스가 없습니다. 패턴으로 키를 찾으려면 전체 키스페이스를 순회할 수밖에 없습니다. `KEYS`는 순회 중 Redis를 블로킹하지만, `SCAN`은 커서 기반으로 논블로킹 순회합니다. `SCAN`은 커서 사이에 키가 추가/삭제되면 정확한 값을 보장하지 않지만, 입장 제어에는 정확한 수가 필요하지 않습니다. 다소 많거나 적게 입장시켜도 다음 주기(1초)에 보정되기 때문입니다.

키스페이스가 커지면(수만 개 이상) `SCAN`도 부담이 될 수 있으며, 이 경우 Sorted Set(score=만료 시각)으로 교체하면 `ZCARD` O(1)로 카운트할 수 있습니다. 단, TTL 자동 만료 대신 만료 시각을 직접 관리해야 하는 복잡도가 추가됩니다.

**최종적 일관성**: peek → activate → remove 3단계를 의도적으로 분리했습니다.

- **peek (삭제 안 함)**: 큐에서 꺼내지 않고 조회만 합니다. 여기서 서버가 죽어도 대기열에 그대로 남아있어 유실이 없습니다.
- **activate**: `active_user:{uuid}` 키를 생성합니다. 멱등 연산이므로 재실행해도 TTL만 갱신됩니다. 여기서 서버가 죽으면 다음 주기에 같은 사용자를 다시 peek → activate하므로 문제없습니다.
- **remove**: activate가 완료된 후에야 큐에서 제거합니다. 이 순서가 보장되므로 "큐에서는 빠졌는데 active는 안 된" 상태가 발생하지 않습니다.

---

### 9. 클라이언트 통신: 폴링 vs WebSocket vs SSE

#### 선택: 2초 주기 HTTP 폴링

**채택 이유**:
- **인프라 단순성**: 별도 WebSocket 서버나 SSE 설정 없이 기존 REST API만으로 동작합니다.
- **로드밸런서 호환**: HTTP 요청은 어떤 로드밸런서/CDN과도 호환됩니다.
- **연결 비용 분산**: 수십만 명이 폴링해도 각 요청은 독립적이며, 커넥션 풀에서 즉시 반환됩니다.

**트레이드오프**:
- **불필요한 요청**: 순번 변화가 없어도 2초마다 요청을 보냅니다. 대기자 50만 명 × 0.5 req/s = 25만 req/s.
- **최대 2초 지연**: 입장이 허용된 직후부터 최대 2초 후에야 클라이언트가 인지합니다.

---

### 10. 스케줄러: Spring @Scheduled (단일 JVM)

#### 선택: Spring @Scheduled + ThreadPoolTaskScheduler(풀 사이즈 2)

```java
// SchedulerConfig.java
scheduler.setPoolSize(2);  // AdmissionScheduler + SyncScheduler
```

**채택 이유**:
- 대용량 트래픽 처리라는 핵심 도메인에 집중하기 위해 스케줄러는 가장 단순한 방식으로 구성했습니다. 별도 인프라 없이 Spring `@Scheduled`만으로 동작합니다.

**프로덕션 고려사항**:
- 서버 이중화 시 ShedLock 등의 분산 스케줄러 도입이 필요합니다. 현재 구조에서는 서버를 여러 대로 스케일아웃하면 스케줄러가 인스턴스 수만큼 중복 실행됩니다.

---

## 패키지 구조

도메인(queue, seat, ticket)이 최상위 패키지가 되고, 각 도메인 안에 레이어(domain, application, adapter)가 배치되는 구조입니다.

```
kr.jemi.zticket
├── ZticketApplication.java                     @EnableScheduling
│
├── queue/                                      대기열 도메인 (독립)
│   ├── domain/
│   │   ├── QueueToken.java                     record(uuid, rank, status)
│   │   └── QueueStatus.java                    enum: WAITING, ACTIVE, SOLD_OUT
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── EnterQueueUseCase.java
│   │   │   │   ├── GetQueueStatusUseCase.java
│   │   │   │   └── AdmitUsersUseCase.java
│   │   │   └── out/
│   │   │       ├── WaitingQueuePort.java
│   │   │       └── ActiveUserPort.java
│   │   └── QueueService.java
│   └── adapter/
│       ├── in/
│       │   ├── web/
│       │   │   ├── QueueApiController.java     /api/queues/tokens/**
│       │   │   └── dto/
│       │   │       ├── TokenResponse.java
│       │   │       └── QueueStatusResponse.java
│       │   └── scheduler/
│       │       └── AdmissionScheduler.java     1초 배치 입장
│       └── out/
│           └── redis/
│               ├── WaitingQueueRedisAdapter.java
│               └── ActiveUserRedisAdapter.java
│
├── seat/                                       좌석 도메인 (독립)
│   ├── domain/
│   │   ├── SeatStatus.java                     enum: AVAILABLE, HELD, PAID, UNKNOWN
│   │   └── SeatStatuses.java                   좌석 상태 맵 래퍼
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   └── GetSeatsUseCase.java
│   │   │   └── out/
│   │   │       └── SeatHoldPort.java           hold/pay/release/setPaid/getStatuses
│   │   └── SeatService.java                    좌석 현황 조회
│   └── adapter/
│       ├── in/
│       │   └── web/
│       │       ├── SeatApiController.java      /api/seats, /api/seats/available-count
│       │       └── dto/
│       │           ├── SeatStatusResponse.java
│       │           └── AvailableCountResponse.java
│       └── out/
│           └── redis/
│               └── SeatHoldRedisAdapter.java   holdSeat(setIfAbsent) + paySeat(Lua)
│
├── ticket/                                     티켓 도메인 (→ queue, seat 의존)
│   ├── domain/
│   │   ├── Ticket.java                         도메인 엔티티
│   │   └── TicketStatus.java                   enum: HELD, PAID, SYNCED
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── PurchaseTicketUseCase.java  purchase(queueToken, seatNumber)
│   │   │   │   └── SyncTicketUseCase.java      syncPaidTickets()
│   │   │   └── out/
│   │   │       └── TicketPersistencePort.java  save/findByUuid/findByStatus
│   │   ├── TicketService.java                  5단계 구매 오케스트레이션
│   │   └── TicketSyncService.java              PAID → Redis 동기화
│   └── adapter/
│       ├── in/
│       │   ├── web/
│       │   │   ├── TicketApiController.java    /api/tickets
│       │   │   └── dto/
│       │   │       ├── PurchaseRequest.java    { seatNumber: 7 }
│       │   │       └── PurchaseResponse.java
│       │   └── scheduler/
│       │       └── SyncScheduler.java          1분 PAID 동기화
│       └── out/
│           └── persistence/
│               ├── TicketJpaEntity.java         seatNumber UNIQUE
│               ├── TicketJpaRepository.java
│               └── TicketJpaAdapter.java        Upsert 패턴 (findById 기반)
│
├── common/
│   ├── web/
│   │   └── PageController.java                 Thymeleaf 뷰 (여러 도메인에 걸침)
│   ├── exception/
│   │   ├── ErrorCode.java
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java
│   └── dto/
│       └── ErrorResponse.java
│
└── config/
    └── RedisConfig.java                        paySeatScript 빈 등록
```

### 도메인 간 의존 관계

```
ticket → queue (ActiveUserPort: 활성 사용자 검증)
ticket → seat  (SeatHoldPort: 좌석 선점/결제)
seat   → (독립)
queue  → (독립)
```

### Lua Script

```
src/main/resources/scripts/
└── pay-seat.lua          held → paid 원자적 전환 (SET이 TTL 자동 제거)
```

### Thymeleaf 화면

```
src/main/resources/templates/
├── index.html              메인 (대기열 진입 버튼)
├── queue.html              대기열 (순번 표시, 2초 폴링, ACTIVE 시 자동 이동)
├── purchase.html           좌석 선택 + 구매 (좌석 배치도 UI, 단일 좌석 선택)
└── confirmation.html       구매 결과 (성공/실패)
```

---

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/queues/tokens` | 대기열 진입, UUID 토큰 반환 | 없음 |
| GET | `/api/queues/tokens/{uuid}` | 대기 순번/상태 조회 | 없음 |
| GET | `/api/seats` | 전체 좌석 현황 조회 | 없음 |
| GET | `/api/seats/available-count` | 잔여 좌석 수 (Caffeine 캐시, 2초 TTL) | 없음 |
| POST | `/api/tickets` | 좌석 구매 | `X-Queue-Token` 헤더 |

### 구매 요청/응답 예시

```
POST /api/tickets
X-Queue-Token: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{ "seatNumber": 7 }
```

```json
{
    "ticketUuid": "a1b2c3d4-...",
    "seatNumber": 7,
    "status": "SYNCED"
}
```

---

## Redis 키 설계

| Key Pattern | Type | 값 예시 | TTL | 용도 |
|-------------|------|---------|-----|------|
| `waiting_queue` | Sorted Set | member=UUID, score=timestamp | 없음 | FIFO 대기열 |
| `active_user:{uuid}` | String | `"1"` | 300초 | 입장 허용 상태 |
| `seat:{seatNumber}` | String | `"held:{token}"` | 300초 | 좌석 임시 선점 |
| `seat:{seatNumber}` | String | `"paid:{token}"` | 없음 (SET 자동 제거) | 좌석 결제 확정 |

---

## 설정값

```yaml
zticket:
  admission:
    batch-size: 60          # 1초마다 최대 입장 인원 수
    interval-ms: 1000       # 입장 스케줄러 실행 주기
    active-ttl-seconds: 300 # 입장 후 구매 가능 시간 (5분)
    max-active-users: 500   # 동시 active 유저 상한 (= 좌석 수)
  seat:
    total-count: 500        # 총 좌석 수
    hold-ttl-seconds: 300   # 좌석 선점 유지 시간 (5분)
  sync:
    interval-ms: 60000      # 동기화 워커 실행 주기 (1분)
```

**핵심 제약**:
- `active-ttl-seconds(300초)` = `hold-ttl-seconds(300초)`: active 유저의 세션 시간과 좌석 선점 시간이 동일해야 합니다. active가 먼저 만료되면 구매를 못 하는데 좌석만 잡혀있고, hold가 먼저 만료되면 구매 중 좌석이 풀립니다.
- `sync.interval-ms(60초)` < `hold-ttl-seconds(300초)`: 동기화 워커가 TTL 만료 전에 실행되어야 합니다. 단, DB `seatNumber UNIQUE` 제약이 최종 방어선으로 이중 판매를 차단합니다.

---

## 부하 테스트 (k6)

[k6](https://grafana.com/docs/k6/)를 사용하여 대기열 진입부터 티켓 구매까지의 전체 플로우를 부하 테스트합니다.

### 설치

```bash
brew install k6
```

### 시나리오

VU(가상 유저) 100명이 동시에 티켓 구매를 시도하는 시나리오입니다. 각 VU는 실제 사용자와 동일한 플로우를 수행합니다.

```
VU 100명 동시 시작
    │
    ├── 1. POST /api/queues/tokens        대기열 진입, UUID 발급
    │
    ├── 2. GET /api/queues/tokens/{uuid}   2초 폴링, ACTIVE까지 대기
    │       (반복)
    │
    ├── 3. GET /api/seats                  빈 좌석 조회
    │
    └── 4. POST /api/tickets               랜덤 빈 좌석 구매
```

### 실행

```bash
# 서버 실행 후
k6 run k6/load-test.js

# BASE_URL 변경
k6 run -e BASE_URL=http://localhost:8080 k6/load-test.js
```

### 커스텀 메트릭

| 메트릭 | 설명 |
|--------|------|
| `purchase_success` | 구매 성공 수 |
| `purchase_fail` | 구매 실패 수 (좌석 충돌 등) |
| `queue_wait_time` | 대기열 진입 → ACTIVE까지 소요시간 |

---

## 모니터링 (Prometheus + Grafana)

Spring Boot Actuator + Micrometer로 메트릭을 수집하고, Prometheus + Grafana로 시각화합니다.

### 구성

```
App (Actuator /actuator/prometheus)
    │
    │  5초 주기 스크래핑
    ▼
Prometheus (:9090)
    │
    │  데이터소스
    ▼
Grafana (:3000)  →  ZTicket 대시보드 (자동 프로비저닝)
```

### 실행

```bash
# 전체 인프라 실행 (MySQL + Redis + Prometheus + Grafana)
docker compose up -d

# 앱 실행
./gradlew bootRun

# 접속
open http://localhost:3000   # Grafana (admin / admin)
open http://localhost:9090   # Prometheus
```

Grafana 접속 시 `ZTicket Monitoring` 대시보드가 자동으로 프로비저닝되어 있습니다.

### 대시보드 패널

| 패널 | 확인할 수 있는 것 |
|------|-------------------|
| HTTP Request Rate | 초당 요청 수 (엔드포인트별) |
| HTTP Response Time p95 | 95% 응답 시간 — 대부분의 사용자 체감 지연 |
| HTTP Response Time p99 | 99% 응답 시간 — 꼬리 지연(tail latency) |
| HTTP Error Rate | 4xx/5xx 에러 비율 |
| JVM Heap Memory | 힙 메모리 사용량 — OOM 징후 감지 |
| JVM Threads | 라이브/피크 스레드 수 — 스레드 고갈 감지 |
| HikariCP Connections | DB 커넥션풀 (active/idle/pending) — DB 병목 감지 |
| GC Pause Time | GC 멈춤 시간 — GC로 인한 응답 지연 감지 |

### Actuator 엔드포인트

| Path | 설명 |
|------|------|
| `/actuator/prometheus` | Prometheus 포맷 메트릭 |
| `/actuator/health` | 앱 + DB + Redis 헬스체크 |
| `/actuator/metrics` | 전체 메트릭 목록 |

### 부하 테스트 중 확인 포인트

```bash
# 1. 인프라 + 모니터링 실행
docker compose up -d

# 2. 앱 실행
./gradlew bootRun

# 3. Grafana 대시보드 열기
open http://localhost:3000

# 4. k6 부하 테스트 실행
k6 run k6/load-test.js

# 5. Grafana에서 실시간 병목 확인
```

| 증상 | 대시보드에서 보이는 것 | 원인 |
|------|----------------------|------|
| 응답 느림 | p95/p99 급등 | Redis/DB 병목 또는 GC |
| 에러 급증 | Error Rate 상승 | 커넥션풀 고갈, 타임아웃 |
| 메모리 부족 | Heap 사용량 max 근접 | JVM 힙 부족, GC 빈번 |
| DB 병목 | HikariCP pending 증가 | 커넥션풀 사이즈 부족 |
| 스레드 고갈 | Threads 급증 | Tomcat 스레드풀 부족 |
