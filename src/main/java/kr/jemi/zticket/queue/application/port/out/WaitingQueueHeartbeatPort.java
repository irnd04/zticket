package kr.jemi.zticket.queue.application.port.out;

import java.util.List;

public interface WaitingQueueHeartbeatPort {

    void refresh(String uuid);

    List<Long> getScores(List<String> uuids);

    List<String> findExpired(long cutoffTimestamp, int size);

    void removeAll(List<String> uuids);
}
