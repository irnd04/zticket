package kr.jemi.zticket.integration;

import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.seat.application.port.out.SeatPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import kr.jemi.zticket.seat.domain.Seat;
import kr.jemi.zticket.seat.domain.SeatStatus;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TtlExpiryIntegrationTest extends IntegrationTestBase {

    @Autowired
    SeatPort seatPort;

    @Autowired
    ActiveUserPort activeUserPort;

    @Test
    @DisplayName("held 좌석 TTL 만료: hold 후 TTL(3초) 대기 -> 좌석 available로 복구")
    void held_seat_expires_after_ttl() {
        String token = "token-1";
        int seatNumber = 1;
        String seatKey = "seat:" + seatNumber;

        seatPort.holdSeat(seatNumber, token, 3);
        assertThat(redisTemplate.hasKey(seatKey)).as("hold 직후 키 존재").isTrue();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(redisTemplate.hasKey(seatKey)).as("TTL 만료 후 키 소멸").isFalse()
                );

        assertThat(seatPort.getStatuses(List.of(seatNumber)).of(seatNumber))
                .as("좌석 available 복구")
                .isEqualTo(new Seat(SeatStatus.AVAILABLE, null));
    }

    @Test
    @DisplayName("active 유저 TTL 만료: activate 후 TTL(3초) 대기 -> isActive=false")
    void active_user_expires_after_ttl() {
        String token = "token-1";

        activeUserPort.activate(token, 3);
        assertThat(activeUserPort.isActive(token)).as("활성화 직후").isTrue();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(activeUserPort.isActive(token)).as("TTL 만료 후 비활성").isFalse()
                );
    }

    @Test
    @DisplayName("releaseSeat: 자신의 held 키만 삭제한다")
    void releaseSeat_deletes_only_own_held_key() {
        String token = "token-1";
        int seatNumber = 1;

        seatPort.holdSeat(seatNumber, token, 300);
        seatPort.releaseSeat(seatNumber, token);

        assertThat(redisTemplate.hasKey("seat:" + seatNumber))
                .as("자신의 held 키 삭제 성공").isFalse();
    }

    @Test
    @DisplayName("releaseSeat: 다른 유저의 held 키는 삭제하지 않는다")
    void releaseSeat_does_not_delete_other_users_held_key() {
        String tokenA = "token-A";
        String tokenB = "token-B";
        int seatNumber = 1;

        seatPort.holdSeat(seatNumber, tokenB, 300);
        seatPort.releaseSeat(seatNumber, tokenA);

        assertThat(redisTemplate.opsForValue().get("seat:" + seatNumber))
                .as("B의 held 키가 그대로 남아있음").isEqualTo("held:" + tokenB);
    }

    @Test
    @DisplayName("releaseSeat: paid 상태의 키는 삭제하지 않는다")
    void releaseSeat_does_not_delete_paid_key() {
        String tokenA = "token-A";
        String tokenB = "token-B";
        int seatNumber = 1;

        seatPort.paySeat(seatNumber, tokenB);
        seatPort.releaseSeat(seatNumber, tokenA);

        assertThat(redisTemplate.opsForValue().get("seat:" + seatNumber))
                .as("B의 paid 키가 그대로 남아있음").isEqualTo("paid:" + tokenB);
    }

    @Test
    @DisplayName("releaseSeat: 키가 존재하지 않아도 에러 없이 처리된다")
    void releaseSeat_no_error_when_key_not_exists() {
        seatPort.releaseSeat(999, "token-1");

        assertThat(redisTemplate.hasKey("seat:999"))
                .as("키가 없어도 에러 없음").isFalse();
    }

    @Test
    @DisplayName("A TTL 만료 → B 선점+결제 완료(SYNCED) → A의 releaseSeat은 B의 paid 키를 삭제하지 않는다")
    void lua_script_protects_paid_key_from_stale_thread_rollback() {
        String tokenA = "token-A";
        String tokenB = "token-B";
        int seatNumber = 1;
        String seatKey = "seat:" + seatNumber;

        // 1. A가 좌석 선점 (짧은 TTL)
        seatPort.holdSeat(seatNumber, tokenA, 3);

        // 2. A의 스레드 장시간 멈춤 시뮬레이션 → held TTL 만료
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(redisTemplate.hasKey(seatKey)).as("A의 held TTL 만료").isFalse()
                );

        // 3. B가 같은 좌석 선점 → DB INSERT 성공 → 비동기 처리 완료 (paid + SYNCED)
        assertThat(seatPort.holdSeat(seatNumber, tokenB, 300))
                .as("B 선점 성공").isTrue();
        seatPort.paySeat(seatNumber, tokenB);

        // 4. A의 스레드 재개 → DB INSERT UNIQUE 위반 실패 → catch 블록에서 releaseSeat 호출
        seatPort.releaseSeat(seatNumber, tokenA);

        // 5. Lua 스크립트가 "held:A" != "paid:B" 이므로 삭제하지 않음
        assertThat(redisTemplate.opsForValue().get(seatKey))
                .as("Lua 스크립트가 B의 paid 키를 보호")
                .isEqualTo("paid:" + tokenB);
    }

    @Test
    @DisplayName("TTL 만료 후 재선점: hold TTL 만료 후 다른 유저가 같은 좌석 hold 성공")
    void rehold_after_ttl_expiry() {
        String token1 = "token-1";
        String token2 = "token-2";
        int seatNumber = 1;
        String seatKey = "seat:" + seatNumber;

        assertThat(seatPort.holdSeat(seatNumber, token1, 3))
                .as("첫 번째 hold 성공").isTrue();
        assertThat(seatPort.holdSeat(seatNumber, token2, 300))
                .as("중복 hold 실패").isFalse();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(redisTemplate.hasKey(seatKey)).as("TTL 만료").isFalse()
                );

        assertThat(seatPort.holdSeat(seatNumber, token2, 300))
                .as("만료 후 재선점 성공").isTrue();

        assertThat(redisTemplate.opsForValue().get(seatKey))
                .isEqualTo("held:" + token2);
    }
}
