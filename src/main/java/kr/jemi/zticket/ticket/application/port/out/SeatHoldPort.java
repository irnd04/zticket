package kr.jemi.zticket.ticket.application.port.out;

public interface SeatHoldPort {

    boolean holdSeat(int seatNumber, String token, long ttlSeconds);

    void paySeat(int seatNumber, String token);

    void releaseSeat(int seatNumber, String token);
}
