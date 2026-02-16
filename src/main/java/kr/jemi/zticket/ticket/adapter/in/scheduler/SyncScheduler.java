package kr.jemi.zticket.ticket.adapter.in.scheduler;

import kr.jemi.zticket.ticket.application.port.in.SyncTicketUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncTicketUseCase syncTicketUseCase;

    public SyncScheduler(SyncTicketUseCase syncTicketUseCase) {
        this.syncTicketUseCase = syncTicketUseCase;
    }

    @Scheduled(fixedDelayString = "${zticket.sync.interval-ms}")
    public void sync() {
        try {
            syncTicketUseCase.syncPaidTickets();
        } catch (Exception e) {
            log.error("티켓 동기화 스케줄러 실패", e);
        }
    }
}
