package kr.jemi.zticket.ticket.infrastructure.out.queue;

import kr.jemi.zticket.queue.api.QueueFacade;
import kr.jemi.zticket.ticket.application.port.out.ActiveUserCheckPort;
import org.springframework.stereotype.Component;

@Component
public class ActiveUserCheckAdapter implements ActiveUserCheckPort {

    private final QueueFacade queueFacade;

    public ActiveUserCheckAdapter(QueueFacade queueFacade) {
        this.queueFacade = queueFacade;
    }

    @Override
    public boolean isActive(String token) {
        return queueFacade.isActive(token);
    }

    @Override
    public void deactivate(String token) {
        queueFacade.deactivate(token);
    }
}
