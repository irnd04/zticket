package kr.jemi.zticket.ticket.application;

import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.seat.application.port.out.SeatPort;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketPaidEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class TicketPaidEventListener {

    private final SeatPort seatPort;
    private final TicketPort ticketPort;
    private final ActiveUserPort activeUserPort;

    public TicketPaidEventListener(SeatPort seatPort,
                                   TicketPort ticketPort,
                                   ActiveUserPort activeUserPort) {
        this.seatPort = seatPort;
        this.ticketPort = ticketPort;
        this.activeUserPort = activeUserPort;
    }

    @Async
    @EventListener
    public void handle(TicketPaidEvent event) {
        Ticket ticket = ticketPort.findByUuid(event.ticketUuid())
                .orElseThrow(() -> new IllegalStateException("티켓 없음: " + event.ticketUuid()));

        // 4. Redis 좌석 결제 확정 (held → paid)
        seatPort.paySeat(ticket.getSeatNumber(), ticket.getQueueToken());

        // 5. DB 티켓 상태를 SYNCED로 변경
        ticket.sync();
        ticketPort.save(ticket);

        // 6. active 유저에서 제거
        activeUserPort.deactivate(ticket.getQueueToken());
    }
}
