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
    private static final int EXPIRE_BATCH_SIZE = 5000;

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
    public List<String> peekBatch(int count, long cutoffTimestamp) {
        if (count <= 0) {
            return List.of();
        }
        Set<String> members = redisTemplate.opsForZSet()
                .rangeByScore(HEARTBEAT_KEY, cutoffTimestamp, Double.POSITIVE_INFINITY, 0, count);
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
        long totalRemoved = 0;
        while (true) {
            Set<String> batch = redisTemplate.opsForZSet()
                    .rangeByScore(HEARTBEAT_KEY, 0, cutoffTimestamp, 0, EXPIRE_BATCH_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            Object[] uuids = batch.toArray();
            redisTemplate.opsForZSet().remove(HEARTBEAT_KEY, uuids);
            redisTemplate.opsForZSet().remove(KEY, uuids);
            totalRemoved += batch.size();
        }
        return totalRemoved;
    }
}
