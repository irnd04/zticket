package kr.jemi.zticket.application.port.in;

import kr.jemi.zticket.domain.seat.SeatStatus;

import java.util.Map;

public interface GetSeatsUseCase {

    Map<Integer, SeatStatus> getAllSeatStatuses(int totalSeats);
}
