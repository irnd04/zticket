package kr.jemi.zticket.application.port.in;

import kr.jemi.zticket.domain.ticket.Ticket;

public interface PurchaseTicketUseCase {

    Ticket purchase(String queueToken, int seatNumber);
}
