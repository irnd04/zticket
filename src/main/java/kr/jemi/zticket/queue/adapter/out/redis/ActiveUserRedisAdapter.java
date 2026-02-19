package kr.jemi.zticket.queue.adapter.out.redis;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
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
    public void activateBatch(List<String> uuids, long ttlSeconds) {
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String uuid : uuids) {
                    operations.opsForValue().set(KEY_PREFIX + uuid, "1", ttlSeconds, TimeUnit.SECONDS);
                }
                return null;
            }
        });
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
