package kr.jemi.zticket.integration;

import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.seat.application.port.out.SeatPort;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.ticket.application.service.TicketWriter;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SyncRecoveryIntegrationTest extends IntegrationTestBase {

    @Autowired
    TicketWriter ticketWriter;

    @Autowired
    TicketPort ticketPort;

    @Autowired
    ActiveUserPort activeUserPort;

    @Autowired
    SeatPort seatPort;

    @Test
    @DisplayName("Outbox 복구: insertAndPublish 후 리스너가 처리하면 Redis paid + DB SYNCED로 전환된다")
    void outbox_insert_and_publish_triggers_listener() {
        String token = "token-1";
        int seatNumber = 1;

        activeUserPort.activate(token, 300);
        seatPort.holdSeat(seatNumber, token, 300);
        Ticket ticket = Ticket.create(seatNumber, token, seatNumber);
        ticketWriter.insertAndPublish(ticket);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            assertThat(redisTemplate.opsForValue().get("seat:" + seatNumber))
                    .as("Redis held -> paid 전환")
                    .isEqualTo("paid:" + token);

            assertThat(ticketPort.findById(ticket.getId()))
                    .as("DB PAID -> SYNCED 전환")
                    .hasValueSatisfying(t ->
                            assertThat(t.getStatus()).isEqualTo(TicketStatus.SYNCED)
                    );

            assertThat(activeUserPort.isActive(token))
                    .as("active 유저 제거")
                    .isFalse();
        });
    }
}
