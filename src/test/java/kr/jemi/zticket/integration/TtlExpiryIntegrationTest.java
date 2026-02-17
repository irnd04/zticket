package kr.jemi.zticket.integration;

import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.seat.application.port.out.SeatHoldPort;
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
    SeatHoldPort seatHoldPort;

    @Autowired
    ActiveUserPort activeUserPort;

    @Test
    @DisplayName("held 좌석 TTL 만료: hold 후 TTL(3초) 대기 -> 좌석 available로 복구")
    void held_seat_expires_after_ttl() {
        String token = "token-1";
        int seatNumber = 1;
        String seatKey = "seat:" + seatNumber;

        seatHoldPort.holdSeat(seatNumber, token, 3);
        assertThat(redisTemplate.hasKey(seatKey)).as("hold 직후 키 존재").isTrue();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(redisTemplate.hasKey(seatKey)).as("TTL 만료 후 키 소멸").isFalse()
                );

        assertThat(seatHoldPort.getStatuses(List.of(seatNumber)).of(seatNumber))
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
    @DisplayName("TTL 만료 후 재선점: hold TTL 만료 후 다른 유저가 같은 좌석 hold 성공")
    void rehold_after_ttl_expiry() {
        String token1 = "token-1";
        String token2 = "token-2";
        int seatNumber = 1;
        String seatKey = "seat:" + seatNumber;

        assertThat(seatHoldPort.holdSeat(seatNumber, token1, 3))
                .as("첫 번째 hold 성공").isTrue();
        assertThat(seatHoldPort.holdSeat(seatNumber, token2, 300))
                .as("중복 hold 실패").isFalse();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(redisTemplate.hasKey(seatKey)).as("TTL 만료").isFalse()
                );

        assertThat(seatHoldPort.holdSeat(seatNumber, token2, 300))
                .as("만료 후 재선점 성공").isTrue();

        assertThat(redisTemplate.opsForValue().get(seatKey))
                .isEqualTo("held:" + token2);
    }
}
