package kr.jemi.zticket.queue.application.port.out;

import java.util.List;

public interface WaitingQueuePort {

    long enqueue(String token);

    Long getRank(String token);

    List<String> peek(int count);

    void removeAll(List<String> tokens);
}
