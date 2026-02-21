package kr.jemi.zticket.queue.application.service;

import kr.jemi.zticket.queue.application.port.in.AdmitUsersUseCase;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.queue.application.port.out.AvailableSeatCountPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdmissionService implements AdmitUsersUseCase {

    private final WaitingQueueOperator waitingQueueOperator;
    private final ActiveUserPort activeUserPort;
    private final AvailableSeatCountPort availableSeatCountPort;
    private final long activeTtlSeconds;
    private final int maxActiveUsers;
    private final int batchSize;

    public AdmissionService(WaitingQueueOperator waitingQueueOperator,
                            ActiveUserPort activeUserPort,
                            AvailableSeatCountPort availableSeatCountPort,
                            @Value("${zticket.admission.active-ttl-seconds}") long activeTtlSeconds,
                            @Value("${zticket.admission.max-active-users}") int maxActiveUsers,
                            @Value("${zticket.admission.batch-size}") int batchSize) {
        this.waitingQueueOperator = waitingQueueOperator;
        this.activeUserPort = activeUserPort;
        this.availableSeatCountPort = availableSeatCountPort;
        this.activeTtlSeconds = activeTtlSeconds;
        this.maxActiveUsers = maxActiveUsers;
        this.batchSize = batchSize;
    }

    @Override
    public void admitBatch() {
        // 1. removeExpired: 잠수 유저 제거
        final int findExpiredBatchSize = 5000;
        while (true) {
            List<String> expiredTokens = waitingQueueOperator.findExpired(findExpiredBatchSize);
            waitingQueueOperator.removeAll(expiredTokens);

            if (expiredTokens.size() < findExpiredBatchSize) {
                break;
            }
        }

        // 2. 입장 인원 계산
        int currentActive = activeUserPort.countActive();
        int availableSlots = Math.max(0, maxActiveUsers - currentActive);

        int remainingSeats = availableSeatCountPort.getAvailableCount();
        int toAdmit = Math.min(batchSize, Math.min(availableSlots, Math.max(0, remainingSeats - currentActive)));

        if (toAdmit <= 0) {
            return;
        }

        // 3. peek: 잠수 유저 제거 후이므로 단순 FIFO 조회
        List<String> tokens = waitingQueueOperator.peek(toAdmit);
        if (tokens.isEmpty()) {
            return;
        }

        // 4. activate: active_user 키 생성 (파이프라이닝, 멱등 — 재실행해도 TTL만 갱신)
        activeUserPort.activateBatch(tokens, activeTtlSeconds);

        // 5. remove: activate 완료 후에야 큐에서 제거
        waitingQueueOperator.removeAll(tokens);
    }
}
