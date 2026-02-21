package kr.jemi.zticket.seat.api;

public interface SeatFacade {

    boolean holdSeat(int seatNumber, String token, long ttlSeconds);

    void paySeat(int seatNumber, String token);

    void releaseSeat(int seatNumber, String token);

    int getAvailableCount();
}
