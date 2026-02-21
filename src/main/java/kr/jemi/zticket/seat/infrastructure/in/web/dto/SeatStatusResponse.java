package kr.jemi.zticket.seat.infrastructure.in.web.dto;

public record SeatStatusResponse(int seatNumber, String status) {

    public static SeatStatusResponse from(int seatNumber, boolean available) {
        String displayStatus = available ? "available" : "unavailable";
        return new SeatStatusResponse(seatNumber, displayStatus);
    }
}
