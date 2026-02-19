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
    @DisplayName("peekCandidates(0)은 빈 리스트를 반환한다")
    void peek_zero_returns_empty() {
        waitingQueuePort.enqueue("uuid-1");
        waitingQueuePort.enqueue("uuid-2");
        waitingQueuePort.enqueue("uuid-3");

        List<String> result = waitingQueuePort.peek(0);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("peekCandidates(1)은 FIFO 순서로 정확히 1건만 반환한다")
    void peek_one_returns_single() {
        waitingQueuePort.enqueue("uuid-1");
        waitingQueuePort.enqueue("uuid-2");
        waitingQueuePort.enqueue("uuid-3");

        List<String> result = waitingQueuePort.peek(1);

        assertThat(result).containsExactly("uuid-1");
    }

    @Test
    @DisplayName("peekCandidates(음수)도 빈 리스트를 반환한다")
    void peek_negative_returns_empty() {
        waitingQueuePort.enqueue("uuid-1");

        List<String> result = waitingQueuePort.peek(-1);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("remove는 waiting_queue에서 유저를 제거한다")
    void remove_deletes_from_queue() {
        waitingQueuePort.enqueue("uuid-1");
        waitingQueuePort.enqueue("uuid-2");

        waitingQueuePort.removeAll(List.of("uuid-1"));

        assertThat(waitingQueuePort.getRank("uuid-1")).isNull();
        assertThat(waitingQueuePort.getRank("uuid-2")).isNotNull();
    }
}
