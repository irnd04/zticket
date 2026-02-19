package kr.jemi.zticket.queue.application.port.out;

import java.util.List;

public interface WaitingQueuePort {

    long enqueue(String uuid);

    Long getRank(String uuid);

    List<String> peek(int count);

    void removeAll(List<String> uuids);
}
