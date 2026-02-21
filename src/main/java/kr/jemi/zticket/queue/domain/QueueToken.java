package kr.jemi.zticket.queue.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.jemi.zticket.common.validation.SelfValidating;

public record QueueToken(@NotBlank String token, @Min(0) long rank, @NotNull QueueStatus status)
        implements SelfValidating {

    public QueueToken(String token, long rank, QueueStatus status) {
        this.token = token;
        this.rank = rank;
        this.status = status;
        validateSelf();
    }

    public static QueueToken waiting(String token, long rank) {
        return new QueueToken(token, rank, QueueStatus.WAITING);
    }

    public static QueueToken active(String token) {
        return new QueueToken(token, 0, QueueStatus.ACTIVE);
    }

    public static QueueToken soldOut(String token) {
        return new QueueToken(token, 0, QueueStatus.SOLD_OUT);
    }
}
