package kr.jemi.zticket.queue.domain;

public record QueueToken(String uuid, long rank, QueueStatus status) {

    public static QueueToken waiting(String uuid, long rank) {
        return new QueueToken(uuid, rank, QueueStatus.WAITING);
    }

    public static QueueToken active(String uuid) {
        return new QueueToken(uuid, 0, QueueStatus.ACTIVE);
    }

    public static QueueToken soldOut(String uuid) {
        return new QueueToken(uuid, 0, QueueStatus.SOLD_OUT);
    }
}
