package kr.jemi.zticket.seat.application.service;

import kr.jemi.zticket.seat.api.SeatFacade;
import kr.jemi.zticket.seat.application.port.in.GetSeatsUseCase;
import kr.jemi.zticket.seat.application.port.out.SeatPort;
import kr.jemi.zticket.seat.domain.Seats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class SeatService implements GetSeatsUseCase, SeatFacade {

    private final SeatPort seatPort;
    private final int totalSeats;

    public SeatService(SeatPort seatPort,
                       @Value("${zticket.seat.total-count}") int totalSeats) {
        this.seatPort = seatPort;
        this.totalSeats = totalSeats;
    }

    @Override
    public Seats getSeats() {
        List<Integer> allSeats = IntStream.rangeClosed(1, totalSeats)
                .boxed()
                .toList();
        return seatPort.getStatuses(allSeats);
    }

    @Override
    @Cacheable(value = "availableCount", sync = true)
    public int getAvailableCount() {
        Seats statuses = getSeats();
        return (int) statuses.seatNumbers().stream()
            .filter(seat -> statuses.of(seat).isAvailableFor(null))
            .count();
    }

    @Override
    public boolean holdSeat(int seatNumber, String token, long ttlSeconds) {
        return seatPort.holdSeat(seatNumber, token, ttlSeconds);
    }

    @Override
    public void paySeat(int seatNumber, String token) {
        seatPort.paySeat(seatNumber, token);
    }

    @Override
    public void releaseSeat(int seatNumber, String token) {
        seatPort.releaseSeat(seatNumber, token);
    }
}
