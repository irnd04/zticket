package kr.jemi.zticket.seat.adapter.out.redis;

import kr.jemi.zticket.seat.domain.Seat;
import kr.jemi.zticket.seat.domain.SeatStatus;
import kr.jemi.zticket.seat.domain.Seats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SeatRedisAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SeatRedisAdapter seatRedisAdapter;

    @Test
    @DisplayName("Redis 값에 따라 Seat을 올바르게 매핑한다")
    void shouldMapRedisValuesToSeat() {
        // given
        List<Integer> seats = List.of(1, 2, 3);
        List<String> keys = List.of("seat:1", "seat:2", "seat:3");
        List<String> values = Arrays.asList("held:token-1", "paid:token-2", null);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(keys)).willReturn(values);

        // when
        Seats result = seatRedisAdapter.getStatuses(seats);

        // then
        assertThat(result.of(1)).isEqualTo(new Seat(SeatStatus.HELD, "token-1"));
        assertThat(result.of(2)).isEqualTo(new Seat(SeatStatus.PAID, "token-2"));
        assertThat(result.of(3)).isEqualTo(new Seat(SeatStatus.AVAILABLE, null));
    }

    @Test
    @DisplayName("알 수 없는 Redis 값이면 예외를 던진다")
    void shouldThrowOnCorruptedRedisValue() {
        // given
        List<Integer> seats = List.of(1);
        List<String> keys = List.of("seat:1");
        List<String> values = Arrays.asList("corrupted");

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(keys)).willReturn(values);

        // when & then
        assertThatThrownBy(() -> seatRedisAdapter.getStatuses(seats))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 유저가 이미 hold한 좌석을 다시 hold하면 성공하고 TTL을 갱신한다")
    void shouldSucceedWhenSameUserReHolds() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent("seat:1", "held:token-1", 300, TimeUnit.SECONDS))
                .willReturn(false);
        given(valueOperations.get("seat:1")).willReturn("held:token-1");
        given(redisTemplate.expire("seat:1", 300, TimeUnit.SECONDS)).willReturn(true);

        // when
        boolean result = seatRedisAdapter.holdSeat(1, "token-1", 300);

        // then
        assertThat(result).isTrue();
        then(redisTemplate).should().expire("seat:1", 300, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("다른 유저가 hold한 좌석을 hold하면 실패한다")
    void shouldFailWhenDifferentUserHolds() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent("seat:1", "held:token-2", 300, TimeUnit.SECONDS))
                .willReturn(false);
        given(valueOperations.get("seat:1")).willReturn("held:token-1");

        // when
        boolean result = seatRedisAdapter.holdSeat(1, "token-2", 300);

        // then
        assertThat(result).isFalse();
    }
}
