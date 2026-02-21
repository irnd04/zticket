package kr.jemi.zticket.seat.infrastructure.out.redis.dto;

import kr.jemi.zticket.seat.domain.Seat;
import kr.jemi.zticket.seat.domain.SeatStatus;

/**
 * Redis에 저장된 좌석 값("held:uuid", "paid:uuid", null)을 파싱하는 adapter DTO.
 */
public record RedisSeat(SeatStatus status, String owner) {

    public static RedisSeat from(String redisValue) {
        if (redisValue == null) {
            return new RedisSeat(SeatStatus.AVAILABLE, null);
        }
        if (redisValue.startsWith("held:")) {
            return new RedisSeat(SeatStatus.HELD, redisValue.substring("held:".length()));
        }
        if (redisValue.startsWith("paid:")) {
            return new RedisSeat(SeatStatus.PAID, redisValue.substring("paid:".length()));
        }
        throw new IllegalArgumentException("알 수 없는 Redis 좌석 값: " + redisValue);
    }

    public Seat toDomain() {
        return new Seat(status, owner);
    }
}
