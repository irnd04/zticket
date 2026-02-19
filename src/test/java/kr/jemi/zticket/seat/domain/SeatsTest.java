package kr.jemi.zticket.seat.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatsTest {

    @Test
    @DisplayName("of: 존재하는 좌석 번호를 조회하면 해당 Seat를 반환한다")
    void of_returns_seat() {
        Seat seat = new Seat(SeatStatus.AVAILABLE, null);
        Seats seats = new Seats(Map.of(1, seat));

        assertThat(seats.of(1)).isEqualTo(seat);
    }

    @Test
    @DisplayName("of: 존재하지 않는 좌석 번호를 조회하면 예외가 발생한다")
    void of_throws_for_unknown_seat() {
        Seats seats = new Seats(Map.of(1, new Seat(SeatStatus.AVAILABLE, null)));

        assertThatThrownBy(() -> seats.of(999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("seatNumbers: 좌석 번호를 오름차순 정렬하여 반환한다")
    void seatNumbers_returns_sorted() {
        Seats seats = new Seats(Map.of(
                3, new Seat(SeatStatus.AVAILABLE, null),
                1, new Seat(SeatStatus.HELD, "token-1"),
                2, new Seat(SeatStatus.PAID, "token-2")
        ));

        assertThat(seats.seatNumbers()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("size: 좌석 개수를 반환한다")
    void size_returns_count() {
        Seats seats = new Seats(Map.of(
                1, new Seat(SeatStatus.AVAILABLE, null),
                2, new Seat(SeatStatus.AVAILABLE, null)
        ));

        assertThat(seats.size()).isEqualTo(2);
    }
}
