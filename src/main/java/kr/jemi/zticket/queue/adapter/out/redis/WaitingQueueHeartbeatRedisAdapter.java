package kr.jemi.zticket.queue.adapter.out.redis;

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
    public void refresh(String uuid) {
        redisTemplate.opsForZSet().add(KEY, uuid, System.currentTimeMillis());
    }

    @Override
    public List<Long> getScores(List<String> uuids) {
        if (uuids.isEmpty()) {
            return List.of();
        }
        List<Double> scores = redisTemplate.opsForZSet().score(KEY, uuids.toArray());
        return scores.stream()
                .map(s -> s != null ? s.longValue() : null)
                .toList();
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
    public void removeAll(List<String> uuids) {
        if (!uuids.isEmpty()) {
            redisTemplate.opsForZSet().remove(KEY, uuids.toArray());
        }
    }
}
