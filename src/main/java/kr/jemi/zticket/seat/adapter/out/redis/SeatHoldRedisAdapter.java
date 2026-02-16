package kr.jemi.zticket.seat.adapter.out.redis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import kr.jemi.zticket.seat.application.port.out.SeatHoldPort;
import kr.jemi.zticket.seat.domain.SeatStatus;
import kr.jemi.zticket.seat.domain.SeatStatuses;

@Component
public class SeatHoldRedisAdapter implements SeatHoldPort {

    private static final String KEY_PREFIX = "seat:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> paySeatScript;

    public SeatHoldRedisAdapter(StringRedisTemplate redisTemplate,
                                DefaultRedisScript<Long> paySeatScript) {
        this.redisTemplate = redisTemplate;
        this.paySeatScript = paySeatScript;
    }

    @Override
    public boolean holdSeat(int seatNumber, String uuid, long ttlSeconds) {
        String key = KEY_PREFIX + seatNumber;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "held:" + uuid, ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public boolean paySeat(int seatNumber, String uuid) {
        List<String> keys = List.of(KEY_PREFIX + seatNumber);
        Long result = redisTemplate.execute(paySeatScript, keys, "held:" + uuid, "paid:" + uuid);
        return result != null && result == 0;
    }

    @Override
    public void setPaidSeat(int seatNumber, String uuid) {
        redisTemplate.opsForValue().set(KEY_PREFIX + seatNumber, "paid:" + uuid);
    }

    @Override
    public void releaseSeat(int seatNumber) {
        redisTemplate.delete(KEY_PREFIX + seatNumber);
    }

    @Override
    public SeatStatuses getStatuses(List<Integer> seatNumbers) {
        List<String> keys = seatNumbers.stream()
                .map(n -> KEY_PREFIX + n)
                .toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        Map<Integer, SeatStatus> statuses = new HashMap<>();
        for (int i = 0; i < seatNumbers.size(); i++) {
            String value = values != null ? values.get(i) : null;
            statuses.put(seatNumbers.get(i), toSeatStatus(value));
        }
        return new SeatStatuses(statuses);
    }

    private SeatStatus toSeatStatus(String value) {
        if (value == null) return SeatStatus.AVAILABLE;
        if (value.startsWith("held:")) return SeatStatus.HELD;
        if (value.startsWith("paid:")) return SeatStatus.PAID;
        return SeatStatus.UNKNOWN;
    }
}
