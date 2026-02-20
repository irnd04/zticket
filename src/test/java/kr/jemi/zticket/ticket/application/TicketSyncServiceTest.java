package kr.jemi.zticket.ticket.application;

import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketPaidEvent;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class TicketSyncServiceTest {

    @Mock
    private TicketPort ticketPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TicketSyncService ticketSyncService;

    @Test
    @DisplayName("PAID 티켓마다 TicketPaidEvent를 발행한다")
    void shouldPublishEventForEachPaidTicket() {
        // given
        Ticket ticket1 = new Ticket(1, 7, TicketStatus.PAID, "token-1", LocalDateTime.now(), null);
        Ticket ticket2 = new Ticket(2, 42, TicketStatus.PAID, "token-2", LocalDateTime.now(), null);
        given(ticketPort.findByStatus(TicketStatus.PAID))
                .willReturn(List.of(ticket1, ticket2));

        // when
        ticketSyncService.syncPaidTickets();

        // then
        ArgumentCaptor<TicketPaidEvent> captor = ArgumentCaptor.forClass(TicketPaidEvent.class);
        then(eventPublisher).should(times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(TicketPaidEvent::ticketId)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("PAID 티켓이 없으면 이벤트를 발행하지 않는다")
    void shouldNotPublishWhenNoPaidTickets() {
        // given
        given(ticketPort.findByStatus(TicketStatus.PAID))
                .willReturn(List.of());

        // when
        ticketSyncService.syncPaidTickets();

        // then
        then(eventPublisher).shouldHaveNoInteractions();
    }
}
