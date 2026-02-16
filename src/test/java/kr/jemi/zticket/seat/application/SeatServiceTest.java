package kr.jemi.zticket.seat.application;

import kr.jemi.zticket.seat.application.port.out.SeatHoldPort;
import kr.jemi.zticket.seat.domain.SeatStatus;
import kr.jemi.zticket.seat.domain.SeatStatuses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock
    private SeatHoldPort seatHoldPort;

    @InjectMocks
    private SeatService seatService;

    @Test
    @DisplayName("SeatHoldPort로부터 받은 상태를 그대로 반환한다")
    void shouldDelegateToSeatHoldPort() {
        // given
        SeatStatuses portStatuses = new SeatStatuses(Map.of(
                1, SeatStatus.HELD,
                2, SeatStatus.PAID,
                3, SeatStatus.AVAILABLE
        ));
        given(seatHoldPort.getStatuses(anyList())).willReturn(portStatuses);

        // when
        SeatStatuses result = seatService.getAllSeatStatuses(3);

        // then
        assertThat(result.of(1)).isEqualTo(SeatStatus.HELD);
        assertThat(result.of(2)).isEqualTo(SeatStatus.PAID);
        assertThat(result.of(3)).isEqualTo(SeatStatus.AVAILABLE);
    }
}
