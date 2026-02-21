package kr.jemi.zticket.queue.infrastructure.in.scheduler;

import kr.jemi.zticket.queue.application.port.in.AdmitUsersUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AdmissionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AdmissionScheduler.class);

    private final AdmitUsersUseCase admitUsersUseCase;

    public AdmissionScheduler(AdmitUsersUseCase admitUsersUseCase) {
        this.admitUsersUseCase = admitUsersUseCase;
    }

    @Scheduled(cron = "${zticket.admission.cron}")
    @SchedulerLock(name = "admit",
            lockAtMostFor = "${zticket.admission.lock-at-most-for}",
            lockAtLeastFor = "${zticket.admission.lock-at-least-for}")
    public void admit() {
        try {
            admitUsersUseCase.admitBatch();
        } catch (Exception e) {
            log.error("입장 배치 처리 실패", e);
        }
    }
}
