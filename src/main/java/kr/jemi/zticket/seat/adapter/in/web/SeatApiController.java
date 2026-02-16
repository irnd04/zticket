package kr.jemi.zticket.seat.adapter.in.web;

import kr.jemi.zticket.seat.adapter.in.web.dto.SeatStatusResponse;
import kr.jemi.zticket.seat.application.port.in.GetSeatsUseCase;
import kr.jemi.zticket.seat.domain.SeatStatuses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SeatApiController {

    private final GetSeatsUseCase getSeatsUseCase;

    public SeatApiController(GetSeatsUseCase getSeatsUseCase) {
        this.getSeatsUseCase = getSeatsUseCase;
    }

    @GetMapping("/api/seats")
    public ResponseEntity<List<SeatStatusResponse>> getStatus() {
        SeatStatuses seatStatuses = getSeatsUseCase.getAllSeatStatuses();
        List<SeatStatusResponse> response = seatStatuses.seatNumbers().stream()
                .map(seat -> SeatStatusResponse.from(seat, seatStatuses.of(seat)))
                .toList();
        return ResponseEntity.ok(response);
    }
}
