package kr.jemi.zticket.ticket.application;

import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.seat.application.port.out.SeatHoldPort;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketPaidEvent;
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
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private SeatHoldPort seatHoldPort;

    @Mock
    private TicketPort ticketPort;

    @Mock
    private ActiveUserPort activeUserPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TicketService ticketService;

    private static final long HOLD_TTL_SECONDS = 300L;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
                seatHoldPort, ticketPort, activeUserPort, eventPublisher, HOLD_TTL_SECONDS);
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
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketPort.save(any(Ticket.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            Ticket result = ticketService.purchase(token, seatNumber);

            // then
            assertThat(result.getStatus()).isEqualTo(TicketStatus.PAID);
            assertThat(result.getSeatNumber()).isEqualTo(seatNumber);
            assertThat(result.getQueueToken()).isEqualTo(token);
        }

        @Test
        @DisplayName("정상 구매 시 올바른 순서로 실행되고 이벤트가 발행된다")
        void shouldExecuteInOrderAndPublishEvent() {
            // given
            String token = "active-token";
            int seatNumber = 7;
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketPort.save(any(Ticket.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            ticketService.purchase(token, seatNumber);

            // then - 순서 검증: isActive → holdSeat → save(PAID) → 이벤트 발행
            InOrder inOrder = inOrder(activeUserPort, seatHoldPort, ticketPort, eventPublisher);
            inOrder.verify(activeUserPort).isActive(token);
            inOrder.verify(seatHoldPort).holdSeat(seatNumber, token, HOLD_TTL_SECONDS);
            inOrder.verify(ticketPort).save(any(Ticket.class));
            inOrder.verify(eventPublisher).publishEvent(any(TicketPaidEvent.class));
        }

        @Test
        @DisplayName("이벤트에 ticketUuid가 전달된다")
        void shouldPublishEventWithTicketUuid() {
            // given
            String token = "active-token";
            int seatNumber = 7;
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketPort.save(any(Ticket.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            Ticket result = ticketService.purchase(token, seatNumber);

            // then
            ArgumentCaptor<TicketPaidEvent> captor = ArgumentCaptor.forClass(TicketPaidEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().ticketUuid()).isEqualTo(result.getUuid());
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

            then(seatHoldPort).shouldHaveNoInteractions();
            then(ticketPort).shouldHaveNoInteractions();
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

            then(ticketPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("좌석 선점 시 TTL이 설정값대로 전달된다")
        void shouldPassConfiguredTtl() {
            // given
            String token = "active-token";
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(7, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketPort.save(any())).willAnswer(inv -> inv.getArgument(0));

            // when
            ticketService.purchase(token, 7);

            // then
            then(seatHoldPort).should().holdSeat(7, token, 300L);
        }
    }

    @Nested
    @DisplayName("purchase() - 3단계 실패: DB 저장 실패 시 Redis 롤백")
    class Phase3DbFailureRollback {

        @Test
        @DisplayName("DB 저장 실패 시 Redis 좌석을 즉시 해제(롤백)한다")
        void shouldReleaseRedisHoldOnDbFailure() {
            // given
            String token = "active-token";
            int seatNumber = 7;
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(seatNumber, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketPort.save(any(Ticket.class)))
                    .willThrow(new RuntimeException("DB connection failed"));

            // when & then
            assertThatThrownBy(() -> ticketService.purchase(token, seatNumber))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INTERNAL_ERROR);

            then(seatHoldPort).should().releaseSeat(seatNumber);
        }

        @Test
        @DisplayName("DB 저장 실패 시 이벤트가 발행되지 않는다")
        void shouldNotPublishEventOnDbFailure() {
            // given
            String token = "active-token";
            given(activeUserPort.isActive(token)).willReturn(true);
            given(seatHoldPort.holdSeat(7, token, HOLD_TTL_SECONDS)).willReturn(true);
            given(ticketPort.save(any(Ticket.class)))
                    .willThrow(new RuntimeException("DB error"));

            // when
            assertThatThrownBy(() -> ticketService.purchase(token, 7))
                    .isInstanceOf(BusinessException.class);

            // then
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

}
