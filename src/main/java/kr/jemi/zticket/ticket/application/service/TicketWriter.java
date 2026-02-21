package kr.jemi.zticket.ticket.application.service;

import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.ticket.domain.Ticket;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketWriter {

    private final TicketPort ticketPort;
    private final ApplicationEventPublisher eventPublisher;

    public TicketWriter(TicketPort ticketPort, ApplicationEventPublisher eventPublisher) {
        this.ticketPort = ticketPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Ticket insertAndPublish(Ticket ticket) {
        List<Object> events = ticket.pullEvents();
        ticket = ticketPort.insert(ticket);
        events.forEach(eventPublisher::publishEvent);
        return ticket;
    }
}
