package kr.jemi.zticket.queue.adapter.out.redis;

import kr.jemi.zticket.integration.IntegrationTestBase;
import kr.jemi.zticket.queue.application.port.out.WaitingQueuePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingQueueRedisAdapterTest extends IntegrationTestBase {

    @Autowired
    WaitingQueuePort waitingQueuePort;

    @Test
    @DisplayName("peekBatch(0)은 빈 리스트를 반환한다 (ZRANGE 0 -1 전체 반환 방지)")
    void peekBatch_zero_returns_empty() {
        // given - 대기열에 3명 등록
        waitingQueuePort.enqueue("uuid-1");
        waitingQueuePort.enqueue("uuid-2");
        waitingQueuePort.enqueue("uuid-3");

        // when
        List<String> result = waitingQueuePort.peekBatch(0);

        // then - 0명 요청이므로 빈 리스트 반환 (전체 반환되면 안 됨)
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("peekBatch(1)은 정확히 1건만 반환한다 (ZRANGE 0 0)")
    void peekBatch_one_returns_single() {
        // given - 대기열에 3명 등록
        waitingQueuePort.enqueue("uuid-1");
        waitingQueuePort.enqueue("uuid-2");
        waitingQueuePort.enqueue("uuid-3");

        // when
        List<String> result = waitingQueuePort.peekBatch(1);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("uuid-1");
    }

    @Test
    @DisplayName("peekBatch(음수)도 빈 리스트를 반환한다")
    void peekBatch_negative_returns_empty() {
        // given
        waitingQueuePort.enqueue("uuid-1");

        // when
        List<String> result = waitingQueuePort.peekBatch(-1);

        // then
        assertThat(result).isEmpty();
    }
}
