package kr.jemi.zticket.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QueueTokenTest {

    @Test
    @DisplayName("rank=0이면 ACTIVE 상태를 의미한다")
    void rankZeroMeansActive() {
        QueueToken token = new QueueToken("uuid-1", 0);

        assertThat(token.rank()).isEqualTo(0);
        assertThat(token.uuid()).isEqualTo("uuid-1");
    }

    @Test
    @DisplayName("rank=-1이면 EXPIRED 상태를 의미한다")
    void rankNegativeMeansExpired() {
        QueueToken token = new QueueToken("uuid-1", -1);

        assertThat(token.rank()).isEqualTo(-1);
    }

    @Test
    @DisplayName("rank>0이면 WAITING 상태이며 순번을 나타낸다")
    void rankPositiveMeansWaiting() {
        QueueToken token = new QueueToken("uuid-1", 347);

        assertThat(token.rank()).isEqualTo(347);
    }

    @Test
    @DisplayName("record의 동등성 비교가 정상 동작한다")
    void shouldSupportRecordEquality() {
        QueueToken token1 = new QueueToken("uuid-1", 5);
        QueueToken token2 = new QueueToken("uuid-1", 5);

        assertThat(token1).isEqualTo(token2);
    }
}
