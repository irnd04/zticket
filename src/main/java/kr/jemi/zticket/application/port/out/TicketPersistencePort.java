package kr.jemi.zticket.application.port.out;

import kr.jemi.zticket.domain.ticket.Ticket;
import kr.jemi.zticket.domain.ticket.TicketStatus;

import java.util.List;
import java.util.Optional;

public interface TicketPersistencePort {

    Ticket save(Ticket ticket);

    Optional<Ticket> findByUuid(String uuid);

    List<Ticket> findByStatus(TicketStatus status);
}
