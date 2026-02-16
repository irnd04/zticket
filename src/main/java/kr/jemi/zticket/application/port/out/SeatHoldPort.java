package kr.jemi.zticket.application.port.out;

import java.util.List;
import java.util.Map;

public interface SeatHoldPort {

    boolean holdSeat(int seatNumber, String uuid, long ttlSeconds);

    boolean paySeat(int seatNumber, String uuid);

    void releaseSeat(int seatNumber);

    void setPaidSeat(int seatNumber, String uuid);

    Map<Integer, String> getStatuses(List<Integer> seatNumbers);
}
