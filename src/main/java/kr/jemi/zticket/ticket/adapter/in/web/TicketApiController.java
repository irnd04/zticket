package kr.jemi.zticket.ticket.adapter.in.web;

import jakarta.validation.Valid;
import kr.jemi.zticket.ticket.adapter.in.web.dto.PurchaseRequest;
import kr.jemi.zticket.ticket.adapter.in.web.dto.PurchaseResponse;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.application.port.in.PurchaseTicketUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ticket")
public class TicketApiController {

    private final PurchaseTicketUseCase purchaseTicketUseCase;

    public TicketApiController(PurchaseTicketUseCase purchaseTicketUseCase) {
        this.purchaseTicketUseCase = purchaseTicketUseCase;
    }

    @PostMapping("/purchase")
    public ResponseEntity<PurchaseResponse> purchase(
            @RequestHeader("X-Queue-Token") String queueToken,
            @Valid @RequestBody PurchaseRequest request) {
        Ticket ticket = purchaseTicketUseCase.purchase(queueToken, request.seatNumber());
        return ResponseEntity.ok(PurchaseResponse.from(ticket));
    }
}
