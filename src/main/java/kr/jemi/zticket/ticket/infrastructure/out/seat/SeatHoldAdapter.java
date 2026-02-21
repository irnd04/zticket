package kr.jemi.zticket.ticket.infrastructure.out.seat;

import kr.jemi.zticket.seat.api.SeatFacade;
import kr.jemi.zticket.ticket.application.port.out.SeatHoldPort;
import org.springframework.stereotype.Component;

@Component
public class SeatHoldAdapter implements SeatHoldPort {

    private final SeatFacade seatFacade;

    public SeatHoldAdapter(SeatFacade seatFacade) {
        this.seatFacade = seatFacade;
    }

    @Override
    public boolean holdSeat(int seatNumber, String token, long ttlSeconds) {
        return seatFacade.holdSeat(seatNumber, token, ttlSeconds);
    }

    @Override
    public void paySeat(int seatNumber, String token) {
        seatFacade.paySeat(seatNumber, token);
    }

    @Override
    public void releaseSeat(int seatNumber, String token) {
        seatFacade.releaseSeat(seatNumber, token);
    }
}
