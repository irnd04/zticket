package kr.jemi.zticket.adapter.in.web.dto;

import kr.jemi.zticket.domain.ticket.Ticket;

public record PurchaseResponse(String ticketUuid, int seatNumber, String status) {

    public static PurchaseResponse from(Ticket ticket) {
        return new PurchaseResponse(
                ticket.getUuid(),
                ticket.getSeatNumber(),
                ticket.getStatus().name()
        );
    }
}
