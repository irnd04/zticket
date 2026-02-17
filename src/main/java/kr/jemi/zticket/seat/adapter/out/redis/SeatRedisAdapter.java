package kr.jemi.zticket.seat.adapter.out.redis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import kr.jemi.zticket.seat.application.port.out.SeatHoldPort;
import kr.jemi.zticket.seat.domain.Seat;
import kr.jemi.zticket.seat.domain.Seats;

@Component
public class SeatRedisAdapter implements SeatHoldPort {

    private static final String KEY_PREFIX = "seat:";

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
    public void releaseSeat(int seatNumber) {
        redisTemplate.delete(KEY_PREFIX + seatNumber);
    }

    @Override
    public Seats getStatuses(List<Integer> seatNumbers) {
        List<String> keys = seatNumbers.stream()
                .map(n -> KEY_PREFIX + n)
                .toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        Map<Integer, Seat> statuses = new HashMap<>();
        for (int i = 0; i < seatNumbers.size(); i++) {
            String value = values != null ? values.get(i) : null;
            statuses.put(seatNumbers.get(i), RedisSeat.from(value).toDomain());
        }
        return new Seats(statuses);
    }
}
