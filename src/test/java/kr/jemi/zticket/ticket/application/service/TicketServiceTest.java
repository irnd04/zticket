package kr.jemi.zticket.ticket.application.service;

import io.hypersistence.tsid.TSID;
import kr.jemi.zticket.ticket.application.port.out.ActiveUserCheckPort;
import kr.jemi.zticket.ticket.application.port.out.SeatHoldPort;
import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private ActiveUserCheckPort activeUserCheckPort;

    @Mock
    private TicketWriter ticketWriter;

    private final TSID.Factory tsidFactory = TSID.Factory.newInstance256(0);

    private TicketService ticketService;

    private static final long HOLD_TTL_SECONDS = 300L;
    private static final int TOTAL_SEAT_COUNT = 1000;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
                seatHoldPort, activeUserCheckPort, ticketWriter, tsidFactory,
                HOLD_TTL_SECONDS, TOTAL_SEAT_COUNT);
    }

    @Nested
    @DisplayName("purchase() - 구매 플로우")
    class Purchase {

        @Test
        @DisplayName("정상 구매: DB PAID 저장 후 PAID 티켓을 반환한다")
        void shouldReturnPaidTicketOnSuccess() {
            // given
            String token = "active-token";
            int seatNumber = 7;
            given(activeUserCheckPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketWriter.insertAndPublish(any(Ticket.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            Ticket result = ticketService.purchase(token, seatNumber);

            // then
            assertThat(result.getStatus()).isEqualTo(TicketStatus.PAID);
            assertThat(result.getSeatNumber()).isEqualTo(seatNumber);
            assertThat(result.getQueueToken()).isEqualTo(token);
        }

        @Test
        @DisplayName("정상 구매 시 올바른 순서로 실행된다: isActive → holdSeat → insertAndPublish")
        void shouldExecuteInOrder() {
            // given
            String token = "active-token";
            int seatNumber = 7;
            given(activeUserCheckPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketWriter.insertAndPublish(any(Ticket.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            ticketService.purchase(token, seatNumber);

            // then
            InOrder inOrder = inOrder(activeUserCheckPort, seatHoldPort, ticketWriter);
            inOrder.verify(activeUserCheckPort).isActive(token);
            inOrder.verify(seatHoldPort).holdSeat(seatNumber, token, HOLD_TTL_SECONDS);
            inOrder.verify(ticketWriter).insertAndPublish(any(Ticket.class));
        }
    }

    @Nested
    @DisplayName("purchase() - 1단계: 좌석 번호 범위 검증")
    class Phase1SeatNumberValidation {

        @Test
        @DisplayName("좌석 번호가 0이면 INVALID_SEAT_NUMBERS 예외가 발생한다")
        void shouldRejectZeroSeatNumber() {
            assertThatThrownBy(() -> ticketService.purchase("token", 0))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_SEAT_NUMBERS);

            then(activeUserCheckPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("좌석 번호가 총 좌석 수를 초과하면 INVALID_SEAT_NUMBERS 예외가 발생한다")
        void shouldRejectSeatNumberExceedingTotal() {
            assertThatThrownBy(() -> ticketService.purchase("token", TOTAL_SEAT_COUNT + 1))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_SEAT_NUMBERS);

            then(activeUserCheckPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("purchase() - 2단계: 활성 사용자 검증")
    class Phase2ActiveUserValidation {

        @Test
        @DisplayName("비활성 사용자가 구매 시도하면 NOT_ACTIVE_USER 예외가 발생한다")
        void shouldRejectNonActiveUser() {
            // given
            given(activeUserCheckPort.isActive("expired-token")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> ticketService.purchase("expired-token", 7))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_ACTIVE_USER);

            then(seatHoldPort).shouldHaveNoInteractions();
            then(ticketWriter).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("purchase() - 3단계: 좌석 선점 (Redis SET NX EX)")
    class Phase3SeatHold {

        @Test
        @DisplayName("이미 선점된 좌석에 대해 SEAT_ALREADY_HELD 예외가 발생한다")
        void shouldRejectAlreadyHeldSeat() {
            // given
            String token = "active-token";
            given(activeUserCheckPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(7, token, HOLD_TTL_SECONDS)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> ticketService.purchase(token, 7))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SEAT_ALREADY_HELD);

            then(ticketWriter).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("좌석 선점 시 TTL이 설정값대로 전달된다")
        void shouldPassConfiguredTtl() {
            // given
            String token = "active-token";
            given(activeUserCheckPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(7, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketWriter.insertAndPublish(any()))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            ticketService.purchase(token, 7);

            // then
            then(seatHoldPort).should().holdSeat(7, token, 300L);
        }
    }

    @Nested
    @DisplayName("purchase() - 4단계 실패: DB 저장 실패 시 Redis 롤백")
    class Phase4DbFailureRollback {

        @Test
        @DisplayName("DB 저장 실패 시 Redis 좌석을 즉시 해제(롤백)한다")
        void shouldReleaseRedisHoldOnDbFailure() {
            // given
            String token = "active-token";
            int seatNumber = 7;
            given(activeUserCheckPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketWriter.insertAndPublish(any(Ticket.class)))
                    .willThrow(new RuntimeException("DB connection failed"));

            // when & then
            assertThatThrownBy(() -> ticketService.purchase(token, seatNumber))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INTERNAL_ERROR);

            then(seatHoldPort).should().releaseSeat(seatNumber, token);
        }
    }

}
