package kr.jemi.zticket.queue.adapter.out.redis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import kr.jemi.zticket.queue.application.port.out.WaitingQueuePort;

@Component
public class WaitingQueueRedisAdapter implements WaitingQueuePort {

    private static final String KEY = "waiting_queue";
    private static final String HEARTBEAT_KEY = "waiting_queue_heartbeat";

    private final StringRedisTemplate redisTemplate;

    public WaitingQueueRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long enqueue(String uuid) {
        double now = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(KEY, uuid, now);
        redisTemplate.opsForZSet().add(HEARTBEAT_KEY, uuid, now);
        Long rank = redisTemplate.opsForZSet().rank(KEY, uuid);
        if (rank == null) {
            return -1;
        }
        return rank + 1;
    }

    @Override
    public Long getRank(String uuid) {
        Long rank = redisTemplate.opsForZSet().rank(KEY, uuid);
        if (rank == null) {
            return null;
        }
        return rank + 1;
    }

    @Override
    public List<String> peekBatch(int count) {
        if (count <= 0) {
            return List.of();
        }
        Set<String> members = redisTemplate.opsForZSet().range(KEY, 0, count - 1);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(members);
    }

    @Override
    public void removeBatch(List<String> uuids) {
        if (!uuids.isEmpty()) {
            Object[] members = uuids.toArray();
            redisTemplate.opsForZSet().remove(KEY, members);
            redisTemplate.opsForZSet().remove(HEARTBEAT_KEY, members);
        }
    }

    @Override
    public void refreshScore(String uuid) {
        redisTemplate.opsForZSet().add(HEARTBEAT_KEY, uuid, System.currentTimeMillis());
    }

    @Override
    public long removeExpired(long cutoffTimestamp) {
        Set<String> expired = redisTemplate.opsForZSet().rangeByScore(HEARTBEAT_KEY, 0, cutoffTimestamp);
        if (expired == null || expired.isEmpty()) {
            return 0;
        }
        Object[] uuids = expired.toArray();
        redisTemplate.opsForZSet().remove(HEARTBEAT_KEY, uuids);
        redisTemplate.opsForZSet().remove(KEY, uuids);
        return expired.size();
    }
}
