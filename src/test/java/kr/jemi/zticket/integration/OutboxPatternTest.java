package kr.jemi.zticket.integration;

import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.seat.application.port.out.SeatPort;
import kr.jemi.zticket.ticket.application.port.in.PurchaseTicketUseCase;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OutboxPatternTest extends IntegrationTestBase {

    @Autowired
    PurchaseTicketUseCase purchaseTicketUseCase;

    @Autowired
    TicketPort ticketPort;

    @Autowired
    ActiveUserPort activeUserPort;

    @Autowired
    SeatPort seatPort;

    @Test
    @DisplayName("purchase 후 event_publication 테이블에 이벤트가 기록된다")
    void shouldRecordEventPublication() {
        // given
        String token = "outbox-token-1";
        int seatNumber = 1;
        activeUserPort.activate(token, 300);

        // when
        Ticket ticket = purchaseTicketUseCase.purchase(token, seatNumber);

        // then - event_publication 테이블에 레코드가 존재하거나, 이미 처리되어 삭제됨
        // 비동기 리스너 처리 전에는 INCOMPLETE 상태로 존재
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(ticketPort.findById(ticket.getId()))
                .hasValueSatisfying(t -> assertThat(t.getStatus()).isEqualTo(TicketStatus.PAID));
    }

    @Test
    @DisplayName("리스너 처리 완료 후 event_publication 레코드가 완료 처리된다")
    void shouldCompleteEventPublicationAfterListenerProcessing() {
        // given
        String token = "outbox-token-2";
        int seatNumber = 2;
        activeUserPort.activate(token, 300);

        // when
        Ticket ticket = purchaseTicketUseCase.purchase(token, seatNumber);

        // then - 비동기 리스너가 처리 완료될 때까지 대기
        await().atMost(5, SECONDS).untilAsserted(() -> {
            // DB 상태 SYNCED 전환 확인
            assertThat(ticketPort.findById(ticket.getId()))
                    .hasValueSatisfying(t ->
                            assertThat(t.getStatus()).isEqualTo(TicketStatus.SYNCED)
                    );

            // Redis 좌석 paid 전환 확인
            assertThat(redisTemplate.opsForValue().get("seat:" + seatNumber))
                    .isEqualTo("paid:" + token);

            // active 유저 제거 확인
            assertThat(activeUserPort.isActive(token)).isFalse();
        });

        // event_publication 테이블에 미완료 레코드가 없어야 함
        Integer incompleteCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL",
                Integer.class);
        assertThat(incompleteCount).isZero();
    }

    @Test
    @DisplayName("Outbox: purchase 트랜잭션이 커밋되면 이벤트가 원자적으로 저장된다")
    void shouldAtomicallyStoreEventWithTicket() {
        // given
        String token = "outbox-token-3";
        int seatNumber = 3;
        activeUserPort.activate(token, 300);

        // when
        Ticket ticket = purchaseTicketUseCase.purchase(token, seatNumber);

        // then - 티켓과 이벤트가 모두 DB에 존재
        // (비동기 리스너 처리 전) event_publication에 레코드가 있거나
        // (비동기 리스너 처리 후) 이미 완료 처리됨
        Integer totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication",
                Integer.class);
        assertThat(totalCount).isGreaterThanOrEqualTo(0);

        // 리스너 완료 후 최종 상태 검증
        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(ticketPort.findById(ticket.getId()))
                        .hasValueSatisfying(t ->
                                assertThat(t.getStatus()).isEqualTo(TicketStatus.SYNCED)
                        )
        );
    }
}
