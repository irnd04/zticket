package kr.jemi.zticket.adapter.in.web;

import kr.jemi.zticket.adapter.in.web.dto.SeatStatusResponse;
import kr.jemi.zticket.application.port.in.GetSeatsUseCase;
import kr.jemi.zticket.domain.seat.SeatStatuses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
public class SeatApiController {

    private final GetSeatsUseCase getSeatsUseCase;
    private final int totalSeats;

    public SeatApiController(GetSeatsUseCase getSeatsUseCase,
                             @Value("${zticket.seat.total-count}") int totalSeats) {
        this.getSeatsUseCase = getSeatsUseCase;
        this.totalSeats = totalSeats;
    }

    @GetMapping("/status")
    public ResponseEntity<List<SeatStatusResponse>> getStatus() {
        SeatStatuses seatStatuses = getSeatsUseCase.getAllSeatStatuses(totalSeats);
        List<SeatStatusResponse> response = seatStatuses.seatNumbers().stream()
                .map(seat -> SeatStatusResponse.from(seat, seatStatuses.of(seat)))
                .toList();
        return ResponseEntity.ok(response);
    }
}
