package kr.jemi.zticket.application.port.out;

import kr.jemi.zticket.domain.seat.SeatStatuses;

import java.util.List;

public interface SeatHoldPort {

    boolean holdSeat(int seatNumber, String uuid, long ttlSeconds);

    boolean paySeat(int seatNumber, String uuid);

    void releaseSeat(int seatNumber);

    void setPaidSeat(int seatNumber, String uuid);

    SeatStatuses getStatuses(List<Integer> seatNumbers);
}
