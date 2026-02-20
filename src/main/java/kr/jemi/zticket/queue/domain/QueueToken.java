package kr.jemi.zticket.queue.domain;

public record QueueToken(String token, long rank, QueueStatus status) {

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
