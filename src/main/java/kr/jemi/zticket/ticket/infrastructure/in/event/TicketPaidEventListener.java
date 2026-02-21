package kr.jemi.zticket.ticket.infrastructure.in.event;

import kr.jemi.zticket.ticket.application.port.in.HandleTicketPaidUseCase;
import kr.jemi.zticket.ticket.domain.TicketPaidEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class TicketPaidEventListener {

    private final HandleTicketPaidUseCase handleTicketPaidUseCase;

    public TicketPaidEventListener(HandleTicketPaidUseCase handleTicketPaidUseCase) {
        this.handleTicketPaidUseCase = handleTicketPaidUseCase;
    }

    @Async
    @EventListener
    public void handle(TicketPaidEvent event) {
        handleTicketPaidUseCase.handle(event.ticketId());
    }
}
