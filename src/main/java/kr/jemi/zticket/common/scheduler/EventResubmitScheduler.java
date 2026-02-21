package kr.jemi.zticket.common.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class EventResubmitScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventResubmitScheduler.class);

    private final IncompleteEventPublications incompleteEventPublications;

    public EventResubmitScheduler(IncompleteEventPublications incompleteEventPublications) {
        this.incompleteEventPublications = incompleteEventPublications;
    }

    @Scheduled(cron = "${zticket.event-resubmit.cron}")
    @SchedulerLock(name = "resubmitIncompleteEvents",
            lockAtMostFor = "${zticket.event-resubmit.lock-at-most-for}",
            lockAtLeastFor = "${zticket.event-resubmit.lock-at-least-for}")
    public void resubmitIncompleteEvents() {
        try {
            incompleteEventPublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5));
        } catch (Exception e) {
            log.error("미완료 이벤트 재발행 스케줄러 실패", e);
        }
    }
}
