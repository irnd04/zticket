package kr.jemi.zticket.queue.infrastructure.out.redis;

import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import kr.jemi.zticket.queue.application.port.out.WaitingQueueHeartbeatPort;

@Component
public class WaitingQueueHeartbeatRedisAdapter implements WaitingQueueHeartbeatPort {

    private static final String KEY = "waiting_queue_heartbeat";

    private final StringRedisTemplate redisTemplate;

    public WaitingQueueHeartbeatRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void refresh(String token) {
        redisTemplate.opsForZSet().add(KEY, token, System.currentTimeMillis());
    }

    @Override
    public List<String> findExpired(long cutoffTimestamp, int size) {
        Set<String> batch = redisTemplate.opsForZSet()
                .rangeByScore(KEY, Double.NEGATIVE_INFINITY, cutoffTimestamp, 0, size);
        if (batch == null || batch.isEmpty()) {
            return List.of();
        }
        return List.copyOf(batch);
    }

    @Override
    public void removeAll(List<String> tokens) {
        if (!tokens.isEmpty()) {
            redisTemplate.opsForZSet().remove(KEY, tokens.toArray());
        }
    }
}
