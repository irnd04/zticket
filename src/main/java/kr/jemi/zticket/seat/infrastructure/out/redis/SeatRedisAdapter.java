package kr.jemi.zticket.seat.infrastructure.out.redis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import kr.jemi.zticket.seat.infrastructure.out.redis.dto.RedisSeat;
import kr.jemi.zticket.seat.application.port.out.SeatPort;
import kr.jemi.zticket.seat.domain.Seat;
import kr.jemi.zticket.seat.domain.Seats;

@Component
public class SeatRedisAdapter implements SeatPort {

    private static final String KEY_PREFIX = "seat:";
    private static final DefaultRedisScript<Boolean> RELEASE_IF_VALUE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """, Boolean.class);

    private final StringRedisTemplate redisTemplate;

    public SeatRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean holdSeat(int seatNumber, String uuid, long ttlSeconds) {
        String key = KEY_PREFIX + seatNumber;
        String value = "held:" + uuid;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, value, ttlSeconds, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(success)) {
            return true;
        }
        String existing = redisTemplate.opsForValue().get(key);
        if (value.equals(existing)) {
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            return true;
        }
        return false;
    }

    @Override
    public void paySeat(int seatNumber, String uuid) {
        redisTemplate.opsForValue().set(KEY_PREFIX + seatNumber, "paid:" + uuid);
    }

    @Override
    public void releaseSeat(int seatNumber, String uuid) {
        redisTemplate.execute(RELEASE_IF_VALUE_SCRIPT,
                List.of(KEY_PREFIX + seatNumber), "held:" + uuid);
    }

    @Override
    public Seats getStatuses(List<Integer> seatNumbers) {
        List<String> keys = seatNumbers.stream()
                .map(n -> KEY_PREFIX + n)
                .toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        Map<Integer, Seat> statuses = new HashMap<>();
        for (int i = 0; i < seatNumbers.size(); i++) {
            String value = values.get(i);
            int seatNumber = seatNumbers.get(i);
            statuses.put(seatNumber, RedisSeat.from(value).toDomain());
        }
        return new Seats(statuses);
    }
}
