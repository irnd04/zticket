package kr.jemi.zticket.queue.infrastructure.out.seat;

import kr.jemi.zticket.queue.application.port.out.AvailableSeatCountPort;
import kr.jemi.zticket.seat.api.SeatFacade;
import org.springframework.stereotype.Component;

@Component
public class AvailableSeatCountAdapter implements AvailableSeatCountPort {

    private final SeatFacade seatFacade;

    public AvailableSeatCountAdapter(SeatFacade seatFacade) {
        this.seatFacade = seatFacade;
    }

    @Override
    public int getAvailableCount() {
        return seatFacade.getAvailableCount();
    }
}
