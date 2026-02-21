package kr.jemi.zticket.seat.infrastructure.in.web;

import kr.jemi.zticket.seat.infrastructure.in.web.dto.AvailableCountResponse;
import kr.jemi.zticket.seat.infrastructure.in.web.dto.SeatStatusResponse;
import kr.jemi.zticket.seat.application.port.in.GetSeatsUseCase;
import kr.jemi.zticket.seat.domain.Seat;
import kr.jemi.zticket.seat.domain.Seats;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SeatApiController {

    private final GetSeatsUseCase getSeatsUseCase;

    public SeatApiController(GetSeatsUseCase getSeatsUseCase) {
        this.getSeatsUseCase = getSeatsUseCase;
    }

    @GetMapping("/api/seats")
    public ResponseEntity<List<SeatStatusResponse>> getStatus(
            @RequestHeader("X-Queue-Token") String token) {
        Seats seatStatuses = getSeatsUseCase.getSeats();
        List<SeatStatusResponse> response = seatStatuses.seatNumbers().stream()
                .map(seatNo -> {
                    Seat seat = seatStatuses.of(seatNo);
                    boolean available = seat.isAvailableFor(token);
                    return SeatStatusResponse.from(seatNo, available);
                })
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/seats/available-count")
    public ResponseEntity<AvailableCountResponse> getAvailableCount() {
        return ResponseEntity.ok(new AvailableCountResponse(getSeatsUseCase.getAvailableCount()));
    }
}
