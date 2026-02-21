package kr.jemi.zticket.seat.application.port.in;

import kr.jemi.zticket.seat.domain.Seats;

public interface GetSeatsUseCase {

    Seats getSeats();

    int getAvailableCount();
}
