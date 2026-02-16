package kr.jemi.zticket.application.ticket;

import kr.jemi.zticket.application.port.out.SeatHoldPort;
import kr.jemi.zticket.application.port.out.TicketPersistencePort;
import kr.jemi.zticket.domain.ticket.Ticket;
import kr.jemi.zticket.domain.ticket.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class TicketSyncServiceTest {

    @Mock
    private TicketPersistencePort ticketPersistencePort;

    @Mock
    private SeatHoldPort seatHoldPort;

    @InjectMocks
    private TicketSyncService ticketSyncService;

    @Nested
    @DisplayName("syncPaidTickets() - 정상 동기화")
    class NormalSync {

        @Test
        @DisplayName("PAID 상태 티켓을 Redis에 paid로 설정하고 DB를 SYNCED로 업데이트한다")
        void shouldSyncPaidTicketToRedisAndUpdateDbToSynced() {
            // given - Case 2: Redis held + DB PAID 상태 (서버 다운 후 복구 시나리오)
            Ticket paidTicket = new Ticket(null, "uuid-1", 7, TicketStatus.PAID, "token-1", LocalDateTime.now(), null);
            given(ticketPersistencePort.findByStatus(TicketStatus.PAID))
                    .willReturn(List.of(paidTicket));

            // when
            ticketSyncService.syncPaidTickets();

            // then - setPaidSeat으로 Redis에 paid:{token} 덮어쓰기 + DB SYNCED
            InOrder inOrder = inOrder(seatHoldPort, ticketPersistencePort);
            inOrder.verify(seatHoldPort).setPaidSeat(7, "token-1");
            inOrder.verify(ticketPersistencePort).save(argThat(t ->
                    t.getUuid().equals("uuid-1") && t.getStatus() == TicketStatus.SYNCED));
        }

        @Test
        @DisplayName("여러 PAID 티켓을 모두 동기화한다")
        void shouldSyncMultiplePaidTickets() {
            // given
            Ticket ticket1 = new Ticket(null, "uuid-1", 7, TicketStatus.PAID, "token-1", LocalDateTime.now(), null);
            Ticket ticket2 = new Ticket(null, "uuid-2", 42, TicketStatus.PAID, "token-2", LocalDateTime.now(), null);
            Ticket ticket3 = new Ticket(null, "uuid-3", 100, TicketStatus.PAID, "token-3", LocalDateTime.now(), null);
            given(ticketPersistencePort.findByStatus(TicketStatus.PAID))
                    .willReturn(List.of(ticket1, ticket2, ticket3));

            // when
            ticketSyncService.syncPaidTickets();

            // then
            then(seatHoldPort).should().setPaidSeat(7, "token-1");
            then(seatHoldPort).should().setPaidSeat(42, "token-2");
            then(seatHoldPort).should().setPaidSeat(100, "token-3");
            then(ticketPersistencePort).should(times(3)).save(any(Ticket.class));
        }

        @Test
        @DisplayName("PAID 티켓이 없으면 아무 작업도 하지 않는다")
        void shouldDoNothingWhenNoPaidTickets() {
            // given
            given(ticketPersistencePort.findByStatus(TicketStatus.PAID))
                    .willReturn(List.of());

            // when
            ticketSyncService.syncPaidTickets();

            // then
            then(seatHoldPort).shouldHaveNoInteractions();
            then(ticketPersistencePort).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("syncPaidTickets() - Case 2 복구: Redis held + DB PAID (서버 다운 후)")
    class Case2Recovery {

        @Test
        @DisplayName("Redis에 held 상태인 좌석을 paid로 덮어쓴다 (DB가 source of truth)")
        void shouldOverwriteHeldWithPaid() {
            // 시나리오: purchase() 4단계(paySeat)에서 서버 다운
            // 상태: Redis에 held:{token} (TTL 째깍), DB에 PAID
            // 워커가 setPaidSeat으로 Redis를 paid:{token}으로 복원
            Ticket ticket = new Ticket(null, "uuid-1", 7, TicketStatus.PAID, "token-1", LocalDateTime.now(), null);
            given(ticketPersistencePort.findByStatus(TicketStatus.PAID))
                    .willReturn(List.of(ticket));

            // when
            ticketSyncService.syncPaidTickets();

            // then - setPaidSeat은 기존 값을 무조건 덮어씀 (held든 null이든)
            then(seatHoldPort).should().setPaidSeat(7, "token-1");
        }
    }

    @Nested
    @DisplayName("syncPaidTickets() - Case 3 복구: Redis paid + DB PAID (SYNCED 업데이트 실패)")
    class Case3Recovery {

        @Test
        @DisplayName("이미 Redis에 paid인 좌석도 setPaidSeat을 재실행한다 (멱등)")
        void shouldBeIdempotentOnAlreadyPaidRedis() {
            // 시나리오: purchase() 5단계(DB SYNCED)에서 서버 다운
            // 상태: Redis에 paid:{token} (영구), DB에 PAID
            // 워커가 setPaidSeat 재실행 → 동일한 결과 → DB SYNCED
            Ticket ticket = new Ticket(null, "uuid-1", 7, TicketStatus.PAID, "token-1", LocalDateTime.now(), null);
            given(ticketPersistencePort.findByStatus(TicketStatus.PAID))
                    .willReturn(List.of(ticket));

            // when
            ticketSyncService.syncPaidTickets();

            // then - setPaidSeat은 이미 paid여도 같은 값을 다시 쓰므로 안전
            then(seatHoldPort).should().setPaidSeat(7, "token-1");
            then(ticketPersistencePort).should().save(argThat(t ->
                    t.getStatus() == TicketStatus.SYNCED));
        }
    }

    @Nested
    @DisplayName("syncPaidTickets() - 부분 실패 처리")
    class PartialFailure {

        @Test
        @DisplayName("한 티켓 동기화 실패 시 나머지 티켓은 계속 처리한다")
        void shouldContinueOnSingleTicketFailure() {
            // given
            Ticket ticket1 = new Ticket(null, "uuid-1", 7, TicketStatus.PAID, "token-1", LocalDateTime.now(), null);
            Ticket ticket2 = new Ticket(null, "uuid-2", 42, TicketStatus.PAID, "token-2", LocalDateTime.now(), null);
            given(ticketPersistencePort.findByStatus(TicketStatus.PAID))
                    .willReturn(List.of(ticket1, ticket2));

            // ticket1의 setPaidSeat에서 예외 발생
            willThrow(new RuntimeException("Redis timeout"))
                    .given(seatHoldPort).setPaidSeat(7, "token-1");

            // when
            ticketSyncService.syncPaidTickets();

            // then - ticket2는 정상 처리됨
            then(seatHoldPort).should().setPaidSeat(42, "token-2");
            then(ticketPersistencePort).should().save(argThat(t ->
                    t.getUuid().equals("uuid-2") && t.getStatus() == TicketStatus.SYNCED));
        }

        @Test
        @DisplayName("DB save 실패 시 다음 주기에 다시 시도할 수 있도록 PAID 상태가 유지된다")
        void shouldRetryOnNextCycleWhenDbSaveFails() {
            // given
            Ticket ticket = new Ticket(null, "uuid-1", 7, TicketStatus.PAID, "token-1", LocalDateTime.now(), null);
            given(ticketPersistencePort.findByStatus(TicketStatus.PAID))
                    .willReturn(List.of(ticket));
            willThrow(new RuntimeException("DB error"))
                    .given(ticketPersistencePort).save(any(Ticket.class));

            // when - 예외가 잡히므로 syncPaidTickets 자체는 정상 종료
            ticketSyncService.syncPaidTickets();

            // then - setPaidSeat은 호출됨 (Redis는 이미 paid로 변경됨)
            // 다음 주기에 DB findByStatus(PAID)에서 다시 조회되어 재시도됨
            then(seatHoldPort).should().setPaidSeat(7, "token-1");
        }
    }

    @Nested
    @DisplayName("syncPaidTickets() - 동기화 순서 검증")
    class SyncOrder {

        @Test
        @DisplayName("각 티켓에 대해 Redis 설정 → DB 저장 순서로 실행된다")
        void shouldSetRedisBeforeDbUpdate() {
            // given
            Ticket ticket = new Ticket(null, "uuid-1", 7, TicketStatus.PAID, "token-1", LocalDateTime.now(), null);
            given(ticketPersistencePort.findByStatus(TicketStatus.PAID))
                    .willReturn(List.of(ticket));

            // when
            ticketSyncService.syncPaidTickets();

            // then - Redis paid 설정이 DB SYNCED보다 먼저 실행됨
            // 이 순서가 중요: Redis가 먼저 paid로 설정되어야 TTL 만료 전에 좌석이 보호됨
            InOrder inOrder = inOrder(seatHoldPort, ticketPersistencePort);
            inOrder.verify(seatHoldPort).setPaidSeat(7, "token-1");
            inOrder.verify(ticketPersistencePort).save(any(Ticket.class));
        }
    }
}
