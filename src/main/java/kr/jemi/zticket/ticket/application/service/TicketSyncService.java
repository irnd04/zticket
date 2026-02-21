package kr.jemi.zticket.ticket.application.service;

import kr.jemi.zticket.ticket.application.port.in.SyncTicketUseCase;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketPaidEvent;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TicketSyncService implements SyncTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(TicketSyncService.class);

    private final TicketPort ticketPort;
    private final ApplicationEventPublisher eventPublisher;

    public TicketSyncService(TicketPort ticketPort,
                             ApplicationEventPublisher eventPublisher) {
        this.ticketPort = ticketPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void syncPaidTickets() {
        List<Ticket> paidTickets = ticketPort.findByStatus(TicketStatus.PAID);

        for (Ticket ticket : paidTickets) {
            eventPublisher.publishEvent(new TicketPaidEvent(ticket.getId()));
        }

        if (!paidTickets.isEmpty()) {
            log.info("동기화 이벤트 발행 완료. PAID 티켓 {}건", paidTickets.size());
        }
    }
}
