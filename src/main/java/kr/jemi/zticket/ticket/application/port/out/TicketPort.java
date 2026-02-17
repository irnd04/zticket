package kr.jemi.zticket.ticket.application.port.out;

import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;

import java.util.List;
import java.util.Optional;

public interface TicketPort {

    Ticket save(Ticket ticket);

    Optional<Ticket> findByUuid(String uuid);

    List<Ticket> findByStatus(TicketStatus status);
}
