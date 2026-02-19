package kr.jemi.zticket.queue.application;

import kr.jemi.zticket.queue.application.port.out.WaitingQueueHeartbeatPort;
import kr.jemi.zticket.queue.application.port.out.WaitingQueuePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Component
public class WaitingQueueOperator {

    private final WaitingQueuePort waitingQueuePort;
    private final WaitingQueueHeartbeatPort waitingQueueHeartbeatPort;
    private final long queueTtlMs;

    public WaitingQueueOperator(WaitingQueuePort waitingQueuePort,
                                WaitingQueueHeartbeatPort waitingQueueHeartbeatPort,
                                @Value("${zticket.admission.queue-ttl-seconds}") long queueTtlSeconds) {
        this.waitingQueuePort = waitingQueuePort;
        this.waitingQueueHeartbeatPort = waitingQueueHeartbeatPort;
        this.queueTtlMs = queueTtlSeconds * 1000;
    }

    public long enqueue(String uuid) {
        long rank = waitingQueuePort.enqueue(uuid);
        waitingQueueHeartbeatPort.refresh(uuid);
        return rank;
    }

    public Long getRank(String uuid) {
        Long rank = waitingQueuePort.getRank(uuid);
        if (rank == null) {
            return null;
        }
        List<Long> scores = waitingQueueHeartbeatPort.getScores(List.of(uuid));
        return isAlive(scores.getFirst()) ? rank : null;
    }

    public void refresh(String uuid) {
        waitingQueueHeartbeatPort.refresh(uuid);
    }

    public List<String> peekAlive(int size) {
        List<String> candidates = waitingQueuePort.peek(size * 2);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> scores = waitingQueueHeartbeatPort.getScores(candidates);
        return IntStream.range(0, candidates.size())
            .filter(i -> isAlive(scores.get(i)))
            .mapToObj(candidates::get)
            .limit(size)
            .toList();
    }

    public List<String> findExpired() {
        return waitingQueueHeartbeatPort.findExpired(getHeartbeatCutoff());
    }

    public void removeAll(List<String> uuids) {
        waitingQueuePort.removeAll(uuids);
        waitingQueueHeartbeatPort.removeAll(uuids);
    }

    private boolean isAlive(Long score) {
        return score != null && score >= getHeartbeatCutoff();
    }

    private long getHeartbeatCutoff() {
        return System.currentTimeMillis() - queueTtlMs;
    }
}
