package kr.jemi.zticket.queue.adapter.out.redis;

import kr.jemi.zticket.integration.IntegrationTestBase;
import kr.jemi.zticket.queue.application.port.out.WaitingQueueHeartbeatPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingQueueHeartbeatRedisAdapterTest extends IntegrationTestBase {

    @Autowired
    WaitingQueueHeartbeatPort waitingQueueHeartbeatPort;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("getScores는 heartbeat이 없는 유저에 대해 null을 반환한다")
    void getScores_returns_null_for_missing() {
        waitingQueueHeartbeatPort.refresh("uuid-1");

        List<Long> scores = waitingQueueHeartbeatPort.getScores(List.of("uuid-1", "uuid-2"));

        assertThat(scores).hasSize(2);
        assertThat(scores.get(0)).isNotNull();
        assertThat(scores.get(1)).isNull();
    }

    @Test
    @DisplayName("findExpired는 cutoff 이후에 갱신된 유저는 반환하지 않는다")
    void findExpired_excludes_refreshed_after_cutoff() throws InterruptedException {
        waitingQueueHeartbeatPort.refresh("uuid-1");
        waitingQueueHeartbeatPort.refresh("uuid-2");
        waitingQueueHeartbeatPort.refresh("uuid-3");

        Thread.sleep(2);
        long cutoff = System.currentTimeMillis() - 1;

        waitingQueueHeartbeatPort.refresh("uuid-3");

        List<String> expired = waitingQueueHeartbeatPort.findExpired(cutoff);

        assertThat(expired).containsExactlyInAnyOrder("uuid-1", "uuid-2");
        assertThat(redisTemplate.opsForZSet().rank("waiting_queue_heartbeat", "uuid-1")).isNull();
        assertThat(redisTemplate.opsForZSet().rank("waiting_queue_heartbeat", "uuid-2")).isNull();
        assertThat(redisTemplate.opsForZSet().rank("waiting_queue_heartbeat", "uuid-3")).isNotNull();
    }

    @Test
    @DisplayName("findExpired는 만료 유저가 없으면 빈 리스트를 반환한다")
    void findExpired_returns_empty_when_none_expired() {
        waitingQueueHeartbeatPort.refresh("uuid-1");

        List<String> expired = waitingQueueHeartbeatPort.findExpired(0);

        assertThat(expired).isEmpty();
    }
}
