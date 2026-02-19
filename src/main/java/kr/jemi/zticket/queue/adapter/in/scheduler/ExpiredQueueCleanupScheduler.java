package kr.jemi.zticket.queue.adapter.in.scheduler;

import kr.jemi.zticket.queue.application.port.in.RemoveExpiredUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredQueueCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiredQueueCleanupScheduler.class);

    private final RemoveExpiredUseCase removeExpiredUseCase;

    public ExpiredQueueCleanupScheduler(RemoveExpiredUseCase removeExpiredUseCase) {
        this.removeExpiredUseCase = removeExpiredUseCase;
    }

    @Scheduled(fixedDelayString = "${zticket.admission.cleanup-interval-ms}")
    public void cleanup() {
        try {
            long removed = removeExpiredUseCase.removeExpired();
            if (removed > 0) {
                log.info("잠수 유저 {} 명 제거", removed);
            }
        } catch (Exception e) {
            log.error("잠수 유저 제거 실패", e);
        }
    }
}
