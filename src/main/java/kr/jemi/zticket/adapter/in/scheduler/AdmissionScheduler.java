package kr.jemi.zticket.adapter.in.scheduler;

import kr.jemi.zticket.application.port.in.AdmitUsersUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AdmissionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AdmissionScheduler.class);

    private final AdmitUsersUseCase admitUsersUseCase;
    private final int batchSize;

    public AdmissionScheduler(AdmitUsersUseCase admitUsersUseCase,
                              @Value("${zticket.admission.batch-size}") int batchSize) {
        this.admitUsersUseCase = admitUsersUseCase;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${zticket.admission.interval-ms}")
    public void admit() {
        try {
            admitUsersUseCase.admitBatch(batchSize);
        } catch (Exception e) {
            log.error("입장 배치 처리 실패", e);
        }
    }
}
