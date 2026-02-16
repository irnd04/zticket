package kr.jemi.zticket.application.port.out;

import java.util.List;

public interface WaitingQueuePort {

    long enqueue(String uuid);

    Long getRank(String uuid);

    List<String> peekBatch(int count);

    void removeBatch(List<String> uuids);
}
