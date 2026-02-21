package kr.jemi.zticket.seat.application.service;

import kr.jemi.zticket.seat.application.port.out.SeatPort;
import kr.jemi.zticket.seat.domain.Seat;
import kr.jemi.zticket.seat.domain.SeatStatus;
import kr.jemi.zticket.seat.domain.Seats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock
    private SeatPort seatPort;

    private SeatService seatService;

    @BeforeEach
    void setUp() {
        seatService = new SeatService(seatPort, 3);
    }

    @Test
    @DisplayName("SeatPort로부터 받은 상태를 그대로 반환한다")
    void shouldDelegateToSeatPort() {
        // given
        Seats portStatuses = new Seats(Map.of(
                1, new Seat(SeatStatus.HELD, "token-1"),
                2, new Seat(SeatStatus.PAID, "token-2"),
                3, new Seat(SeatStatus.AVAILABLE, null)
        ));
        given(seatPort.getStatuses(anyList())).willReturn(portStatuses);

        // when
        Seats result = seatService.getSeats();

        // then
        assertThat(result.of(1)).isEqualTo(new Seat(SeatStatus.HELD, "token-1"));
        assertThat(result.of(2)).isEqualTo(new Seat(SeatStatus.PAID, "token-2"));
        assertThat(result.of(3)).isEqualTo(new Seat(SeatStatus.AVAILABLE, null));
    }
}
