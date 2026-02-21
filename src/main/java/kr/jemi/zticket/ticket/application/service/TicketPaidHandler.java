package kr.jemi.zticket.ticket.application.service;

import kr.jemi.zticket.ticket.application.port.in.HandleTicketPaidUseCase;
import kr.jemi.zticket.ticket.application.port.out.ActiveUserCheckPort;
import kr.jemi.zticket.ticket.application.port.out.SeatHoldPort;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.ticket.domain.Ticket;
import org.springframework.stereotype.Service;

@Service
public class TicketPaidHandler implements HandleTicketPaidUseCase {

    private final SeatHoldPort seatHoldPort;
    private final TicketPort ticketPort;
    private final ActiveUserCheckPort activeUserCheckPort;

    public TicketPaidHandler(SeatHoldPort seatHoldPort,
                             TicketPort ticketPort,
                             ActiveUserCheckPort activeUserCheckPort) {
        this.seatHoldPort = seatHoldPort;
        this.ticketPort = ticketPort;
        this.activeUserCheckPort = activeUserCheckPort;
    }

    @Override
    public void handle(long ticketId) {
        Ticket ticket = ticketPort.findById(ticketId)
                .orElseThrow(() -> new IllegalStateException("티켓 없음: " + ticketId));

        // 4. Redis 좌석 결제 확정 (held → paid)
        seatHoldPort.paySeat(ticket.getSeatNumber(), ticket.getQueueToken());

        // 5. DB 티켓 상태를 SYNCED로 변경
        ticket.sync();
        ticketPort.update(ticket);

        // 6. active 유저에서 제거
        activeUserCheckPort.deactivate(ticket.getQueueToken());
    }
}
