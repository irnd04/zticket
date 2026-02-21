package kr.jemi.zticket.seat.infrastructure.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Seat", description = "좌석 현황 조회")
@RestController
public class SeatApiController {

    private final GetSeatsUseCase getSeatsUseCase;

    public SeatApiController(GetSeatsUseCase getSeatsUseCase) {
        this.getSeatsUseCase = getSeatsUseCase;
    }

    @Operation(summary = "전체 좌석 현황 조회", description = "모든 좌석의 선점 상태를 반환합니다. 본인이 선점한 좌석은 available로 표시됩니다.")
    @GetMapping("/api/seats")
    public ResponseEntity<List<SeatStatusResponse>> getStatus(
            @Parameter(description = "대기열 토큰") @RequestHeader("X-Queue-Token") String token) {
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

    @Operation(summary = "잔여 좌석 수 조회", description = "현재 선점 가능한 좌석 수를 반환합니다.")
    @GetMapping("/api/seats/available-count")
    public ResponseEntity<AvailableCountResponse> getAvailableCount() {
        return ResponseEntity.ok(new AvailableCountResponse(getSeatsUseCase.getAvailableCount()));
    }
}
