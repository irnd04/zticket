package kr.jemi.zticket.queue.application.port.out;

import java.util.List;

public interface ActiveUserPort {

    void activate(String uuid, long ttlSeconds);

    void activateBatch(List<String> uuids, long ttlSeconds);

    void deactivate(String uuid);

    boolean isActive(String uuid);

    int countActive();
}
