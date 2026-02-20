# ZTicket - 대용량 선착순 좌석 티켓 구매 시스템

수십만 명 동시 접속 상황에서 선착순 좌석 티켓 구매를 처리하는 시스템입니다.
Java 25 Virtual Thread 기반의 높은 동시성 처리와 Redis 기반 대기열, 비동기 이벤트 동기화 + 배치 복구를 통해
**중복 판매 없는 정합성**과 **높은 처리량**을 동시에 달성합니다.

---

## 목차

1. [기술 스택](#기술-스택)
2. [실행 방법](#실행-방법)
3. [아키텍처 Overview](#아키텍처-overview)
4. [핵심 플로우](#핵심-플로우)
5. [데이터 정합성](#데이터-정합성)
6. [설계 결정](#설계-결정)
7. [부하 테스트 (k6)](#부하-테스트-k6)
8. [모니터링 (Prometheus + Grafana)](#모니터링-prometheus--grafana)
9. [패키지 구조](#패키지-구조)
10. [API 명세](#api-명세)
11. [Redis 키 설계](#redis-키-설계)
12. [설정값](#설정값)

---

## 기술 스택

| 구분 | 기술 | 버전 |
|------|------|------|
| Language | Java | 25 |
| Framework | Spring Boot | 4.0.2 |
| ORM | Spring Data JPA + Hibernate | - |
| Database | MySQL | 8.0 |
| Cache/Queue | Redis | 7 |
| Template | Thymeleaf | - |
| Build | Gradle | 9.3 |
| Container | Docker Compose | - |

---

## 실행 방법

```bash
# 1. 전체 실행 (App + MySQL + Redis + Prometheus + Grafana)
docker compose up -d

# 2. 접속
open http://localhost:8080   # ZTicket
open http://localhost:3000   # Grafana (admin / admin)
```
---

## 아키텍처 Overview

### 헥사고날 아키텍처 (Ports and Adapters)

```mermaid
flowchart TD
    HTTP[HTTP Request] --> IN[Adapter IN<br/>Controller / Scheduler]
    IN -- UseCase 인터페이스 호출 --> APP[Application Service<br/>유스케이스 오케스트레이션]
    APP -- Port OUT 인터페이스 호출 --> OUT[Adapter OUT<br/>Redis Adapter / JPA Adapter]
    OUT --> Redis[(Redis)]
    OUT --> MySQL[(MySQL)]
```

**핵심 원칙**: domain 패키지는 순수 Java로만 구성되며, Spring/JPA/Redis 등 프레임워크 의존성이 전혀 없습니다. 모든 외부 기술은 adapter 패키지에서 port 인터페이스를 구현하는 방식으로 연결됩니다.

---

## 핵심 플로우

### 1. 대기열 진입

```mermaid
sequenceDiagram
    participant U as 사용자
    participant Q as QueueService
    participant O as WaitingQueueOperator
    participant R as Redis

    U->>Q: POST /api/queues/tokens
    Q->>O: enqueue(uuid)
    O->>R: ZADD waiting_queue (score=진입시각)
    O->>R: ZADD waiting_queue_heartbeat (score=진입시각)
    R-->>O: rank
    O-->>Q: rank
    Q-->>U: { uuid, rank }
```

- `waiting_queue`(순서 관리)와 `waiting_queue_heartbeat`(생존 감지)에 동시 등록

### 2. 대기열 폴링

```mermaid
sequenceDiagram
    participant U as 사용자
    participant Q as QueueService
    participant O as WaitingQueueOperator
    participant R as Redis

    loop 5초 폴링
        U->>Q: GET /api/queues/tokens/{uuid}
        alt active_user:{uuid} 존재
            Q-->>U: ACTIVE → 좌석 선택 페이지로 이동
        else 잔여 좌석 = 0
            Q-->>U: SOLD_OUT → 매진 안내
        else 대기 중
            Q->>O: getRank(uuid)
            O->>R: ZRANK waiting_queue
            R-->>O: rank
            O-->>Q: rank (없으면 null)
            Q->>O: refresh(uuid)
            O->>R: ZADD waiting_queue_heartbeat (score=현재시각)
            Q-->>U: WAITING (현재 순번)
        end
    end
```

- `waiting_queue_heartbeat`의 score만 현재 시각으로 갱신 (순서를 유지하기 위해 `waiting_queue`는 건드리지 않음)
- **SOLD_OUT 판정**: 잔여 좌석이 0이면 대기열 순번 조회 없이 즉시 SOLD_OUT 반환

**시간복잡도** (N = 대기열 인원):

| 연산 | 명령 | 시간 복잡도 | 빈도 |
|------|------|--------|------|
| 진입 | ZADD × 2 | O(log N) | 유저당 1회 |
| 폴링 heartbeat 갱신 | ZADD × 1 | O(log N) | 유저당 5초마다 |
| rank 조회 | ZRANK | O(log N) | 유저당 5초마다 |

100만 명 대기 시 log(1M) ≈ 20. 모든 연산이 O(log N)이므로 대기자 수가 늘어도 연산당 처리 시간은 완만하게 증가합니다.

### 3. 입장 스케줄러 플로우

`AdmissionScheduler`(5초 주기)에서 **잠수 유저 제거 → 입장**을 한 번에 처리합니다.

```mermaid
sequenceDiagram
    participant S as AdmissionScheduler<br/>(매 5초)
    participant Q as QueueService
    participant O as WaitingQueueOperator
    participant A as ActiveUserPort
    participant SS as SeatService
    participant R as Redis

    S->>Q: admitBatch()

    Note over Q: removeExpired: 잠수 유저 제거
    loop findExpired 결과가 요청 size보다 작을 때까지
        Q->>O: findExpired(5000)
        O->>R: ZRANGEBYSCORE waiting_queue_heartbeat -inf cutoff LIMIT 0 5000
        R-->>O: 잠수 uuid 목록
        Q->>O: removeAll(expiredUuids)
        O->>R: ZREM waiting_queue + ZREM waiting_queue_heartbeat
    end

    Note over Q: 입장 인원 계산
    Q->>A: countActive()
    A->>R: SCAN active_user:*
    R-->>A: currentActive
    Q->>SS: getAvailableCount()
    SS-->>Q: remainingSeats
    Note over Q: toAdmit = min(batchSize, 빈 슬롯, 잔여 좌석 - active 유저)

    Note over Q: peek
    Q->>O: peek(toAdmit)
    O->>R: ZRANGE waiting_queue 0 (toAdmit-1)
    R-->>O: FIFO 순서 후보
    O-->>Q: uuid 목록

    Note over Q: activate
    Q->>A: activateBatch(uuids, ttl)
    A->>R: Pipeline SET active_user:{uuid} "1" EX 300 (일괄)

    Note over Q: remove
    Q->>O: removeAll(uuids)
    O->>R: ZREM waiting_queue {uuids}
    O->>R: ZREM waiting_queue_heartbeat {uuids}
```

**removeExpired → peek → activate → remove 4단계**: 잠수 유저를 먼저 제거한 뒤 단순 FIFO peek으로 입장 후보를 조회합니다. activate 완료 후에야 큐에서 제거하므로 중간에 서버가 죽어도 데이터가 유실되지 않습니다.

### 4. 구매 플로우

> 이 시스템에서는 별도 결제 게이트웨이(PG) 없이, **DB에 티켓을 INSERT하는 것 자체가 결제 완료**를 의미합니다. 실제 서비스라면 PG 연동이 2~3단계 사이에 추가되겠지만, 이 프로젝트는 대기열과 좌석 정합성에 집중하기 위해 결제 과정을 생략했습니다.

```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant T as TicketService
    participant E as EventPublisher
    participant L as TicketPaidEventListener<br/>(@Async)
    participant R as Redis
    participant DB as MySQL

    C->>T: POST /api/tickets

    T->>R: 1. isActive(token)
    alt 비활성 사용자
        R-->>T: false
        T-->>C: NOT_ACTIVE_USER
    end
    R-->>T: true

    T->>R: 2. SET seat:N "held:{token}" NX EX 300
    alt 이미 선점된 좌석
        R-->>T: false
        T-->>C: SEAT_ALREADY_HELD
    end
    R-->>T: true

    T->>DB: 3. INSERT ticket (status=PAID)
    alt DB 저장 실패
        T->>R: Lua: held:{token}일 때만 DEL seat:N (롤백)
        T-->>C: INTERNAL_ERROR
    end

    T-->>C: PAID 티켓 반환 (구매 확정)

    Note over T: DB PAID = Source of Truth<br/>이후 Redis 동기화는 비동기 처리

    T->>E: publishEvent(TicketPaidEvent)
    Note over T: 즉시 반환 (비동기)

    E->>L: handle(event)
    L->>DB: findByUuid(ticketUuid)
    DB-->>L: Ticket (PAID)

    L->>R: SET seat:N "paid:{token}" (held → paid 전환)
    L->>DB: UPDATE ticket SET status=SYNCED
    L->>R: DEL active_user:{token}
```

### 5. 동기화 배치 복구: PAID 티켓 재동기화

5단계 비동기 처리가 실패하면 DB에 PAID 상태로 남습니다. 동기화 배치(`SyncScheduler`, 매 1분)가 이를 감지하여 동일한 `TicketPaidEvent`를 발행합니다. 리스너의 로직은 멱등하므로 몇 번을 재실행해도 동일한 결과를 보장합니다.

```mermaid
flowchart TD
    A[SyncScheduler 매 1분] --> B[DB에서 status=PAID 티켓 조회]
    B --> C{PAID 티켓 있음?}
    C -- 없음 --> END[종료]
    C -- 있음 --> D["각 PAID 티켓에 대해<br/>TicketPaidEvent 발행"]

    style A fill:#f9f,stroke:#333
    style END fill:#9f9,stroke:#333
```

**DB PAID가 Source of Truth**: Redis는 장애나 TTL 만료로 상태가 유실될 수 있지만, DB에 저장된 티켓은 구매 확정입니다. 동기화 배치는 DB의 PAID 레코드를 기준으로 Redis 상태를 복원합니다.

---

## 데이터 정합성

이 시스템의 가장 어려운 문제는 **Redis와 DB 간의 상태 불일치**입니다. 두 저장소에 걸친 연산은 분산 트랜잭션이 불가능하므로, 장애 시나리오별 대응 전략을 설계했습니다.

### 1. Redis-DB 장애 시나리오와 복구

#### Case 1: Redis held 성공 → DB INSERT 실패

- **사용자 응답**: INTERNAL_ERROR (구매 실패)
- **상태**: Redis `held:{token}` (TTL 째깍째깍) + DB 레코드 없음
- 구매가 확정되지 않은 상태이므로 catch 블록에서 Lua 스크립트로 `held:{자신의 토큰}`일 때만 `DEL seat:{n}` 롤백합니다 (다른 사용자의 키를 삭제하지 않음, [좌석 해제: Lua 스크립트로 조건부 삭제](#좌석-해제-lua-스크립트로-조건부-삭제) 참고).
- 롤백마저 실패해도 held 키의 TTL(5분)이 자동 해제합니다.
- **중복 판매 불가능**: DB에 레코드 자체가 없으므로 판매된 적이 없습니다.

#### Case 2-1: DB PAID 성공 → 비동기 Redis paid 전환 실패 (또는 서버 사망)

- **사용자 응답**: 구매 성공 (PAID 티켓 반환 완료)
- **상태**: Redis `held:{token}` (TTL 째깍째깍) + DB `PAID`
- Redis에 아직 held 상태이므로 TTL 만료 시 다른 사용자에게 빈 좌석으로 노출될 수 있습니다.
- 동기화 배치가 1분마다 PAID 티켓을 조회하여 `SET seat:{n} paid:{token}`으로 Redis를 복원합니다.
- **중복 판매 불가능**: 설령 TTL 만료 후 다른 사용자가 hold하더라도, DB INSERT 시 `seat_number UNIQUE` 제약에 의해 거부됩니다.

#### Case 2-2: DB INSERT 성공했으나 타임아웃으로 실패 응답

- **시나리오**: Redis held 성공 → DB INSERT 전송 → DB는 커밋 성공 → 그러나 네트워크 타임아웃으로 애플리케이션은 예외 수신
- **사용자 응답**: INTERNAL_ERROR (구매 실패로 인식)
- **상태**: catch 블록에서 Lua 스크립트로 `held:{자신의 토큰}`일 때만 `DEL seat:{n}` 롤백 실행 → Redis 키 삭제 + DB `PAID` 레코드 존재
- **복구**: 동기화 배치(1분 주기)가 DB에서 `PAID` 티켓을 발견하고, `SET seat:{n} paid:{token}`으로 Redis를 복원한 뒤 `SYNCED`로 갱신합니다.
- **중복 판매 불가능**: Redis 키가 잠시 삭제되어 다른 사용자가 hold할 수 있지만, DB INSERT 시 `seat_number UNIQUE` 제약에 의해 거부됩니다. 사용자에게는 실패로 응답되었지만 실제로는 구매가 완료된 상태이므로, 티켓 조회(마이페이지 등)에서 확인할 수 있습니다.

#### Case 3: Redis paid 성공 → DB SYNCED 갱신 실패

- **사용자 응답**: 구매 성공 (PAID 티켓 반환 완료)
- **상태**: Redis `paid:{token}` (영구) + DB `PAID`
- 동기화 배치가 paySeat을 재실행하지만, 이미 paid이므로 동일한 값을 덮어쓸 뿐입니다(멱등). 이후 DB를 SYNCED로 갱신합니다.
- **중복 판매 불가능**: Redis가 이미 paid로 영구 점유 중이라 다른 사용자의 hold가 불가능합니다.

#### Case 4: DB SYNCED 성공 → active 유저 제거 실패

- **사용자 응답**: 구매 성공 (PAID 티켓 반환 완료)
- **상태**: Redis `paid:{token}` (영구) + DB `SYNCED` + `active_user:{token}` 잔존
- 구매와 동기화는 완료된 상태이며, active 슬롯만 5분간 불필요하게 점유됩니다.
- `active_user` 키에 TTL(5분)이 걸려 있어 별도 처리 없이 자동 만료됩니다.

---

### 2. 좌석 선점과 해제

#### 선점: `SET NX EX`

"키가 없을 때만 생성"이라는 단순한 조건이므로, Redis 네이티브 명령 하나로 원자적으로 처리됩니다.

```java
// SeatRedisAdapter.java
Boolean success = redisTemplate.opsForValue()
        .setIfAbsent(key, "held:" + uuid, ttlSeconds, TimeUnit.SECONDS);
```

#### 해제: Lua 스크립트로 자신의 held만 삭제

DB INSERT 실패 시 Redis 좌석을 롤백해야 하는데, 무조건 `DEL`하면 다른 사용자의 키를 삭제할 위험이 있습니다.

- **문제 시나리오**: A가 `SET seat:7 "held:A" NX` 성공 → A의 스레드가 장시간 멈춤 (GC, 긴 I/O 대기, CPU 스케줄링 밀림 등) → 그 사이 held TTL 만료 → B가 같은 좌석 선점 + DB INSERT 성공 (PAID) → B의 비동기 처리 완료 (`SET seat:7 "paid:B"`, 티켓 `SYNCED`) → A의 스레드 재개, 아직 선점 중이라고 인식하고 DB INSERT 시도 → UNIQUE 위반으로 실패 → A의 catch 블록에서 `DEL seat:7` 실행 → **B의 `paid:B`가 삭제됨**
- **위험**: B는 정상 구매 완료했는데 Redis에서 `paid:B`가 사라집니다. 좌석이 AVAILABLE로 노출되고, B의 티켓은 이미 `SYNCED`라서 동기화 배치가 다시 잡아주지 않습니다. 영구적 Redis-DB 불일치.
- **해결**: Lua 스크립트로 `held:{자신의 토큰}`일 때만 삭제합니다. GET + 비교 + DEL을 원자적으로 처리하여 race condition을 방지합니다.

```lua
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
else
    return 0
end
```

이는 Redis 분산 락 해제의 정석 패턴(Compare-and-Delete)과 동일합니다.

---

### 3. 대기열 관리: 잠수 제거 + 입장

대기열은 Sorted Set 2개로 관리한다.

```
waiting_queue           (score = 진입 시각)      → FIFO 순서 유지, rank 조회용
waiting_queue_heartbeat (score = 마지막 폴링 시각) → 잠수 감지 및 제거용
```

**동작 흐름**:

| 시점 | waiting_queue | waiting_queue_heartbeat |
|------|--------------|------------------------|
| 진입 (`POST /api/queues/tokens`) | ZADD {진입시각} | ZADD {진입시각} |
| 폴링 (`GET /api/queues/tokens/{uuid}`) | 안 건드림 | ZADD {현재시각} |
| 잠수 제거 (`admitBatch` 1단계) | ZREM | ZRANGEBYSCORE + ZREM |
| 입장 (`admitBatch` 3단계) | ZRANGE(peek) + ZREM | ZREM |

**왜 Sorted Set 2개?**
- `waiting_queue`의 score를 폴링 시각으로 갱신하면 FIFO 순서가 깨져서 rank가 매 폴링마다 뒤바뀐다.
- 진입 순서(rank)와 생존 여부(heartbeat)는 별개 관심사이므로 분리했다.
- 개별 키(`queue_hb:{uuid}`) N개 대신 Sorted Set 1개로 관리. 키스페이스를 오염시키지 않는다.

#### 잠수 유저 제거 + 입장 제어 (removeExpired → peek → activate → remove)

`AdmissionScheduler`(5초 주기)에서 잠수 유저 제거와 입장을 한 번에 처리한다. 먼저 잠수 유저를 제거한 뒤, active 유저 수를 세고 `maxActiveUsers - currentActive` 만큼만 입장시키되, `batchSize`(100명)를 상한으로 제한한다. 또한 잔여 좌석에서 active 유저 수를 보수적으로 차감하여, 좌석보다 많은 유저가 입장하지 않도록 한다. 대기열 진입 시점에서도 잔여 좌석이 0이면 진입 자체를 거부(SOLD_OUT)한다.

잠수 유저를 먼저 제거하므로, 이후 `peek`은 단순 FIFO 조회(`ZRANGE waiting_queue`)만 수행하면 된다.

**입장 후 잠수 유저**: 입장 후 구매하지 않는 잠수 유저는 `active_user:{uuid}` 키의 TTL(300초)로 자연 회수된다. 5분 뒤 자동 만료되어 슬롯이 반환된다.

**active 유저 카운트 — SCAN 사용 이유**: `active_user:{uuid}` 패턴의 키 수를 세야 하는데, Redis에는 접두사 인덱스가 없다. `KEYS`는 블로킹, `SCAN`은 커서 기반 논블로킹. `SCAN`은 정확한 값을 보장하지 않지만, 입장 제어에는 정확한 수가 필요 없다. 다소 많거나 적게 입장시켜도 다음 주기에 보정된다.

**removeExpired → peek → activate → remove 4단계 분리**:

- **removeExpired**: 잠수 유저(heartbeat 60초 이상 미갱신)를 대기열에서 제거. 이후 peek이 단순해진다.
- **peek**: 큐에서 꺼내지 않고 FIFO 순서로 조회만.
- **activate**: `active_user:{uuid}` 키를 Redis 파이프라이닝으로 일괄 생성. 멱등 연산이라 재실행해도 TTL만 갱신. 서버가 죽으면 다음 주기에 다시 처리.
- **remove**: activate 완료 후에야 큐에서 제거. "큐에서는 빠졌는데 active는 안 된" 상태가 안 생긴다.

**시간복잡도** (N = 대기열 인원, K = 입장 인원, M = 잠수 유저 수):

| 연산 | 명령 | 시간 복잡도 | 빈도 |
|------|------|--------|------|
| 잠수 감지 | ZRANGEBYSCORE | O(log N + M) | 5초마다 |
| 잠수 제거 | ZREM × 2 | O(M log N) | 5초마다 |
| active 카운트 | SCAN | O(전체 키 수) | 5초마다 |
| peek | ZRANGE | O(K log N) | 5초마다 |
| remove | ZREM × 2 | O(K log N) | 5초마다 |

---

## 설계 결정

### 1. 아키텍처: 헥사고날 vs 레이어드

#### 레이어드 아키텍처의 문제

전통적인 레이어드 아키텍처는 `Controller → Service → Repository`로 구성됩니다. 레이어 간 경계가 인터페이스 없이 구체 클래스로 직접 연결되어 있습니다.

```
Controller → Service (구체 클래스) → Repository (구체 클래스)
                ↓                        ↓
           @Entity 도메인             JPA 직접 의존
```

이 구조에서는 다음과 같은 문제가 발생합니다:

- **도메인이 인프라에 오염**: `Ticket.java`에 `@Entity`, `@Column` 같은 JPA 어노테이션이 직접 붙습니다. 도메인 객체가 JPA에 종속되어, DB를 교체하면 도메인 코드까지 수정해야 합니다.
- **테스트 시 인프라 의존**: Service가 Repository 구체 클래스에 직접 의존하므로, 단위 테스트에서 Redis/MySQL 없이 로직만 테스트하기 어렵습니다.
- **의존성 방향이 아래로만 흐름**: `Controller → Service → Repository → DB`. 상위 레이어가 하위 레이어의 구현을 알게 됩니다.

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

헥사고날 아키텍처는 **DIP(의존성 역전 원칙)**와 **객체 변환**을 통해 레이어드의 문제를 해결합니다.

**1) Port 인터페이스로 의존성 역전**

Service가 Repository 구체 클래스에 직접 의존하는 대신, application 패키지에 Port 인터페이스를 정의하고 adapter가 이를 구현합니다. 의존성 방향이 역전되어 인프라(adapter)가 도메인(application)에 의존하게 됩니다.

```
레이어드:    Controller → Service → Repository (구체)     의존성: 위 → 아래
헥사고날:    Controller → UseCase(Port) ← Service → Port(인터페이스) ← Adapter
             adapter/in    port/in      application     port/out       adapter/out
```

**2) Adapter에서 JPA Entity ↔ 도메인 객체 변환**

레이어드에서는 `@Entity`가 붙은 JPA 엔티티를 Service까지 그대로 올려보냅니다. 도메인 객체 자체가 JPA에 종속됩니다. 헥사고날에서는 Adapter가 경계에서 JPA 엔티티를 도메인 객체로 변환하여 반환합니다. Port 인터페이스의 반환 타입이 도메인 객체(`Ticket`)이므로, 도메인은 JPA의 존재를 알 수 없습니다.

```
레이어드:    Repository → TicketEntity(@Entity) → Service → Controller
             JPA 엔티티가 도메인까지 그대로 노출

헥사고날:    TicketJpaAdapter → TicketJpaEntity(@Entity) → toDomain() → Ticket(순수 Java)
             Adapter 내부에서 변환, Port 밖으로는 도메인 객체만 나감
```

```java
// TicketJpaAdapter.java - Port 구현체
public Ticket save(Ticket ticket) {
    TicketJpaEntity entity = TicketJpaEntity.fromDomain(ticket);  // 도메인 → JPA
    return repository.save(entity).toDomain();                     // JPA → 도메인
}
```

이 두 가지 메커니즘(DIP + 객체 변환)으로 레이어드의 세 가지 문제를 해결합니다:

**채택 이유**:
- **도메인 순수성**: Port 인터페이스가 경계를, Adapter의 객체 변환이 분리를 만듭니다. `Ticket.java`에 `@Entity` 같은 JPA 어노테이션이 없습니다. Redis를 Memcached로, MySQL을 PostgreSQL로 교체해도 domain 패키지는 한 줄도 수정하지 않습니다.
- **테스트 용이성**: Port 인터페이스 덕분에 단위 테스트에서 Mock으로 교체할 수 있습니다. `TicketService`는 `SeatPort`와 `TicketPort`만 Mock하면 Redis/MySQL 없이도 구매 로직을 완벽히 테스트할 수 있습니다.
- **의존성 방향 제어**: DIP에 의해 모든 의존성이 domain을 향합니다 (`adapter → application → domain`). domain은 어디에도 의존하지 않습니다.
- **경계의 명시성**: `port/in`, `port/out`, `adapter/in`, `adapter/out`이라는 패키지 구조가 안쪽(도메인)과 바깥쪽(인프라)의 경계를 명확히 드러냅니다.

**트레이드오프**:
- **파일 수 증가**: `SeatPort`(인터페이스) + `SeatRedisAdapter`(구현)처럼 인터페이스-구현 쌍이 반드시 필요합니다. 레이어드라면 `SeatService` 하나로 끝납니다.
- **간접 참조 비용**: Controller → UseCase 인터페이스 → Service → Port 인터페이스 → Adapter. 호출 체인이 길어져 코드를 따라가기 어려울 수 있습니다.
- **매핑 코드 추가**: Adapter마다 `fromDomain()`과 `toDomain()` 변환 코드가 필요합니다. 레이어드에서는 `@Entity` 객체를 그대로 사용하므로 이 코드가 없습니다.

---

### 2. 대기열: Redis Sorted Set vs 메시지 큐 (Kafka/RabbitMQ)

#### 선택: Redis Sorted Set (`ZADD`, `ZRANK`, `ZRANGE + ZREM`)

**채택 이유**:
- **실시간 순번 조회**: `ZRANK`는 O(log N)으로 즉시 현재 순번을 반환합니다. 클라이언트가 5초마다 폴링할 때 "현재 347번째"를 바로 응답할 수 있습니다. Kafka에서는 consumer offset으로 순번을 계산하는 것이 불가능에 가깝습니다.
- **배치 입장의 단순성**: `ZRANGE(0, 59)` + `ZREM`으로 상위 60명을 원자적으로 추출합니다.
- **인프라 단순성**: 이미 좌석 선점용으로 Redis를 사용하므로, 별도 인프라를 추가하지 않습니다.

**트레이드오프**:
- **메모리**: UUID 멤버 기준 `waiting_queue` + `waiting_queue_heartbeat` 합산 유저당 ~250bytes. 1,000만 명이면 ~2.5GB로 단일 인스턴스에서 충분합니다. 다만 대기자가 많아질수록 폴링에 의한 ops/s가 증가하므로, 단일 인스턴스로는 한계에 도달할 수 있습니다. 대응 방법은 두 가지입니다:
  - **폴링 주기 늘리기**: 폴링 간격을 늘려 ops/s를 줄입니다. 인프라 변경 없이 설정값만 조정하면 되지만, 입장 반영이 지연됩니다.
  - **애플리케이션 레벨 큐 샤딩**: `waiting_queue`를 여러 Sorted Set으로 분할하고 별도 Redis 인스턴스에 배치하여, 글로벌 순위와 입장을 적절히 처리합니다. Redis Cluster는 키 단위 분산이라 단일 Sorted Set에는 효과가 없으므로 애플리케이션 레벨에서 샤딩해야 하며, 구현 복잡도가 높습니다.
- **영속성 부재**: Redis는 인메모리 저장소이므로 장애 시 대기열 데이터가 유실됩니다. 다만 대기열은 일시적 데이터라 유실되어도 크게 이슈가 없고, 영속성보다 처리 성능이 더 중요하다고 판단했습니다.
- **순서 보장 범위**: `System.currentTimeMillis()` 기반 score를 사용하므로, 같은 밀리초에 도착한 요청은 순서가 보장되지 않습니다. 또한 서버가 여러 대일 경우 서버 간 시각 차이가 존재할 수 있습니다. 다만 ms 단위의 차이는 사람이 체감할 수 없는 수준이므로, 선착순 공정성에 실질적인 영향은 없습니다.

---

### 3. 1티켓-1좌석: 단일 좌석 vs 다중 좌석 선택

#### 선택: 1티켓 = 1좌석 (`seatNumber: int`)

```java
// Ticket.java
private final int seatNumber;  // List<Integer> seatNumbers가 아님
```

**채택 이유**:
- **본질에 집중**: 이 프로젝트의 핵심은 대용량 동시 접속 환경에서의 선착순 티켓 구매 시스템입니다. 다중 좌석 선택은 부가 기능이지 핵심 도메인이 아닙니다. 대기열 관리, Redis-DB 동기화, 중복 판매 방지 등 핵심 문제에 집중하기 위해 의도적으로 제외했습니다.

---

### 4. 영속화 패턴: 도메인 객체 `save(ticket)` vs 직접 `updateStatus(uuid, status)`

#### 선택: 도메인 엔티티에서 상태 전이 후 save

```java
// TicketService.java - 상태 전이는 도메인 엔티티가 담당
ticket.sync();                       // Ticket 내부에서 PAID→SYNCED 전환 + 유효성 검증
ticketPort.save(ticket);  // 변경된 도메인 객체를 그대로 저장
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
- **Port 인터페이스 단순화**: `TicketPort`에 `save`, `findByUuid`, `findByStatus` 3개 메서드만 있습니다. `updateStatus`가 없으므로 포트가 더 범용적입니다.
- **Upsert 패턴**: 같은 `save()` 메서드로 INSERT(첫 저장)와 UPDATE(상태 변경)를 모두 처리합니다.

**트레이드오프**:
- **Upsert의 추가 SELECT**: 매 save마다 `findById`를 먼저 실행합니다. 직접 `UPDATE ... WHERE id = ?`보다 한 번의 SELECT가 추가됩니다. 하지만 PK 조회이므로 성능 차이는 미미합니다.
- **도메인 엔티티 외부 수정 불가**: `ticket.setStatus()`가 없으므로 테스트에서 임의 상태를 주입하려면 생성자를 사용해야 합니다.

---

### 5. 클라이언트 통신: 폴링 vs WebSocket vs SSE

#### 선택: 5초 주기 HTTP 폴링

**WebSocket / SSE를 사용하지 않는 이유**:
- **WebSocket**: 서버가 각 클라이언트와 상시 TCP 연결을 유지해야 합니다. 대기자 50만 명이면 50만 개의 커넥션이 동시에 열려있어야 하고, 로드밸런서에서 같은 클라이언트를 같은 서버로 라우팅하는 sticky session 설정이 필요합니다.
- **SSE(Server-Sent Events)**: WebSocket보다 단순하지만 마찬가지로 서버가 HTTP 연결을 끊지 않고 유지합니다. 대규모 동시 접속에서 커넥션 수 문제는 동일합니다.

**폴링 채택 이유**:
- **연결을 유지하지 않음**: 요청-응답 후 즉시 커넥션이 반환됩니다. 50만 명이 대기해도 동시 커넥션 수는 폴링 주기에 비례한 일부분만 차지합니다.
- **인프라 단순성**: 일반 REST API이므로 별도 설정 없이 모든 로드밸런서/CDN과 호환됩니다.

**트레이드오프**:
- **불필요한 요청**: 순번 변화가 없어도 5초마다 요청을 보냅니다. 대기자 50만 명 × 0.2 req/s = ~100,000 req/s.
- **최대 5초 지연**: 입장이 허용된 직후부터 최대 5초 후에야 클라이언트가 인지합니다.

### 6. 스레드 모델: Virtual Thread vs Platform Thread

#### 선택: Java 25 Virtual Thread (`spring.threads.virtual.enabled: true`)

**Platform Thread를 사용하지 않는 이유**:
- **스레드 풀이 병목**: Tomcat 기본 200개 스레드로는 동시 요청이 200개를 넘으면 대기열에 쌓입니다. 폴링 요청이 수만 req/s인 환경에서 스레드 풀 크기를 늘려도 OS 스레드 생성 비용(~1MB 스택)과 컨텍스트 스위칭 오버헤드로 한계가 있습니다.
- **I/O 대기 중 스레드 점유**: Redis 호출이나 DB 쿼리를 기다리는 동안 platform thread가 블로킹되어 다른 요청을 처리하지 못합니다.

**Virtual Thread 채택 이유**:
- **요청당 생성·소멸**: 스레드 풀 없이 요청마다 VT를 생성합니다. 생성 비용이 수 μs, 스택 수 KB로 극히 낮아서 수만 개를 동시에 운용해도 부담이 없습니다.
- **I/O 대기 시 자동 양보**: VT가 Redis/DB 응답을 기다리면 carrier thread에서 자동으로 unmount되어 다른 VT가 실행됩니다. 블로킹 코드 그대로 논블로킹 수준의 동시성을 달성합니다.
- **코드 변경 최소화**: `spring.threads.virtual.enabled: true` 설정 하나로 Tomcat, `@Async`, `@Scheduled` 모두 VT 기반으로 전환됩니다.

**주의사항**:
- **ThreadLocal 남용 금지**: VT는 요청마다 생성·소멸되므로 platform thread처럼 ThreadLocal이 누수되지는 않습니다. 하지만 VT가 수만 개 동시에 존재하면 ThreadLocal 인스턴스도 수만 개가 생성되어 순간 메모리 사용량이 증가합니다. 가능하면 `ScopedValue`(Java 25~)로 대체하는 것이 권장됩니다.
- **pinning 해결 (JEP 491)**: Java 24부터 `synchronized` 블록 내에서 블로킹해도 VT가 carrier thread에 고정(pinning)되지 않으므로 `ReentrantLock`으로 대체할 필요가 없습니다. pinning 자체가 발생하지 않으므로 `-Djdk.tracePinnedThreads=short` 옵션도 더 이상 지원되지 않습니다.

---

## 부하 테스트 (k6)

[k6](https://grafana.com/docs/k6/)를 사용하여 부하 테스트합니다. 두 가지 시나리오를 제공합니다.

```bash
brew install k6
```

### 1. 전체 플로우 시나리오 (`full-flow.js`)

대기열 진입 → 폴링 → 좌석 조회 → 구매까지 실제 사용자와 동일한 플로우를 시뮬레이션합니다 (`per-vu-iterations`, VU당 1회 실행).

```
VU 동시 시작 (각 VU 1회만 실행)
    │
    ├── 1. POST /api/queues/tokens        대기열 진입, UUID 발급
    │
    ├── 2. GET /api/queues/tokens/{uuid}   5초 폴링, ACTIVE까지 대기
    │       (반복)
    │
    ├── 3. GET /api/seats                  빈 좌석 조회
    │
    └── 4. POST /api/tickets               랜덤 빈 좌석 구매
```

```bash
k6 run k6/full-flow.js
```

| 커스텀 메트릭 | 설명 |
|--------------|------|
| `purchase_success` | 구매 성공 수 |
| `purchase_fail` | 구매 실패 수 (좌석 충돌 등) |
| `queue_wait_time` | 대기열 진입 → ACTIVE까지 소요시간 |

### 2. 스트레스 테스트 (`enter-stress.js` + `queue-stress.js`)

두 스크립트를 동시에 실행하여 진입과 폴링을 동시에 부하를 줍니다.

| 스크립트 | VU    | 동작 | 종료 조건 |
|---------|-------|------|----------|
| `enter-stress.js` | 250   | `POST /api/queues/tokens` 무한 반복 | 10분 경과 |
| `queue-stress.js` | 1,500 | 토큰 1개 발급 후 `GET /api/queues/tokens/{uuid}` 무한 폴링 (ACTIVE/SOLD_OUT 시 1회 작업 종료) | 10분 경과 |

Docker Compose profile로 실행합니다.

```bash
# 스트레스 테스트 (enter-stress.js + queue-stress.js)
docker compose --profile k6-stress up -d

# 전체 플로우 테스트 (full-flow.js)
docker compose --profile k6-full-flow up -d
```

### 부하 테스트 결과

> 단일 머신(MacBook Pro, Apple M4 Max / 32GB)에서 Docker Compose(App + Redis + MySQL + Prometheus + Grafana) + k6를 동시에 실행한 환경.
> k6 VU의 JS 런타임 오버헤드와 CPU 경합이 있으므로, 실 운영 대비 보수적인 수치입니다.

**테스트 조건**: enter-stress 500 VU + queue-stress 3,000 VU (합계 3,500 VU), 10분

#### App (CPU 6코어, Memory 4GB, Heap 2GB, G1GC 기본 설정)

| 항목 | 값 | 비고 |
|------|------|------|
| CPU | 89% | 6코어 대부분 사용 |
| GC Overhead | 16.8% | CPU의 17%를 GC에 소비 |
| GC STW (avg) | ~14ms | Young GC 1회당 평균 STW |
| GC STW (max) | 40ms | |
| GC 횟수 | ~12회/s | Young GC만 발생, Full GC 0회 |
| Eden 할당 속도 | ~3.3GB/s | 단기 객체 생성 속도 |

#### HTTP

| 엔드포인트 | 초당 처리량 | p95 | p99 | p99.9 | 비고 |
|-----------|--------|-----|-----|-------|------|
| `POST /api/queues/tokens` | ~6.0K req/s | 88ms | 106ms | 166ms | 1분에 ~36만 명 진입 가능 |
| `GET /api/queues/tokens/{uuid}` | ~36.4K req/s | 88ms | 107ms | 163ms | 5초 폴링 기준 **~18.2만 명** 동시 대기 |
| **합계** | **~42.4K req/s** | | | | |

#### Redis (CPU 1코어, Memory 1GB, maxmemory 700MB)

| 항목 | 값 | 비고 |
|------|------|------|
| CPU | 50% | 여유 있음 |
| Memory | 88MB / 700MB | 여유 있음 |

#### Redis 커맨드 (Lettuce 클라이언트 기준)

| 명령 | ops/s | p95 | p99 | p99.9 | 용도 |
|------|-------|-----|-----|-------|------|
| ZADD | ~48.4K | 28ms | 33ms | 42ms | 대기열 진입 + heartbeat 갱신 |
| ZRANK | ~42.4K | 26ms | 33ms | 41ms | 순번 조회 |
| EXISTS | ~36.4K | 25ms | 32ms | 40ms | active 토큰 확인 |
| SET | ~14 | - | - | - | active 토큰 등록 |
| ZREM | ~2.8 | 24ms | 35ms | 39ms | 잠수 유저 제거 + 입장 remove |
| ZRANGEBYSCORE | ~1.2 | 30ms | 33ms | 33ms | 잠수 유저 탐색 |
| SCAN | ~0.2 | 13ms | 14ms | 14ms | active 유저 카운트 |
| MGET | ~0.2 | 10ms | 11ms | 11ms | heartbeat score 조회 |
| **전체** | **~127.2K** | | | | |

#### 결론

App CPU 89%, Redis CPU 50%로 병목은 App 쪽에 있습니다. 동일 스펙의 App 서버를 하나 더 두어 로드밸런싱하고, Redis CPU를 더 활용하면 간단하게 트래픽을 2배 정도는 더 수용할 수 있을 것으로 예상됩니다.

---

## 모니터링 (Prometheus + Grafana)

Spring Boot Actuator + Micrometer + Redis Exporter로 메트릭을 수집하고, Prometheus + Grafana로 시각화합니다.

### 구성

```
App (Actuator /actuator/prometheus)
    │                                    Redis Exporter (:9121)
    │  10초 주기 스크래핑                       │
    ▼                                         ▼
Prometheus (:9090)  ◄─────────────────────────┘
    │
    │  데이터소스
    ▼
Grafana (:3000)  →  ZTicket 대시보드 (자동 프로비저닝)
```

`docker compose up -d` 후 [localhost:3000](http://localhost:3000)에서 `ZTicket Monitoring` 대시보드가 자동 프로비저닝되어 있습니다.

### 대시보드 패널

Row 단위로 그룹핑되어 있으며, 각 Row를 클릭하면 접고 펼 수 있습니다.

#### HTTP

| 패널 | 설명 |
|------|------|
| HTTP Request Rate (req/s) | 엔드포인트별 초당 요청 수 |
| HTTP Error Rate (4xx + 5xx) | 4xx/5xx 에러 요청 비율 |
| HTTP Response Time (p95 / p99 / p99.9) | 응답 시간 백분위수 |
| Logback Error / Warn Events | 애플리케이션 에러/경고 발생률 |

#### App - JVM

| 패널 | 설명 |
|------|------|
| CPU Usage | process CPU(앱)와 system CPU(전체) 사용률 |
| JVM Heap Memory | 힙 메모리 used / committed / max(-Xmx) |
| JVM Threads | 라이브/피크 플랫폼 스레드 수 (VT는 미포함) |

#### App - GC

| 패널 | 설명 |
|------|------|
| GC STW Duration | GC 1회당 평균/최대 Stop-The-World 시간 |
| GC Count Rate | 초당 GC 발생 횟수 (action/cause별) |
| Full GC / Humongous Allocation Count | Major GC와 Humongous 할당 발생률. 0이 아니면 주의 |
| Heap Usage After GC | GC 후 Old 영역 사용률. 90% 이상이면 힙 부족 또는 메모리 누수 의심 |
| GC Overhead | 전체 CPU 시간 중 GC에 소비되는 비율. 10% 이상이면 주의, 25% 이상이면 심각 |
| Memory Promoted / Allocated Rate | Eden 할당 속도(allocated)와 Old 승격 속도(promoted). 승격이 많으면 Full GC 위험 |

#### App - Database Pool

| 패널 | 설명 |
|------|------|
| HikariCP Acquire Time | DB 커넥션 획득까지 평균/최대 대기 시간 |
| HikariCP Connection Timeout | 커넥션 획득 타임아웃 발생률. 0이 아니면 풀 크기 부족 |

#### Redis - Client (Lettuce)

| 패널 | 설명 |
|------|------|
| Redis Command Rate (ops/s) | 앱에서 Redis로 보내는 명령별 초당 처리량 |
| Redis Latency (p95 / p99 / p99.9) | 앱이 관측한 Redis 명령 응답 시간 백분위수 |

#### Redis - Server

| 패널 | 설명 |
|------|------|
| Redis CPU Usage | Redis 서버 CPU 사용률 (total = user + system). user는 명령 처리, system은 네트워크 I/O |
| Redis Memory | Redis 메모리 사용량 vs maxmemory 한도 |
| Redis Connected Clients | 연결된 클라이언트 수와 블로킹된 클라이언트 수 |
| Redis Commands/s & Hit Rate | Redis 서버 초당 명령 처리량과 키 조회 적중률 |

### Actuator 엔드포인트

| Path | 설명 |
|------|------|
| `/actuator/health` | 앱·DB·Redis 상태 확인 |
| `/actuator/prometheus` | Prometheus 스크래핑용 메트릭 |
| `/actuator/metrics` | 등록된 메트릭 목록 조회 |
| `/actuator/env` | 환경 변수 및 설정 프로퍼티 조회 |
| `/actuator/conditions` | 자동 구성 조건 평가 결과 |
| `/actuator/beans` | 등록된 Spring Bean 목록 |

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
│   │   │   │   ├── EnterQueueUseCase.java         대기열 진입
│   │   │   │   ├── GetQueueTokenUseCase.java      토큰 상태·순번 조회
│   │   │   │   └── AdmitUsersUseCase.java         잠수 제거 + 배치 입장
│   │   │   └── out/
│   │   │       ├── WaitingQueuePort.java          대기열 Sorted Set 조작
│   │   │       ├── WaitingQueueHeartbeatPort.java             heartbeat Sorted Set 조작
│   │   │       └── ActiveUserPort.java            active 유저 SET 조작
│   │   ├── QueueService.java                      대기열 비즈니스 로직
│   │   └── WaitingQueueOperator.java              대기열+heartbeat 조합 연산
│   └── adapter/
│       ├── in/
│       │   ├── web/
│       │   │   ├── QueueApiController.java     /api/queues/tokens/**
│       │   │   └── dto/
│       │   │       ├── TokenResponse.java              진입 응답 (uuid)
│       │   │       └── QueueStatusResponse.java        폴링 응답 (status, rank)
│       │   └── scheduler/
│       │       └── AdmissionScheduler.java          5초 잠수 제거 + 배치 입장
│       └── out/
│           └── redis/
│               ├── WaitingQueueRedisAdapter.java  Sorted Set 기반 대기열
│               ├── WaitingQueueHeartbeatRedisAdapter.java     Sorted Set 기반 heartbeat
│               └── ActiveUserRedisAdapter.java    SET 기반 active 관리
│
├── seat/                                       좌석 도메인 (독립)
│   ├── domain/
│   │   ├── SeatStatus.java                     enum: AVAILABLE, HELD, PAID, UNKNOWN
│   │   ├── Seat.java                           좌석 상태 + 소유자 (도메인 객체)
│   │   └── Seats.java                          좌석 상태 맵 래퍼
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   └── GetSeatsUseCase.java           좌석 현황 조회
│   │   │   └── out/
│   │   │       └── SeatPort.java           hold/pay/release/getStatuses
│   │   └── SeatService.java                    좌석 현황 조회
│   └── adapter/
│       ├── in/
│       │   └── web/
│       │       ├── SeatApiController.java      /api/seats, /api/seats/available-count
│       │       └── dto/
│       │           ├── SeatStatusResponse.java         좌석별 상태
│       │           └── AvailableCountResponse.java    잔여 좌석 수
│       └── out/
│           └── redis/
│               ├── SeatRedisAdapter.java   holdSeat(setIfAbsent) + paySeat(SET)
│               └── RedisSeat.java             Redis 값 파싱 DTO
│
├── ticket/                                     티켓 도메인 (→ queue, seat 의존)
│   ├── domain/
│   │   ├── Ticket.java                         도메인 엔티티
│   │   ├── TicketStatus.java                   enum: PAID, SYNCED
│   │   └── TicketPaidEvent.java                record(ticketUuid) - 비동기 후처리 이벤트
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── PurchaseTicketUseCase.java  purchase(queueToken, seatNumber)
│   │   │   │   └── SyncTicketUseCase.java      syncPaidTickets()
│   │   │   └── out/
│   │   │       └── TicketPort.java  save/findByUuid/findByStatus
│   │   ├── TicketService.java                  동기 3단계 구매 + 이벤트 발행
│   │   ├── TicketSyncService.java              PAID 티켓 이벤트 재발행 (배치 복구)
│   │   └── TicketPaidEventListener.java        @Async 후처리 (paid 전환, SYNCED, deactivate)
│   └── adapter/
│       ├── in/
│       │   ├── web/
│       │   │   ├── TicketApiController.java    /api/tickets
│       │   │   └── dto/
│       │   │       ├── PurchaseRequest.java    { seatNumber: 7 }
│       │   │       └── PurchaseResponse.java   구매 결과 (ticketId, seatNumber)
│       │   └── scheduler/
│       │       └── SyncScheduler.java          1분 PAID 동기화
│       └── out/
│           └── persistence/
│               ├── TicketJpaEntity.java         seatNumber UNIQUE
│               ├── TicketJpaRepository.java        Spring Data JPA
│               └── TicketJpaAdapter.java        Upsert 패턴 (findById 기반)
│
├── common/
│   ├── web/
│   │   └── PageController.java                 Thymeleaf 뷰 (여러 도메인에 걸침)
│   ├── exception/
│   │   ├── ErrorCode.java                         에러 코드 enum
│   │   ├── BusinessException.java                 비즈니스 예외
│   │   └── GlobalExceptionHandler.java            @RestControllerAdvice
│   └── dto/
│       └── ErrorResponse.java                     에러 응답 DTO
│
└── config/
    └── AsyncConfig.java                       @Async 설정 + AsyncUncaughtExceptionHandler
```

### 도메인 간 의존 관계

```
ticket → queue (ActiveUserPort: 활성 사용자 검증)
ticket → seat  (SeatPort: 좌석 선점/결제)
seat   → (독립)
queue  → (독립)
```

### Thymeleaf 화면

```
src/main/resources/templates/
├── index.html              메인 (대기열 진입 버튼)
├── queue.html              대기열 (순번 표시, 5초 폴링, ACTIVE 시 자동 이동)
├── purchase.html           좌석 선택 + 구매 (1000석, 50×20 그리드, A1~AX20)
└── confirmation.html       구매 결과 (성공/실패)
```

---

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/queues/tokens` | 대기열 진입, UUID 토큰 반환 | 없음 |
| GET | `/api/queues/tokens/{uuid}` | 대기 순번/상태 조회 | 없음 |
| GET | `/api/seats` | 전체 좌석 현황 조회 | 없음 |
| GET | `/api/seats/available-count` | 잔여 좌석 수 (Caffeine 캐시, 5초 TTL, sync=true) | 없음 |
| POST | `/api/tickets` | 좌석 구매 | `X-Queue-Token` 헤더 |

---

## Redis 키 설계

| Key Pattern | Type | 값 예시 | TTL | 용도 |
|-------------|------|---------|-----|------|
| `waiting_queue` | Sorted Set | member=UUID, score=진입시각 | 없음 | FIFO 대기열 (rank 조회) |
| `waiting_queue_heartbeat` | Sorted Set | member=UUID, score=마지막 폴링시각 | 없음 | 잠수 유저 감지 (ZREMRANGEBYSCORE) |
| `active_user:{uuid}` | String | `"1"` | 300초 | 입장 허용 상태 |
| `seat:{seatNumber}` | String | `"held:{token}"` | 300초 | 좌석 임시 선점 |
| `seat:{seatNumber}` | String | `"paid:{token}"` | 없음 (SET 자동 제거) | 좌석 결제 확정 |

---

## 설정값

```yaml
zticket:
  admission:
    interval-ms: 5000       # 입장 스케줄러 실행 주기 (5초)
    active-ttl-seconds: 300 # 입장 후 구매 가능 시간 (5분)
    max-active-users: ${zticket.seat.total-count}  # 동시 active 유저 상한 (= 총 좌석 수)
    batch-size: 100         # 주기당 최대 입장 인원
    queue-ttl-seconds: 60   # 대기열 잠수 제거 기준 (60초간 폴링 없으면 제거)
  seat:
    total-count: 1000       # 총 좌석 수
    hold-ttl-seconds: 300   # 좌석 선점 유지 시간 (5분)
  sync:
    interval-ms: 60000      # 동기화 워커 실행 주기 (1분)
```

**핵심 제약**:
- `active-ttl-seconds(300초)` >= `hold-ttl-seconds(300초)`: active 세션이 좌석 선점보다 먼저 만료되면 구매를 못 하는데 좌석만 잡혀있는 상태가 됩니다. hold가 먼저 만료되면 구매 중 좌석이 풀리므로, active TTL은 hold TTL과 같거나 커야 합니다.
- `sync.interval-ms(60초)` < `hold-ttl-seconds(300초)`: 동기화 워커가 TTL 만료 전에 실행되어야 합니다. 단, DB `seatNumber UNIQUE` 제약이 최종 방어선으로 중복 판매를 차단합니다.


