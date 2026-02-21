package kr.jemi.zticket.ticket.infrastructure.in.event;

import kr.jemi.zticket.ticket.application.port.in.HandleTicketPaidUseCase;
import kr.jemi.zticket.ticket.domain.TicketPaidEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TicketPaidEventListener {

    private final HandleTicketPaidUseCase handleTicketPaidUseCase;

    public TicketPaidEventListener(HandleTicketPaidUseCase handleTicketPaidUseCase) {
        this.handleTicketPaidUseCase = handleTicketPaidUseCase;
    }

    @Async
    @TransactionalEventListener
    public void handle(TicketPaidEvent event) {
        handleTicketPaidUseCase.handle(event.ticketId());
    }
}
