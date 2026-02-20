package kr.jemi.zticket.ticket.application;

import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.seat.application.port.out.SeatPort;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketPaidEvent;
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
class TicketPaidEventListenerTest {

    @Mock
    private SeatPort seatPort;

    @Mock
    private TicketPort ticketPort;

    @Mock
    private ActiveUserPort activeUserPort;

    @InjectMocks
    private TicketPaidEventListener listener;

    @Test
    @DisplayName("정상 처리: paySeat → sync+update → deactivate 순서로 실행된다")
    void shouldExecuteStepsInOrder() {
        // given
        Ticket ticket = new Ticket(1L, 7, TicketStatus.PAID, "token-1", LocalDateTime.now(), null);
        given(ticketPort.findById(1)).willReturn(Optional.of(ticket));

        // when
        listener.handle(new TicketPaidEvent(1));

        // then
        InOrder inOrder = inOrder(seatPort, ticketPort, activeUserPort);
        inOrder.verify(seatPort).paySeat(7, "token-1");
        inOrder.verify(ticketPort).update(any(Ticket.class));
        inOrder.verify(activeUserPort).deactivate("token-1");
    }

    @Test
    @DisplayName("존재하지 않는 티켓 UUID이면 IllegalStateException이 발생한다")
    void shouldThrowWhenTicketNotFound() {
        // given
        given(ticketPort.findById(0)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> listener.handle(new TicketPaidEvent(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("티켓 없음");

        then(seatPort).shouldHaveNoInteractions();
        then(activeUserPort).shouldHaveNoInteractions();
    }
}
