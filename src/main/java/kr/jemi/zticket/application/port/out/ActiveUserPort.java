package kr.jemi.zticket.application.port.out;

public interface ActiveUserPort {

    void activate(String uuid, long ttlSeconds);

    boolean isActive(String uuid);

    long countActive();
}
