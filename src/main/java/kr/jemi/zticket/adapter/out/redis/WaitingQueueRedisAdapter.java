package kr.jemi.zticket.adapter.out.redis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import kr.jemi.zticket.application.port.out.WaitingQueuePort;

@Component
public class WaitingQueueRedisAdapter implements WaitingQueuePort {

    private static final String KEY = "waiting_queue";

    private final StringRedisTemplate redisTemplate;

    public WaitingQueueRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long enqueue(String uuid) {
        redisTemplate.opsForZSet().add(KEY, uuid, System.currentTimeMillis());
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
        Set<String> members = redisTemplate.opsForZSet().range(KEY, 0, count - 1);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(members);
    }

    @Override
    public void removeBatch(List<String> uuids) {
        if (!uuids.isEmpty()) {
            redisTemplate.opsForZSet().remove(KEY, uuids.toArray());
        }
    }
}
