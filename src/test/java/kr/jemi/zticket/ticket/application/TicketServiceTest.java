package kr.jemi.zticket.ticket.application;

import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.seat.application.port.out.SeatHoldPort;
import kr.jemi.zticket.ticket.application.port.out.TicketPersistencePort;
import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private SeatHoldPort seatHoldPort;

    @Mock
    private TicketPersistencePort ticketPersistencePort;

    @Mock
    private ActiveUserPort activeUserPort;

    private TicketService ticketService;

    private static final long HOLD_TTL_SECONDS = 300L;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
                seatHoldPort, ticketPersistencePort, activeUserPort, HOLD_TTL_SECONDS);
    }

    @Nested
    @DisplayName("purchase() - 5단계 구매 플로우")
    class Purchase {

        @Test
        @DisplayName("정상 구매: 5단계 모두 성공 시 SYNCED 티켓을 반환한다")
        void shouldReturnSyncedTicketOnFullSuccess() {
            // given
            String token = "active-token";
            int seatNumber = 7;
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketPersistencePort.save(any(Ticket.class))).willAnswer(inv -> inv.getArgument(0));
            given(seatHoldPort.paySeat(seatNumber, token)).willReturn(true);

            // when
            Ticket result = ticketService.purchase(token, seatNumber);

            // then
            assertThat(result.getStatus()).isEqualTo(TicketStatus.SYNCED);
            assertThat(result.getSeatNumber()).isEqualTo(seatNumber);
            assertThat(result.getQueueToken()).isEqualTo(token);
        }

        @Test
        @DisplayName("정상 구매 시 5단계가 올바른 순서로 실행된다")
        void shouldExecuteFivePhasesInOrder() {
            // given
            String token = "active-token";
            int seatNumber = 7;
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token, HOLD_TTL_SECONDS)).willReturn(true);
            // save 호출 시점의 status를 캡처 (Ticket은 mutable이므로 나중에 sync()로 변경됨)
            ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
            java.util.List<TicketStatus> capturedStatuses = new java.util.ArrayList<>();
            given(ticketPersistencePort.save(any(Ticket.class))).willAnswer(inv -> {
                Ticket t = inv.getArgument(0);
                capturedStatuses.add(t.getStatus());
                return t;
            });
            given(seatHoldPort.paySeat(seatNumber, token)).willReturn(true);

            // when
            ticketService.purchase(token, seatNumber);

            // then - 순서 검증: isActive → holdSeat → save(PAID) → paySeat → save(SYNCED)
            InOrder inOrder = inOrder(activeUserPort, seatHoldPort, ticketPersistencePort);
            inOrder.verify(activeUserPort).isActive(token);
            inOrder.verify(seatHoldPort).holdSeat(seatNumber, token, HOLD_TTL_SECONDS);
            inOrder.verify(ticketPersistencePort).save(any(Ticket.class)); // 1차: PAID
            inOrder.verify(seatHoldPort).paySeat(seatNumber, token);
            inOrder.verify(ticketPersistencePort).save(any(Ticket.class)); // 2차: SYNCED

            // save 호출 시점의 상태 검증 (mutable 객체이므로 캡처된 값으로 확인)
            assertThat(capturedStatuses).containsExactly(TicketStatus.PAID, TicketStatus.SYNCED);
        }
    }

    @Nested
    @DisplayName("purchase() - 1단계: 활성 사용자 검증")
    class Phase1ActiveUserValidation {

        @Test
        @DisplayName("비활성 사용자가 구매 시도하면 NOT_ACTIVE_USER 예외가 발생한다")
        void shouldRejectNonActiveUser() {
            // given
            given(activeUserPort.isActive("expired-token")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> ticketService.purchase("expired-token", 7))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_ACTIVE_USER);

            // 후속 단계가 실행되지 않았음을 검증
            then(seatHoldPort).shouldHaveNoInteractions();
            then(ticketPersistencePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("purchase() - 2단계: 좌석 선점 (Redis SET NX EX)")
    class Phase2SeatHold {

        @Test
        @DisplayName("이미 선점된 좌석에 대해 SEAT_ALREADY_HELD 예외가 발생한다")
        void shouldRejectAlreadyHeldSeat() {
            // given
            String token = "active-token";
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(7, token, HOLD_TTL_SECONDS)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> ticketService.purchase(token, 7))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SEAT_ALREADY_HELD);

            // DB에 저장되지 않았음을 검증 (이중 판매 방지)
            then(ticketPersistencePort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("좌석 선점 시 TTL이 설정값대로 전달된다")
        void shouldPassConfiguredTtl() {
            // given
            String token = "active-token";
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(7, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketPersistencePort.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(seatHoldPort.paySeat(7, token)).willReturn(true);

            // when
            ticketService.purchase(token, 7);

            // then
            then(seatHoldPort).should().holdSeat(7, token, 300L);
        }
    }

    @Nested
    @DisplayName("purchase() - 3단계 실패: DB 저장 실패 시 Redis 롤백 (Case 1)")
    class Phase3DbFailureRollback {

        @Test
        @DisplayName("DB 저장 실패 시 Redis 좌석을 즉시 해제(롤백)한다")
        void shouldReleaseRedisHoldOnDbFailure() {
            // given
            String token = "active-token";
            int seatNumber = 7;
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketPersistencePort.save(any(Ticket.class)))
                    .willThrow(new RuntimeException("DB connection failed"));

            // when & then
            assertThatThrownBy(() -> ticketService.purchase(token, seatNumber))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INTERNAL_ERROR);

            // 핵심 검증: Redis 좌석이 해제되었는지
            then(seatHoldPort).should().releaseSeat(seatNumber);
        }

        @Test
        @DisplayName("DB 저장 실패 시 Redis paid 전환은 실행되지 않는다")
        void shouldNotAttemptPayOnDbFailure() {
            // given
            String token = "active-token";
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(7, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketPersistencePort.save(any(Ticket.class)))
                    .willThrow(new RuntimeException("DB error"));

            // when
            assertThatThrownBy(() -> ticketService.purchase(token, 7))
                    .isInstanceOf(BusinessException.class);

            // then - paySeat이 호출되지 않았음을 검증
            then(seatHoldPort).should(never()).paySeat(anyInt(), anyString());
        }
    }

    @Nested
    @DisplayName("purchase() - 4단계 실패: Redis paid 전환 실패 (Case 2 - 동기화 워커가 복구)")
    class Phase4PaySeatFailure {

        @Test
        @DisplayName("Redis paid 전환 실패 시 PAID 상태 티켓을 반환한다 (워커가 나중에 복구)")
        void shouldReturnPaidTicketWhenPaySeatFails() {
            // given
            String token = "active-token";
            int seatNumber = 7;
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketPersistencePort.save(any(Ticket.class))).willAnswer(inv -> inv.getArgument(0));
            given(seatHoldPort.paySeat(seatNumber, token)).willReturn(false); // paid 전환 실패

            // when
            Ticket result = ticketService.purchase(token, seatNumber);

            // then
            // 상태: DB에 PAID + Redis에 held (TTL 째깍째깍)
            // 동기화 워커가 나중에 setPaidSeat으로 Redis를 복구할 예정
            assertThat(result.getStatus()).isEqualTo(TicketStatus.PAID);
            assertThat(result.getSeatNumber()).isEqualTo(seatNumber);
        }

        @Test
        @DisplayName("Redis paid 전환 실패 시 DB save(SYNCED)가 실행되지 않는다")
        void shouldNotUpdateToSyncedWhenPaySeatFails() {
            // given
            String token = "active-token";
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(7, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketPersistencePort.save(any(Ticket.class))).willAnswer(inv -> inv.getArgument(0));
            given(seatHoldPort.paySeat(7, token)).willReturn(false);

            // when
            ticketService.purchase(token, 7);

            // then - save는 PAID 저장 1회만 호출 (SYNCED 저장 없음)
            then(ticketPersistencePort).should(times(1)).save(any(Ticket.class));
            then(ticketPersistencePort).should()
                    .save(argThat(t -> t.getStatus() == TicketStatus.PAID));
        }
    }

    @Nested
    @DisplayName("purchase() - 이중 판매 방지 검증")
    class DoubleSalePrevention {

        @Test
        @DisplayName("두 사용자가 같은 좌석을 시도할 때 첫 번째만 성공한다 (holdSeat NX)")
        void shouldPreventDoubleSaleViaNx() {
            // given
            String token1 = "user-1";
            String token2 = "user-2";
            int seatNumber = 7;

            given(activeUserPort.isActive(anyString())).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token1, HOLD_TTL_SECONDS)).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token2, HOLD_TTL_SECONDS)).willReturn(false);
            given(ticketPersistencePort.save(any(Ticket.class))).willAnswer(inv -> inv.getArgument(0));
            given(seatHoldPort.paySeat(seatNumber, token1)).willReturn(true);

            // when
            Ticket success = ticketService.purchase(token1, seatNumber);

            assertThatThrownBy(() -> ticketService.purchase(token2, seatNumber))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SEAT_ALREADY_HELD);

            // then
            assertThat(success.getStatus()).isEqualTo(TicketStatus.SYNCED);
        }
    }

}
