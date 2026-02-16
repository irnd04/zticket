package kr.jemi.zticket.application.port.in;

import kr.jemi.zticket.domain.seat.SeatStatuses;

public interface GetSeatsUseCase {

    SeatStatuses getAllSeatStatuses(int totalSeats);
}
