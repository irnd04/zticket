package kr.jemi.zticket.queue.application.port.out;

public interface ActiveUserPort {

    void activate(String uuid, long ttlSeconds);

    void deactivate(String uuid);

    boolean isActive(String uuid);

    long countActive();
}
