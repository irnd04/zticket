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

    private final StringRedisTemplate redisTemplate;

    public WaitingQueueRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long enqueue(String uuid) {
        redisTemplate.opsForZSet().add(KEY, uuid, System.currentTimeMillis());
        Long rank = this.getRank(uuid);
        if (rank == null) {
            throw new IllegalStateException("rank는 null일 수 없습니다.");
        }
        return rank;
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
    public List<String> peek(int count) {
        if (count <= 0) {
            return List.of();
        }
        Set<String> members = redisTemplate.opsForZSet().range(KEY, 0, (long) count - 1);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(members);
    }

    @Override
    public void removeAll(List<String> uuids) {
        if (!uuids.isEmpty()) {
            redisTemplate.opsForZSet().remove(KEY, uuids.toArray());
        }
    }
}
