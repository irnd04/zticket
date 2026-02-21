package kr.jemi.zticket.seat.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatTest {

    @Nested
    @DisplayName("생성 validation")
    class Validation {

        @Test
        @DisplayName("status가 null이면 예외가 발생한다")
        void null_status_throws() {
            assertThatThrownBy(() -> new Seat(null, null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("AVAILABLE 좌석은 owner가 null이어야 한다")
        void available_with_owner_throws() {
            assertThatThrownBy(() -> new Seat(SeatStatus.AVAILABLE, "token-1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("AVAILABLE 좌석은 owner가 null이면 정상 생성된다")
        void available_without_owner_succeeds() {
            Seat seat = new Seat(SeatStatus.AVAILABLE, null);

            assertThat(seat.status()).isEqualTo(SeatStatus.AVAILABLE);
            assertThat(seat.owner()).isNull();
        }

        @Test
        @DisplayName("HELD 좌석은 owner가 반드시 있어야 한다")
        void held_without_owner_throws() {
            assertThatThrownBy(() -> new Seat(SeatStatus.HELD, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("PAID 좌석은 owner가 반드시 있어야 한다")
        void paid_without_owner_throws() {
            assertThatThrownBy(() -> new Seat(SeatStatus.PAID, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("HELD 좌석의 owner가 빈 문자열이면 예외가 발생한다")
        void held_with_blank_owner_throws() {
            assertThatThrownBy(() -> new Seat(SeatStatus.HELD, ""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Seat(SeatStatus.HELD, "   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("isAvailableFor")
    class IsAvailableFor {

        @Test
        @DisplayName("AVAILABLE 좌석은 누구에게나 결제 가능하다")
        void available_seat_is_available_for_anyone() {
            Seat seat = new Seat(SeatStatus.AVAILABLE, null);

            assertThat(seat.isAvailableFor("token-1")).isTrue();
            assertThat(seat.isAvailableFor(null)).isTrue();
        }

        @Test
        @DisplayName("내가 HELD한 좌석은 결제 가능하다")
        void held_seat_is_available_for_owner() {
            Seat seat = new Seat(SeatStatus.HELD, "token-1");

            assertThat(seat.isAvailableFor("token-1")).isTrue();
        }

        @Test
        @DisplayName("다른 사람이 HELD한 좌석은 결제 불가하다")
        void held_seat_is_unavailable_for_others() {
            Seat seat = new Seat(SeatStatus.HELD, "token-1");

            assertThat(seat.isAvailableFor("token-2")).isFalse();
        }

        @Test
        @DisplayName("token이 null이면 HELD 좌석은 결제 불가하다")
        void held_seat_is_unavailable_for_null_token() {
            Seat seat = new Seat(SeatStatus.HELD, "token-1");

            assertThat(seat.isAvailableFor(null)).isFalse();
        }

        @Test
        @DisplayName("PAID 좌석은 누구에게도 결제 불가하다")
        void paid_seat_is_unavailable_for_anyone() {
            Seat seat = new Seat(SeatStatus.PAID, "token-1");

            assertThat(seat.isAvailableFor("token-1")).isFalse();
            assertThat(seat.isAvailableFor("token-2")).isFalse();
            assertThat(seat.isAvailableFor(null)).isFalse();
        }
    }
}
