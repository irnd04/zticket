package kr.jemi.zticket.seat.application;

import kr.jemi.zticket.seat.application.port.in.GetSeatsUseCase;
import kr.jemi.zticket.seat.application.port.out.SeatPort;
import kr.jemi.zticket.seat.domain.Seats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class SeatService implements GetSeatsUseCase {

    private final SeatPort seatPort;
    private final int totalSeats;

    public SeatService(SeatPort seatPort,
                       @Value("${zticket.seat.total-count}") int totalSeats) {
        this.seatPort = seatPort;
        this.totalSeats = totalSeats;
    }

    @Override
    public Seats getSeats(String token) {
        List<Integer> allSeats = IntStream.rangeClosed(1, totalSeats)
                .boxed()
                .toList();
        return seatPort.getStatuses(allSeats);
    }

    @Cacheable("availableCount")
    public long getAvailableCount() {
        return getAvailableCountNoCache();
    }

    public long getAvailableCountNoCache() {
        Seats statuses = getSeats(null);
        return statuses.seatNumbers().stream()
            .filter(seat -> statuses.of(seat).isAvailableFor(null))
            .count();
    }
}
