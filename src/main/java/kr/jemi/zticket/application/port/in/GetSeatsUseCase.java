package kr.jemi.zticket.application.port.in;

import java.util.Map;

public interface GetSeatsUseCase {

    Map<Integer, String> getAllSeatStatuses(int totalSeats);
}
