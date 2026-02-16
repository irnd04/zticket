package kr.jemi.zticket.seat.application.port.in;

import kr.jemi.zticket.seat.domain.SeatStatuses;

public interface GetSeatsUseCase {

    SeatStatuses getAllSeatStatuses(int totalSeats);
}
