package kr.jemi.zticket.integration;

import kr.jemi.zticket.seat.application.port.in.GetSeatsUseCase;
import kr.jemi.zticket.seat.domain.Seat;
import kr.jemi.zticket.seat.domain.SeatStatus;
import kr.jemi.zticket.ticket.application.port.in.PurchaseTicketUseCase;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.seat.domain.Seats;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class TicketPurchaseIntegrationTest extends IntegrationTestBase {

    @Autowired
    PurchaseTicketUseCase purchaseTicketUseCase;

    @Autowired
    GetSeatsUseCase getSeatsUseCase;

    @Autowired
    ActiveUserPort activeUserPort;

    @Autowired
    TicketPort ticketPort;

    @Test
    @DisplayName("정상 구매 E2E: DB PAID → 비동기 후처리(Redis paid, DB SYNCED)")
    void purchase_success_e2e() {
        String token = "test-token-1";
        int seatNumber = 1;

        activeUserPort.activate(token, 300);

        Ticket ticket = purchaseTicketUseCase.purchase(token, seatNumber);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);

        // 비동기 후처리 완료 대기
        await().atMost(5, SECONDS).untilAsserted(() -> {
            assertThat(redisTemplate.opsForValue().get("seat:" + seatNumber))
                    .as("Redis 좌석 상태")
                    .isEqualTo("paid:" + token);

            assertThat(ticketPort.findByUuid(ticket.getUuid()))
                    .as("DB 티켓 상태")
                    .hasValueSatisfying(dbTicket ->
                            assertThat(dbTicket.getStatus()).isEqualTo(TicketStatus.SYNCED)
                    );

            assertThat(activeUserPort.isActive(token))
                    .as("active 유저 제거")
                    .isFalse();
        });
    }

    @Test
    @DisplayName("비활성 사용자 거부: NOT_ACTIVE_USER")
    void purchase_not_active_user_rejected() {
        assertThatThrownBy(() -> purchaseTicketUseCase.purchase("inactive-token", 1))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_ACTIVE_USER)
                );
    }

    @Test
    @DisplayName("이미 선점된 좌석 거부: SEAT_ALREADY_HELD")
    void purchase_seat_already_held_rejected() {
        String token1 = "token-1";
        String token2 = "token-2";
        int seatNumber = 1;

        activeUserPort.activate(token1, 300);
        activeUserPort.activate(token2, 300);
        purchaseTicketUseCase.purchase(token1, seatNumber);

        assertThatThrownBy(() -> purchaseTicketUseCase.purchase(token2, seatNumber))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEAT_ALREADY_HELD)
                );
    }

    @Test
    @DisplayName("좌석 현황 조회: 구매 후 비동기 후처리 완료 시 paid 상태")
    void getSeats_after_purchase() {
        activeUserPort.activate("token-1", 300);
        purchaseTicketUseCase.purchase("token-1", 3);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            Seats statuses = getSeatsUseCase.getSeats();
            assertThat(statuses.of(3)).isEqualTo(new Seat(SeatStatus.PAID, "token-1"));
        });

        Seats statuses = getSeatsUseCase.getSeats();
        assertThat(statuses.of(1)).isEqualTo(new Seat(SeatStatus.AVAILABLE, null));
        assertThat(statuses.of(2)).isEqualTo(new Seat(SeatStatus.AVAILABLE, null));
        assertThat(statuses.of(4)).isEqualTo(new Seat(SeatStatus.AVAILABLE, null));
        assertThat(statuses.of(5)).isEqualTo(new Seat(SeatStatus.AVAILABLE, null));
    }
}
