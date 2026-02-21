package kr.jemi.zticket.queue.application.service;

import kr.jemi.zticket.queue.api.QueueFacade;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import org.springframework.stereotype.Service;

@Service
public class ActiveUserService implements QueueFacade {

    private final ActiveUserPort activeUserPort;

    public ActiveUserService(ActiveUserPort activeUserPort) {
        this.activeUserPort = activeUserPort;
    }

    @Override
    public boolean isActive(String token) {
        return activeUserPort.isActive(token);
    }

    @Override
    public void deactivate(String token) {
        activeUserPort.deactivate(token);
    }
}
