package kr.jemi.zticket.queue.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QueueTokenTest {

    @Test
    @DisplayName("waiting()으로 생성하면 WAITING 상태와 순번을 가진다")
    void waitingToken() {
        QueueToken token = QueueToken.waiting("uuid-1", 347);

        assertThat(token.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(token.rank()).isEqualTo(347);
    }

    @Test
    @DisplayName("active()로 생성하면 ACTIVE 상태이고 rank=0")
    void activeToken() {
        QueueToken token = QueueToken.active("uuid-1");

        assertThat(token.status()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(token.rank()).isZero();
    }

    @Test
    @DisplayName("soldOut()으로 생성하면 SOLD_OUT 상태")
    void soldOutToken() {
        QueueToken token = QueueToken.soldOut("uuid-1");

        assertThat(token.status()).isEqualTo(QueueStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("record의 동등성 비교가 정상 동작한다")
    void shouldSupportRecordEquality() {
        QueueToken token1 = QueueToken.waiting("uuid-1", 5);
        QueueToken token2 = QueueToken.waiting("uuid-1", 5);

        assertThat(token1).isEqualTo(token2);
    }
}
