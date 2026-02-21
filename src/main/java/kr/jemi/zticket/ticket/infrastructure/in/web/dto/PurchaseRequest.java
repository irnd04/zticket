package kr.jemi.zticket.ticket.infrastructure.in.web.dto;

import jakarta.validation.constraints.Min;

public record PurchaseRequest(@Min(1) int seatNumber) {
}
