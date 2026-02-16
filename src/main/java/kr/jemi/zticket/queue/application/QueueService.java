package kr.jemi.zticket.queue.application;

import kr.jemi.zticket.queue.application.port.in.AdmitUsersUseCase;
import kr.jemi.zticket.queue.application.port.in.EnterQueueUseCase;
import kr.jemi.zticket.queue.application.port.in.GetQueueStatusUseCase;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.queue.application.port.out.WaitingQueuePort;
import kr.jemi.zticket.queue.domain.QueueToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QueueService implements EnterQueueUseCase, GetQueueStatusUseCase, AdmitUsersUseCase {

    private final WaitingQueuePort waitingQueuePort;
    private final ActiveUserPort activeUserPort;
    private final long activeTtlSeconds;
    private final int maxActiveUsers;

    public QueueService(WaitingQueuePort waitingQueuePort,
                        ActiveUserPort activeUserPort,
                        @Value("${zticket.admission.active-ttl-seconds}") long activeTtlSeconds,
                        @Value("${zticket.admission.max-active-users}") int maxActiveUsers) {
        this.waitingQueuePort = waitingQueuePort;
        this.activeUserPort = activeUserPort;
        this.activeTtlSeconds = activeTtlSeconds;
        this.maxActiveUsers = maxActiveUsers;
    }

    @Override
    public QueueToken enter() {
        String uuid = UUID.randomUUID().toString();
        long rank = waitingQueuePort.enqueue(uuid);
        return new QueueToken(uuid, rank);
    }

    @Override
    public QueueToken getStatus(String uuid) {
        if (activeUserPort.isActive(uuid)) {
            return new QueueToken(uuid, 0);
        }
        Long rank = waitingQueuePort.getRank(uuid);
        if (rank == null) {
            return new QueueToken(uuid, -1);
        }
        return new QueueToken(uuid, rank);
    }

    @Override
    public void admitBatch(int batchSize) {
        // 현재 active 유저 수를 확인하고 빈 슬롯만큼만 입장
        long currentActive = activeUserPort.countActive();
        int slotsAvailable = (int) Math.max(0, maxActiveUsers - currentActive);
        int toAdmit = Math.min(batchSize, slotsAvailable);

        if (toAdmit <= 0) {
            return;
        }

        // 1. peek: 큐에서 조회만 (삭제 안 함 → crash 시 유실 없음)
        List<String> candidates = waitingQueuePort.peekBatch(toAdmit);
        if (candidates.isEmpty()) {
            return;
        }

        // 2. activate: active_user 키 생성 (멱등 — 재실행해도 TTL만 갱신)
        for (String uuid : candidates) {
            activeUserPort.activate(uuid, activeTtlSeconds);
        }

        // 3. remove: activate 완료 후에야 큐에서 제거
        waitingQueuePort.removeBatch(candidates);
    }
}
