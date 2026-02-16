package kr.jemi.zticket.adapter.out.redis;

import kr.jemi.zticket.domain.seat.SeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SeatHoldRedisAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private DefaultRedisScript<Long> paySeatScript;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SeatHoldRedisAdapter seatHoldRedisAdapter;

    @Test
    @DisplayName("Redis 값에 따라 SeatStatus를 올바르게 매핑한다")
    void shouldMapRedisValuesToSeatStatus() {
        // given
        List<Integer> seats = List.of(1, 2, 3, 4);
        List<String> keys = List.of("seat:1", "seat:2", "seat:3", "seat:4");
        List<String> values = Arrays.asList("held:token-1", "paid:token-2", null, "corrupted");

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(keys)).willReturn(values);

        // when
        Map<Integer, SeatStatus> result = seatHoldRedisAdapter.getStatuses(seats);

        // then
        assertThat(result)
                .containsEntry(1, SeatStatus.HELD)
                .containsEntry(2, SeatStatus.PAID)
                .containsEntry(3, SeatStatus.AVAILABLE)
                .containsEntry(4, SeatStatus.UNKNOWN);
    }
}
