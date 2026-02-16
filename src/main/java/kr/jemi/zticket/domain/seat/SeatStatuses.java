package kr.jemi.zticket.domain.seat;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class SeatStatuses {

    private final Map<Integer, SeatStatus> statuses;

    public SeatStatuses(Map<Integer, SeatStatus> statuses) {
        this.statuses = Map.copyOf(statuses);
    }

    public SeatStatus of(int seatNumber) {
        return statuses.getOrDefault(seatNumber, SeatStatus.AVAILABLE);
    }

    public List<Integer> seatNumbers() {
        return statuses.keySet().stream().sorted().toList();
    }

    public int size() {
        return statuses.size();
    }
}
