package kr.jemi.zticket.seat.domain;

import java.util.List;
import java.util.Map;

public class SeatStatuses {

    private final Map<Integer, SeatStatus> statuses;

    public SeatStatuses(Map<Integer, SeatStatus> statuses) {
        this.statuses = Map.copyOf(statuses);
    }

    public SeatStatus of(int seatNumber) {
        SeatStatus status = statuses.get(seatNumber);
        if (status == null) {
            throw new IllegalArgumentException("존재하지 않는 좌석 번호: " + seatNumber);
        }
        return status;
    }

    public List<Integer> seatNumbers() {
        return statuses.keySet().stream().sorted().toList();
    }

    public int size() {
        return statuses.size();
    }
}
