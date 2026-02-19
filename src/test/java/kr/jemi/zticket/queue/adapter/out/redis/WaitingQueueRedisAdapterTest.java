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

    private static final long FRESH_CUTOFF = 0L;

    @Test
    @DisplayName("peekBatch(0)은 빈 리스트를 반환한다")
    void peekBatch_zero_returns_empty() {
        // given - 대기열에 3명 등록
        waitingQueuePort.enqueue("uuid-1");
        waitingQueuePort.enqueue("uuid-2");
        waitingQueuePort.enqueue("uuid-3");

        // when
        List<String> result = waitingQueuePort.peekBatch(0, FRESH_CUTOFF);

        // then - 0명 요청이므로 빈 리스트 반환
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("peekBatch(1)은 정확히 1건만 반환한다")
    void peekBatch_one_returns_single() {
        // given - 대기열에 3명 등록
        waitingQueuePort.enqueue("uuid-1");
        waitingQueuePort.enqueue("uuid-2");
        waitingQueuePort.enqueue("uuid-3");

        // when
        List<String> result = waitingQueuePort.peekBatch(1, FRESH_CUTOFF);

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
        List<String> result = waitingQueuePort.peekBatch(-1, FRESH_CUTOFF);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("peekBatch는 cutoff 이전 heartbeat 유저를 제외한다")
    void peekBatch_excludes_expired_heartbeat() {
        // given - uuid-1의 heartbeat만 오래됨
        waitingQueuePort.enqueue("uuid-1");
        waitingQueuePort.enqueue("uuid-2");

        // uuid-2만 heartbeat 갱신
        waitingQueuePort.refreshScore("uuid-2");

        // when - cutoff를 현재 시각 직전으로 설정 (uuid-1의 진입 시각 이후)
        long cutoff = System.currentTimeMillis() - 1;
        List<String> result = waitingQueuePort.peekBatch(10, cutoff);

        // then - uuid-2만 반환 (uuid-1은 heartbeat가 cutoff 이전)
        assertThat(result).containsExactly("uuid-2");
    }
}
