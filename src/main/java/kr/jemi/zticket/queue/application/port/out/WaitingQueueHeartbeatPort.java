package kr.jemi.zticket.queue.application.port.out;

import java.util.List;

public interface WaitingQueueHeartbeatPort {

    void refresh(String token);

    List<String> findExpired(long cutoffTimestamp, int size);

    void removeAll(List<String> tokens);
}
