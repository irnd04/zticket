package kr.jemi.zticket.queue.infrastructure.out.redis;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
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
    public void activate(String token, long ttlSeconds) {
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void activateBatch(List<String> tokens, long ttlSeconds) {
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String token : tokens) {
                    operations.opsForValue().set(KEY_PREFIX + token, "1", ttlSeconds, TimeUnit.SECONDS);
                }
                return null;
            }
        });
    }

    @Override
    public void deactivate(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }

    @Override
    public boolean isActive(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }

    @Override
    public int countActive() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }
}
