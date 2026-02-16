package kr.jemi.zticket.seat.adapter.in.web.dto;

import kr.jemi.zticket.seat.domain.SeatStatus;

public record SeatStatusResponse(int seatNumber, String status) {

    public static SeatStatusResponse from(int seatNumber, SeatStatus seatStatus) {
        return new SeatStatusResponse(seatNumber, seatStatus.name().toLowerCase());
    }
}
