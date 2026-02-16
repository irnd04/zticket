package kr.jemi.zticket.ticket.application.port.in;

import kr.jemi.zticket.ticket.domain.Ticket;

public interface PurchaseTicketUseCase {

    Ticket purchase(String queueToken, int seatNumber);
}
