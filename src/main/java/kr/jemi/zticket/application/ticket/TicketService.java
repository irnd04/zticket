package kr.jemi.zticket.application.ticket;

import kr.jemi.zticket.application.port.in.GetSeatsUseCase;
import kr.jemi.zticket.application.port.in.PurchaseTicketUseCase;
import kr.jemi.zticket.application.port.out.ActiveUserPort;
import kr.jemi.zticket.application.port.out.SeatHoldPort;
import kr.jemi.zticket.application.port.out.TicketPersistencePort;
import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.domain.ticket.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class TicketService implements PurchaseTicketUseCase, GetSeatsUseCase {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final SeatHoldPort seatHoldPort;
    private final TicketPersistencePort ticketPersistencePort;
    private final ActiveUserPort activeUserPort;
    private final long holdTtlSeconds;

    public TicketService(SeatHoldPort seatHoldPort,
                         TicketPersistencePort ticketPersistencePort,
                         ActiveUserPort activeUserPort,
                         @Value("${zticket.seat.hold-ttl-seconds}") long holdTtlSeconds) {
        this.seatHoldPort = seatHoldPort;
        this.ticketPersistencePort = ticketPersistencePort;
        this.activeUserPort = activeUserPort;
        this.holdTtlSeconds = holdTtlSeconds;
    }

    @Override
    public Ticket purchase(String queueToken, int seatNumber) {
        // 1. 활성 사용자 검증
        if (!activeUserPort.isActive(queueToken)) {
            throw new BusinessException(ErrorCode.NOT_ACTIVE_USER);
        }

        // 2. Redis 좌석 선점 (SET NX EX)
        boolean held = seatHoldPort.holdSeat(seatNumber, queueToken, holdTtlSeconds);
        if (!held) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_HELD);
        }

        // 3. DB에 PAID 티켓 저장 (결제 완료)
        Ticket ticket = Ticket.create(queueToken, seatNumber);
        try {
            ticketPersistencePort.save(ticket);
        } catch (Exception e) {
            // 롤백: Redis 좌석 해제
            log.error("DB 저장 실패, 좌석 해제: {}", seatNumber, e);
            seatHoldPort.releaseSeat(seatNumber);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        // 4. Redis 좌석 결제 확정 (held → paid + PERSIST)
        boolean paid = seatHoldPort.paySeat(seatNumber, queueToken);
        if (!paid) {
            log.error("좌석 결제 확정 실패, 복구 워커가 처리 예정: ticketUuid={}", ticket.getUuid());
            return ticket;
        }

        // 5. DB 티켓 상태를 SYNCED로 변경
        ticket.sync();
        ticketPersistencePort.save(ticket);

        return ticket;
    }

    @Override
    public Map<Integer, String> getAllSeatStatuses(int totalSeats) {
        List<Integer> allSeats = IntStream.rangeClosed(1, totalSeats)
                .boxed()
                .toList();
        Map<Integer, String> redisStatuses = seatHoldPort.getStatuses(allSeats);
        Map<Integer, String> result = new LinkedHashMap<>();
        for (int seat = 1; seat <= totalSeats; seat++) {
            String value = redisStatuses.get(seat);
            if (value == null) {
                result.put(seat, "available");
            } else if (value.startsWith("held:")) {
                result.put(seat, "held");
            } else if (value.startsWith("paid:")) {
                result.put(seat, "paid");
            } else {
                result.put(seat, "unknown");
            }
        }
        return result;
    }
}
