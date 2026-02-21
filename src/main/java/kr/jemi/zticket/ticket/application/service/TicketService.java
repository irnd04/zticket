package kr.jemi.zticket.ticket.application.service;

import io.hypersistence.tsid.TSID;
import kr.jemi.zticket.ticket.application.port.in.PurchaseTicketUseCase;
import kr.jemi.zticket.ticket.application.port.out.ActiveUserCheckPort;
import kr.jemi.zticket.ticket.application.port.out.SeatHoldPort;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.ticket.domain.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class TicketService implements PurchaseTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final SeatHoldPort seatHoldPort;
    private final TicketPort ticketPort;
    private final ActiveUserCheckPort activeUserCheckPort;
    private final ApplicationEventPublisher eventPublisher;
    private final TSID.Factory tsidFactory;
    private final long holdTtlSeconds;

    public TicketService(SeatHoldPort seatHoldPort,
                         TicketPort ticketPort,
                         ActiveUserCheckPort activeUserCheckPort,
                         ApplicationEventPublisher eventPublisher,
                         TSID.Factory tsidFactory,
                         @Value("${zticket.seat.hold-ttl-seconds}") long holdTtlSeconds) {
        this.seatHoldPort = seatHoldPort;
        this.ticketPort = ticketPort;
        this.activeUserCheckPort = activeUserCheckPort;
        this.eventPublisher = eventPublisher;
        this.tsidFactory = tsidFactory;
        this.holdTtlSeconds = holdTtlSeconds;
    }

    @Override
    public Ticket purchase(String queueToken, int seatNumber) {
        // 1. 활성 사용자 검증
        if (!activeUserCheckPort.isActive(queueToken)) {
            throw new BusinessException(ErrorCode.NOT_ACTIVE_USER);
        }

        // 2. Redis 좌석 선점 (SET NX EX)
        boolean held = seatHoldPort.holdSeat(seatNumber, queueToken, holdTtlSeconds);
        if (!held) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_HELD);
        }

        // 3. DB에 PAID 티켓 저장 (결제 완료)
        long id = tsidFactory.generate().toLong();
        Ticket ticket = Ticket.create(id, queueToken, seatNumber);
        List<Object> events = ticket.pullEvents();
        try {
            ticket = ticketPort.insert(ticket);
        } catch (Exception e) {
            // 롤백: Redis 좌석 해제
            log.error("DB 저장 실패, 좌석 해제: {}", seatNumber, e);
            seatHoldPort.releaseSeat(seatNumber, queueToken);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        // 4~6. 비동기 후처리 (Redis paid 전환, DB SYNCED, active 유저 제거)
        events.forEach(eventPublisher::publishEvent);

        return ticket;
    }
}
