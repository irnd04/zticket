package kr.jemi.zticket.ticket.application;

import kr.jemi.zticket.ticket.application.port.in.PurchaseTicketUseCase;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.seat.application.port.out.SeatPort;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketPaidEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;


@Service
public class TicketService implements PurchaseTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final SeatPort seatPort;
    private final TicketPort ticketPort;
    private final ActiveUserPort activeUserPort;
    private final ApplicationEventPublisher eventPublisher;
    private final long holdTtlSeconds;

    public TicketService(SeatPort seatPort,
                         TicketPort ticketPort,
                         ActiveUserPort activeUserPort,
                         ApplicationEventPublisher eventPublisher,
                         @Value("${zticket.seat.hold-ttl-seconds}") long holdTtlSeconds) {
        this.seatPort = seatPort;
        this.ticketPort = ticketPort;
        this.activeUserPort = activeUserPort;
        this.eventPublisher = eventPublisher;
        this.holdTtlSeconds = holdTtlSeconds;
    }

    @Override
    public Ticket purchase(String queueToken, int seatNumber) {
        // 1. 활성 사용자 검증
        if (!activeUserPort.isActive(queueToken)) {
            throw new BusinessException(ErrorCode.NOT_ACTIVE_USER);
        }

        // 2. Redis 좌석 선점 (SET NX EX)
        boolean held = seatPort.holdSeat(seatNumber, queueToken, holdTtlSeconds);
        if (!held) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_HELD);
        }

        // 3. DB에 PAID 티켓 저장 (결제 완료)
        Ticket ticket = Ticket.create(queueToken, seatNumber);
        try {
            ticket = ticketPort.save(ticket);
        } catch (Exception e) {
            // 롤백: Redis 좌석 해제
            log.error("DB 저장 실패, 좌석 해제: {}", seatNumber, e);
            seatPort.releaseSeat(seatNumber);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        // 4~6. 비동기 후처리 (Redis paid 전환, DB SYNCED, active 유저 제거)
        eventPublisher.publishEvent(new TicketPaidEvent(ticket.getUuid()));

        return ticket;
    }
}
