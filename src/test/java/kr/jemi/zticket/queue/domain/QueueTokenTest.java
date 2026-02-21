package kr.jemi.zticket.queue.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QueueTokenTest {

    @Nested
    @DisplayName("생성 validation")
    class Validation {

        @Test
        @DisplayName("token이 null이면 예외가 발생한다")
        void null_token_throws() {
            assertThatThrownBy(() -> QueueToken.waiting(null, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("token이 빈 문자열이면 예외가 발생한다")
        void blank_token_throws() {
            assertThatThrownBy(() -> QueueToken.waiting("", 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> QueueToken.waiting("   ", 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rank가 음수이면 예외가 발생한다")
        void negative_rank_throws() {
            assertThatThrownBy(() -> QueueToken.waiting("token-1", -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("status가 null이면 예외가 발생한다")
        void null_status_throws() {
            assertThatThrownBy(() -> new QueueToken("token-1", 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("팩토리 메서드")
    class FactoryMethods {

        @Test
        @DisplayName("waiting()으로 생성하면 WAITING 상태와 순번을 가진다")
        void waitingToken() {
            QueueToken token = QueueToken.waiting("token-1", 347);

            assertThat(token.status()).isEqualTo(QueueStatus.WAITING);
            assertThat(token.rank()).isEqualTo(347);
        }

        @Test
        @DisplayName("active()로 생성하면 ACTIVE 상태이고 rank=0")
        void activeToken() {
            QueueToken token = QueueToken.active("token-1");

            assertThat(token.status()).isEqualTo(QueueStatus.ACTIVE);
            assertThat(token.rank()).isZero();
        }

        @Test
        @DisplayName("soldOut()으로 생성하면 SOLD_OUT 상태")
        void soldOutToken() {
            QueueToken token = QueueToken.soldOut("token-1");

            assertThat(token.status()).isEqualTo(QueueStatus.SOLD_OUT);
        }
    }

    @Test
    @DisplayName("record의 동등성 비교가 정상 동작한다")
    void shouldSupportRecordEquality() {
        QueueToken token1 = QueueToken.waiting("token-1", 5);
        QueueToken token2 = QueueToken.waiting("token-1", 5);

        assertThat(token1).isEqualTo(token2);
    }
}
