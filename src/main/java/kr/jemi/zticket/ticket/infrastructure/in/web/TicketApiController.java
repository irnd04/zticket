package kr.jemi.zticket.ticket.infrastructure.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.jemi.zticket.ticket.infrastructure.in.web.dto.PurchaseRequest;
import kr.jemi.zticket.ticket.infrastructure.in.web.dto.PurchaseResponse;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.application.port.in.PurchaseTicketUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ticket", description = "티켓 구매")
@RestController
public class TicketApiController {

    private final PurchaseTicketUseCase purchaseTicketUseCase;

    public TicketApiController(PurchaseTicketUseCase purchaseTicketUseCase) {
        this.purchaseTicketUseCase = purchaseTicketUseCase;
    }

    @Operation(summary = "티켓 구매", description = "좌석을 선점하고 티켓을 발급합니다. 입장 권한(ACTIVE)이 필요합니다.")
    @PostMapping("/api/tickets")
    public ResponseEntity<PurchaseResponse> purchase(
            @Parameter(description = "대기열 토큰") @RequestHeader("X-Queue-Token") String queueToken,
            @Valid @RequestBody PurchaseRequest request) {
        Ticket ticket = purchaseTicketUseCase.purchase(queueToken, request.seatNumber());
        return ResponseEntity.ok(PurchaseResponse.from(ticket));
    }
}
