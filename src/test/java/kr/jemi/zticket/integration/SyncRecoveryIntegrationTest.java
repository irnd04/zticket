package kr.jemi.zticket.integration;

import kr.jemi.zticket.ticket.application.port.in.SyncTicketUseCase;
import kr.jemi.zticket.ticket.application.port.out.TicketPersistencePort;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SyncRecoveryIntegrationTest extends IntegrationTestBase {

    @Autowired
    SyncTicketUseCase syncTicketUseCase;

    @Autowired
    TicketPersistencePort ticketPersistencePort;

    @Test
    @DisplayName("Case 2 복구: Redis held + DB PAID -> syncPaidTickets -> Redis paid + DB SYNCED")
    void case2_redis_held_db_paid_sync_recovers() {
        String token = "token-1";
        int seatNumber = 1;

        redisTemplate.opsForValue().set("seat:" + seatNumber, "held:" + token, 300, TimeUnit.SECONDS);
        Ticket ticket = Ticket.create(token, seatNumber);
        ticketPersistencePort.save(ticket);

        syncTicketUseCase.syncPaidTickets();

        assertThat(redisTemplate.opsForValue().get("seat:" + seatNumber))
                .as("Redis held -> paid 전환")
                .isEqualTo("paid:" + token);

        assertThat(ticketPersistencePort.findByUuid(ticket.getUuid()))
                .as("DB PAID -> SYNCED 전환")
                .hasValueSatisfying(t ->
                        assertThat(t.getStatus()).isEqualTo(TicketStatus.SYNCED)
                );
    }

    @Test
    @DisplayName("Case 3 복구: Redis paid + DB PAID -> syncPaidTickets -> DB SYNCED (멱등)")
    void case3_redis_paid_db_paid_sync_idempotent() {
        String token = "token-1";
        int seatNumber = 1;

        redisTemplate.opsForValue().set("seat:" + seatNumber, "paid:" + token);
        Ticket ticket = Ticket.create(token, seatNumber);
        ticketPersistencePort.save(ticket);

        syncTicketUseCase.syncPaidTickets();

        assertThat(redisTemplate.opsForValue().get("seat:" + seatNumber))
                .as("Redis paid 유지")
                .isEqualTo("paid:" + token);

        assertThat(ticketPersistencePort.findByUuid(ticket.getUuid()))
                .as("DB SYNCED 멱등 전환")
                .hasValueSatisfying(t ->
                        assertThat(t.getStatus()).isEqualTo(TicketStatus.SYNCED)
                );
    }

    @Test
    @DisplayName("TTL 만료 후 복구: Redis 키 없음 + DB PAID -> syncPaidTickets -> Redis paid 복원")
    void ttl_expired_sync_restores_redis() {
        String token = "token-1";
        int seatNumber = 1;
        String seatKey = "seat:" + seatNumber;

        Ticket ticket = Ticket.create(token, seatNumber);
        ticketPersistencePort.save(ticket);

        assertThat(redisTemplate.hasKey(seatKey))
                .as("TTL 만료 시뮬레이션: Redis 키 부재")
                .isFalse();

        syncTicketUseCase.syncPaidTickets();

        assertThat(redisTemplate.opsForValue().get(seatKey))
                .as("Redis paid 복원")
                .isEqualTo("paid:" + token);

        assertThat(ticketPersistencePort.findByUuid(ticket.getUuid()))
                .as("DB SYNCED 전환")
                .hasValueSatisfying(t ->
                        assertThat(t.getStatus()).isEqualTo(TicketStatus.SYNCED)
                );
    }
}
