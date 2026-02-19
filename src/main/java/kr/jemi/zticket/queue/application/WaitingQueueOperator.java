package kr.jemi.zticket.queue.application;

import kr.jemi.zticket.queue.application.port.out.HeartbeatPort;
import kr.jemi.zticket.queue.application.port.out.WaitingQueuePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Component
public class WaitingQueueOperator {

    private final WaitingQueuePort waitingQueuePort;
    private final HeartbeatPort heartbeatPort;
    private final long queueTtlMs;

    public WaitingQueueOperator(WaitingQueuePort waitingQueuePort,
                                HeartbeatPort heartbeatPort,
                                @Value("${zticket.admission.queue-ttl-seconds}") long queueTtlSeconds) {
        this.waitingQueuePort = waitingQueuePort;
        this.heartbeatPort = heartbeatPort;
        this.queueTtlMs = queueTtlSeconds * 1000;
    }

    public long enqueue(String uuid) {
        long rank = waitingQueuePort.enqueue(uuid);
        heartbeatPort.register(uuid);
        return rank;
    }

    public Long getRank(String uuid) {
        Long rank = waitingQueuePort.getRank(uuid);
        if (rank == null) {
            return null;
        }
        List<Long> scores = heartbeatPort.getScores(List.of(uuid));
        return isAlive(scores.getFirst()) ? rank : null;
    }

    public void refresh(String uuid) {
        heartbeatPort.refresh(uuid);
    }

    public List<String> peekAlive(int size) {
        List<String> candidates = waitingQueuePort.peek(size * 2);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> scores = heartbeatPort.getScores(candidates);
        return IntStream.range(0, candidates.size())
            .filter(i -> isAlive(scores.get(i)))
            .mapToObj(candidates::get)
            .limit(size)
            .toList();
    }

    public List<String> findExpired() {
        return heartbeatPort.findExpired(getHeartbeatCutoff());
    }

    public void removeAll(List<String> uuids) {
        waitingQueuePort.removeAll(uuids);
        heartbeatPort.removeAll(uuids);
    }

    private boolean isAlive(Long score) {
        return score != null && score >= getHeartbeatCutoff();
    }

    private long getHeartbeatCutoff() {
        return System.currentTimeMillis() - queueTtlMs;
    }
}
