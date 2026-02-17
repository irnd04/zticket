package kr.jemi.zticket.ticket.application;

import kr.jemi.zticket.ticket.application.port.in.SyncTicketUseCase;
import kr.jemi.zticket.ticket.application.port.out.TicketPersistencePort;
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

    private final TicketPersistencePort ticketPersistencePort;
    private final ApplicationEventPublisher eventPublisher;

    public TicketSyncService(TicketPersistencePort ticketPersistencePort,
                             ApplicationEventPublisher eventPublisher) {
        this.ticketPersistencePort = ticketPersistencePort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void syncPaidTickets() {
        List<Ticket> paidTickets = ticketPersistencePort.findByStatus(TicketStatus.PAID);

        for (Ticket ticket : paidTickets) {
            eventPublisher.publishEvent(new TicketPaidEvent(ticket.getUuid()));
        }

        if (!paidTickets.isEmpty()) {
            log.info("동기화 이벤트 발행 완료. PAID 티켓 {}건", paidTickets.size());
        }
    }
}
