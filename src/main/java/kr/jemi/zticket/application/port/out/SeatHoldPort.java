package kr.jemi.zticket.application.port.out;

import kr.jemi.zticket.domain.seat.SeatStatus;

import java.util.List;
import java.util.Map;

public interface SeatHoldPort {

    boolean holdSeat(int seatNumber, String uuid, long ttlSeconds);

    boolean paySeat(int seatNumber, String uuid);

    void releaseSeat(int seatNumber);

    void setPaidSeat(int seatNumber, String uuid);

    Map<Integer, SeatStatus> getStatuses(List<Integer> seatNumbers);
}
