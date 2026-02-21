package kr.jemi.zticket.seat.application.port.out;

import kr.jemi.zticket.seat.domain.Seats;

import java.util.List;

public interface SeatPort {

    /**
     * 좌석을 선점한다. 이미 같은 유저가 선점한 경우 TTL을 갱신하고 성공으로 처리한다.
     */
    boolean holdSeat(int seatNumber, String token, long ttlSeconds);

    void paySeat(int seatNumber, String token);

    void releaseSeat(int seatNumber, String token);

    Seats getStatuses(List<Integer> seatNumbers);
}
