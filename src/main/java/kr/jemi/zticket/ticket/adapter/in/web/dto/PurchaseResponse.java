package kr.jemi.zticket.ticket.adapter.in.web.dto;

import io.hypersistence.tsid.TSID;
import kr.jemi.zticket.ticket.domain.Ticket;

public record PurchaseResponse(long ticketId, int seatNumber, String status) {

    public static PurchaseResponse from(Ticket ticket) {
        return new PurchaseResponse(
            ticket.getId(),
                ticket.getSeatNumber(),
                ticket.getStatus().name()
        );
    }
}
