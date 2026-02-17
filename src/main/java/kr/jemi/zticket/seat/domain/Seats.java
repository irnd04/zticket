package kr.jemi.zticket.seat.domain;

import java.util.List;
import java.util.Map;

public class Seats {

    private final Map<Integer, Seat> statuses;

    public Seats(Map<Integer, Seat> statuses) {
        this.statuses = Map.copyOf(statuses);
    }

    public Seat of(int seatNumber) {
        Seat info = statuses.get(seatNumber);
        if (info == null) {
            throw new IllegalArgumentException("존재하지 않는 좌석 번호: " + seatNumber);
        }
        return info;
    }

    public List<Integer> seatNumbers() {
        return statuses.keySet().stream().sorted().toList();
    }

    public int size() {
        return statuses.size();
    }
}
