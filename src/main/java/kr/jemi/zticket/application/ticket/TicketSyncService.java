package kr.jemi.zticket.application.ticket;

import kr.jemi.zticket.application.port.in.SyncTicketUseCase;
import kr.jemi.zticket.application.port.out.SeatHoldPort;
import kr.jemi.zticket.application.port.out.TicketPersistencePort;
import kr.jemi.zticket.domain.ticket.Ticket;
import kr.jemi.zticket.domain.ticket.TicketStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TicketSyncService implements SyncTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(TicketSyncService.class);

    private final TicketPersistencePort ticketPersistencePort;
    private final SeatHoldPort seatHoldPort;

    public TicketSyncService(TicketPersistencePort ticketPersistencePort,
                             SeatHoldPort seatHoldPort) {
        this.ticketPersistencePort = ticketPersistencePort;
        this.seatHoldPort = seatHoldPort;
    }

    @Override
    public void syncPaidTickets() {
        List<Ticket> paidTickets = ticketPersistencePort.findByStatus(TicketStatus.PAID);

        for (Ticket ticket : paidTickets) {
            try {
                syncSingle(ticket);
            } catch (Exception e) {
                log.error("티켓 동기화 실패 {}: {}", ticket.getUuid(), e.getMessage(), e);
            }
        }

        if (!paidTickets.isEmpty()) {
            log.info("동기화 완료. PAID 티켓 {}건 처리", paidTickets.size());
        }
    }

    /*
     * DB(PAID) → Redis(paid:{token}) 동기화 후 DB 상태를 SYNCED로 전환
     * Redis 키가 held/null 어떤 상태든 paid로 덮어씀 (DB가 source of truth)
     *
     * 발생 케이스: TicketService.purchase() 절차 중
     *   1. Redis hold → 2. DB save(PAID) → [여기서 서버 다운] → 3. Redis paid 전환 실패
     *   DB에는 PAID가 남아있지만 Redis는 held(TTL 째깍) 또는 null(TTL 만료) 상태
     *
     * TTL 만료 시 다른 사용자가 같은 좌석을 Redis에서 hold할 수 있지만,
     * DB tickets 테이블에 seatNumber UNIQUE 제약이 걸려있어 해당 사용자의 DB 저장이 실패함.
     * 따라서 setPaidSeat으로 덮어써도 안전함.
     */
    private void syncSingle(Ticket ticket) {
        seatHoldPort.setPaidSeat(ticket.getSeatNumber(), ticket.getQueueToken());
        ticket.sync();
        ticketPersistencePort.save(ticket);
        log.info("티켓 동기화 완료: {}", ticket.getUuid());
    }
}
