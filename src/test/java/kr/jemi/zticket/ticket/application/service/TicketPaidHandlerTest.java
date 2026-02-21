package kr.jemi.zticket.ticket.application.service;

import kr.jemi.zticket.ticket.application.port.out.ActiveUserCheckPort;
import kr.jemi.zticket.ticket.application.port.out.SeatHoldPort;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class TicketPaidHandlerTest {

    @Mock
    private SeatHoldPort seatHoldPort;

    @Mock
    private TicketPort ticketPort;

    @Mock
    private ActiveUserCheckPort activeUserCheckPort;

    @InjectMocks
    private TicketPaidHandler handler;

    @Test
    @DisplayName("정상 처리: paySeat → sync+update → deactivate 순서로 실행된다")
    void shouldExecuteStepsInOrder() {
        // given
        Ticket ticket = new Ticket(1L, 7, TicketStatus.PAID, "token-1", LocalDateTime.now(), null);
        given(ticketPort.findById(1)).willReturn(Optional.of(ticket));

        // when
        handler.handle(1);

        // then
        InOrder inOrder = inOrder(seatHoldPort, ticketPort, activeUserCheckPort);
        inOrder.verify(seatHoldPort).paySeat(7, "token-1");
        inOrder.verify(ticketPort).update(any(Ticket.class));
        inOrder.verify(activeUserCheckPort).deactivate("token-1");
    }

    @Test
    @DisplayName("존재하지 않는 ticketId이면 IllegalStateException이 발생한다")
    void shouldThrowWhenTicketNotFound() {
        // given
        given(ticketPort.findById(0)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("티켓 없음");

        then(seatHoldPort).shouldHaveNoInteractions();
        then(activeUserCheckPort).shouldHaveNoInteractions();
    }
}
