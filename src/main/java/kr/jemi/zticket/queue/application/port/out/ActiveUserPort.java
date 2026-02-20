package kr.jemi.zticket.queue.application.port.out;

import java.util.List;

public interface ActiveUserPort {

    void activate(String token, long ttlSeconds);

    void activateBatch(List<String> tokens, long ttlSeconds);

    void deactivate(String token);

    boolean isActive(String token);

    int countActive();
}
