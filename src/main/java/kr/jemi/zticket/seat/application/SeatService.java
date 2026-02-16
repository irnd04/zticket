package kr.jemi.zticket.seat.application;

import kr.jemi.zticket.seat.application.port.in.GetSeatsUseCase;
import kr.jemi.zticket.seat.application.port.out.SeatHoldPort;
import kr.jemi.zticket.seat.domain.SeatStatuses;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class SeatService implements GetSeatsUseCase {

    private final SeatHoldPort seatHoldPort;

    public SeatService(SeatHoldPort seatHoldPort) {
        this.seatHoldPort = seatHoldPort;
    }

    @Override
    public SeatStatuses getAllSeatStatuses(int totalSeats) {
        List<Integer> allSeats = IntStream.rangeClosed(1, totalSeats)
                .boxed()
                .toList();
        return seatHoldPort.getStatuses(allSeats);
    }
}
