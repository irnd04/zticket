package kr.jemi.zticket.queue.adapter.out.redis;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;

@Component
public class ActiveUserRedisAdapter implements ActiveUserPort {

    private static final String KEY_PREFIX = "active_user:";

    private final StringRedisTemplate redisTemplate;

    public ActiveUserRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void activate(String uuid, long ttlSeconds) {
        redisTemplate.opsForValue().set(KEY_PREFIX + uuid, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void deactivate(String uuid) {
        redisTemplate.delete(KEY_PREFIX + uuid);
    }

    @Override
    public boolean isActive(String uuid) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + uuid));
    }

    @Override
    public int countActive() {
        int count = 0;
        ScanOptions options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + "*").count(5000).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
        }
        return count;
    }
}
