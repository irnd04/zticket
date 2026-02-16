package kr.jemi.zticket.seat.application;

import kr.jemi.zticket.seat.application.port.in.GetSeatsUseCase;
import kr.jemi.zticket.seat.application.port.out.SeatHoldPort;
import kr.jemi.zticket.seat.domain.SeatStatus;
import kr.jemi.zticket.seat.domain.SeatStatuses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class SeatService implements GetSeatsUseCase {

    private final SeatHoldPort seatHoldPort;
    private final int totalSeats;

    public SeatService(SeatHoldPort seatHoldPort,
                       @Value("${zticket.seat.total-count}") int totalSeats) {
        this.seatHoldPort = seatHoldPort;
        this.totalSeats = totalSeats;
    }

    @Override
    public SeatStatuses getAllSeatStatuses() {
        List<Integer> allSeats = IntStream.rangeClosed(1, totalSeats)
                .boxed()
                .toList();
        return seatHoldPort.getStatuses(allSeats);
    }

    @Cacheable("availableCount")
    public long getAvailableCount() {
        SeatStatuses statuses = getAllSeatStatuses();
        return statuses.seatNumbers().stream()
                .filter(seat -> statuses.of(seat) == SeatStatus.AVAILABLE)
                .count();
    }
}
