package kr.jemi.zticket.queue.adapter.out.redis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import kr.jemi.zticket.queue.application.port.out.HeartbeatPort;

@Component
public class HeartbeatRedisAdapter implements HeartbeatPort {

    private static final String KEY = "waiting_queue_heartbeat";
    private static final int EXPIRE_BATCH_SIZE = 5000;

    private final StringRedisTemplate redisTemplate;

    public HeartbeatRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void register(String uuid) {
        redisTemplate.opsForZSet().add(KEY, uuid, System.currentTimeMillis());
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
    public List<String> findExpired(long cutoffTimestamp) {
        List<String> expired = new ArrayList<>();
        while (true) {
            Set<String> batch = redisTemplate.opsForZSet()
                    .rangeByScore(KEY, Double.NEGATIVE_INFINITY, cutoffTimestamp, 0, EXPIRE_BATCH_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            expired.addAll(batch);
            redisTemplate.opsForZSet().remove(KEY, batch.toArray());
        }
        return expired;
    }

    @Override
    public void removeAll(List<String> uuids) {
        if (!uuids.isEmpty()) {
            redisTemplate.opsForZSet().remove(KEY, uuids.toArray());
        }
    }
}
