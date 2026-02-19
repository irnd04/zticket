package kr.jemi.zticket.queue.application;

import kr.jemi.zticket.queue.application.port.out.WaitingQueueHeartbeatPort;
import kr.jemi.zticket.queue.application.port.out.WaitingQueuePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

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
        refresh(uuid);
        return rank;
    }

    public Long getRank(String uuid) {
        return waitingQueuePort.getRank(uuid);
    }

    public void refresh(String uuid) {
        waitingQueueHeartbeatPort.refresh(uuid);
    }

    public List<String> peek(int size) {
        return waitingQueuePort.peek(size);
    }

    public List<String> findExpired(int size) {
        return waitingQueueHeartbeatPort.findExpired(getHeartbeatCutoff(), size);
    }

    public void removeAll(List<String> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return;
        }
        waitingQueuePort.removeAll(uuids);
        waitingQueueHeartbeatPort.removeAll(uuids);
    }

    private long getHeartbeatCutoff() {
        return System.currentTimeMillis() - queueTtlMs;
    }
}
