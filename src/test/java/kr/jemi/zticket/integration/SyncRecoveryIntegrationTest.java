package kr.jemi.zticket.integration;

import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.seat.application.port.out.SeatPort;
import kr.jemi.zticket.ticket.application.port.in.SyncTicketUseCase;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SyncRecoveryIntegrationTest extends IntegrationTestBase {

    @Autowired
    SyncTicketUseCase syncTicketUseCase;

    @Autowired
    TicketPort ticketPort;

    @Autowired
    ActiveUserPort activeUserPort;

    @Autowired
    SeatPort seatPort;

    @Test
    @DisplayName("Case 2 복구: Redis held + DB PAID -> syncPaidTickets -> Redis paid + DB SYNCED")
    void case2_redis_held_db_paid_sync_recovers() {
        String token = "token-1";
        int seatNumber = 1;

        activeUserPort.activate(token, 300);
        seatPort.holdSeat(seatNumber, token, 300);
        Ticket ticket = Ticket.create(token, seatNumber);
        ticketPort.save(ticket);

        syncTicketUseCase.syncPaidTickets();

        await().atMost(5, SECONDS).untilAsserted(() -> {
            assertThat(redisTemplate.opsForValue().get("seat:" + seatNumber))
                    .as("Redis held -> paid 전환")
                    .isEqualTo("paid:" + token);

            assertThat(ticketPort.findByUuid(ticket.getUuid()))
                    .as("DB PAID -> SYNCED 전환")
                    .hasValueSatisfying(t ->
                            assertThat(t.getStatus()).isEqualTo(TicketStatus.SYNCED)
                    );

            assertThat(activeUserPort.isActive(token))
                    .as("active 유저 제거")
                    .isFalse();
        });
    }

    @Test
    @DisplayName("Case 3 복구: Redis paid + DB PAID -> syncPaidTickets -> DB SYNCED (멱등)")
    void case3_redis_paid_db_paid_sync_idempotent() {
        String token = "token-1";
        int seatNumber = 1;

        activeUserPort.activate(token, 300);
        seatPort.paySeat(seatNumber, token);
        Ticket ticket = Ticket.create(token, seatNumber);
        ticketPort.save(ticket);

        syncTicketUseCase.syncPaidTickets();

        await().atMost(5, SECONDS).untilAsserted(() -> {
            assertThat(redisTemplate.opsForValue().get("seat:" + seatNumber))
                    .as("Redis paid 유지")
                    .isEqualTo("paid:" + token);

            assertThat(ticketPort.findByUuid(ticket.getUuid()))
                    .as("DB SYNCED 멱등 전환")
                    .hasValueSatisfying(t ->
                            assertThat(t.getStatus()).isEqualTo(TicketStatus.SYNCED)
                    );

            assertThat(activeUserPort.isActive(token))
                    .as("active 유저 제거")
                    .isFalse();
        });
    }

    @Test
    @DisplayName("Case 2-2 복구: DB INSERT 성공 + 타임아웃 → releaseSeat으로 held 삭제 → sync 배치가 Redis paid 복원")
    void case2_2_db_insert_success_but_timeout_causes_rollback_then_sync_recovers() {
        String token = "token-1";
        int seatNumber = 1;
        String seatKey = "seat:" + seatNumber;

        // 1. 활성 유저 + 좌석 선점
        activeUserPort.activate(token, 300);
        seatPort.holdSeat(seatNumber, token, 300);

        // 2. DB INSERT 성공 (PAID 저장됨)
        Ticket ticket = Ticket.create(token, seatNumber);
        ticketPort.save(ticket);

        // 3. 타임아웃으로 앱이 실패로 인식 → catch 블록에서 Lua releaseSeat 실행
        seatPort.releaseSeat(seatNumber, token);

        // 4. 상태 확인: DB에는 PAID 있지만 Redis 키는 삭제됨
        assertThat(redisTemplate.hasKey(seatKey))
                .as("releaseSeat으로 held 키 삭제됨").isFalse();
        assertThat(ticketPort.findByUuid(ticket.getUuid()))
                .as("DB에는 PAID 레코드 존재")
                .hasValueSatisfying(t ->
                        assertThat(t.getStatus()).isEqualTo(TicketStatus.PAID)
                );

        // 5. sync 배치 실행 → 복구
        syncTicketUseCase.syncPaidTickets();

        await().atMost(5, SECONDS).untilAsserted(() -> {
            assertThat(redisTemplate.opsForValue().get(seatKey))
                    .as("Redis paid 복원")
                    .isEqualTo("paid:" + token);

            assertThat(ticketPort.findByUuid(ticket.getUuid()))
                    .as("DB SYNCED 전환")
                    .hasValueSatisfying(t ->
                            assertThat(t.getStatus()).isEqualTo(TicketStatus.SYNCED)
                    );

            assertThat(activeUserPort.isActive(token))
                    .as("active 유저 제거")
                    .isFalse();
        });
    }

    @Test
    @DisplayName("TTL 만료 후 복구: Redis 키 없음 + DB PAID -> syncPaidTickets -> Redis paid 복원")
    void ttl_expired_sync_restores_redis() {
        String token = "token-1";
        int seatNumber = 1;
        String seatKey = "seat:" + seatNumber;

        activeUserPort.activate(token, 300);
        Ticket ticket = Ticket.create(token, seatNumber);
        ticketPort.save(ticket);

        assertThat(redisTemplate.hasKey(seatKey))
                .as("TTL 만료 시뮬레이션: Redis 키 부재")
                .isFalse();

        syncTicketUseCase.syncPaidTickets();

        await().atMost(5, SECONDS).untilAsserted(() -> {
            assertThat(redisTemplate.opsForValue().get(seatKey))
                    .as("Redis paid 복원")
                    .isEqualTo("paid:" + token);

            assertThat(ticketPort.findByUuid(ticket.getUuid()))
                    .as("DB SYNCED 전환")
                    .hasValueSatisfying(t ->
                            assertThat(t.getStatus()).isEqualTo(TicketStatus.SYNCED)
                    );

            assertThat(activeUserPort.isActive(token))
                    .as("active 유저 제거")
                    .isFalse();
        });
    }
}
