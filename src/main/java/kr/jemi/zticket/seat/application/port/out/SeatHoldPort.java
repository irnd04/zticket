package kr.jemi.zticket.seat.application.port.out;

import kr.jemi.zticket.seat.domain.SeatStatuses;

import java.util.List;

public interface SeatHoldPort {

    boolean holdSeat(int seatNumber, String uuid, long ttlSeconds);

    void paySeat(int seatNumber, String uuid);

    void releaseSeat(int seatNumber);

    SeatStatuses getStatuses(List<Integer> seatNumbers);
}
