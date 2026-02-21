package kr.jemi.zticket.queue.api;

public interface QueueFacade {

    boolean isActive(String token);

    void deactivate(String token);
}
