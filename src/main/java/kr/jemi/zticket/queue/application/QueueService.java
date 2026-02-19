package kr.jemi.zticket.queue.application;

import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.queue.application.port.in.AdmitUsersUseCase;
import kr.jemi.zticket.queue.application.port.in.EnterQueueUseCase;
import kr.jemi.zticket.queue.application.port.in.GetQueueTokenUseCase;
import kr.jemi.zticket.queue.application.port.in.RemoveExpiredUseCase;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.queue.domain.QueueToken;
import kr.jemi.zticket.seat.application.SeatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QueueService implements EnterQueueUseCase, GetQueueTokenUseCase, AdmitUsersUseCase, RemoveExpiredUseCase {

    private final WaitingQueueOperator waitingQueueOperator;
    private final ActiveUserPort activeUserPort;
    private final SeatService seatService;
    private final long activeTtlSeconds;
    private final int maxActiveUsers;
    private final int batchSize;

    public QueueService(WaitingQueueOperator waitingQueueOperator,
                        ActiveUserPort activeUserPort,
                        SeatService seatService,
                        @Value("${zticket.admission.active-ttl-seconds}") long activeTtlSeconds,
                        @Value("${zticket.admission.max-active-users}") int maxActiveUsers,
                        @Value("${zticket.admission.batch-size}") int batchSize) {
        this.waitingQueueOperator = waitingQueueOperator;
        this.activeUserPort = activeUserPort;
        this.seatService = seatService;
        this.activeTtlSeconds = activeTtlSeconds;
        this.maxActiveUsers = maxActiveUsers;
        this.batchSize = batchSize;
    }

    @Override
    public QueueToken enter() {
        if (seatService.getAvailableCount() <= 0) {
            throw new BusinessException(ErrorCode.SOLD_OUT);
        }
        String uuid = UUID.randomUUID().toString();
        long rank = waitingQueueOperator.enqueue(uuid);
        return QueueToken.waiting(uuid, rank);
    }

    @Override
    public QueueToken getQueueToken(String uuid) {
        if (activeUserPort.isActive(uuid)) {
            return QueueToken.active(uuid);
        }
        if (seatService.getAvailableCount() <= 0) {
            return QueueToken.soldOut(uuid);
        }
        Long rank = waitingQueueOperator.getRank(uuid);
        if (rank == null) {
            throw new BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
        }
        waitingQueueOperator.refresh(uuid);
        return QueueToken.waiting(uuid, rank);
    }

    @Override
    public long removeExpired() {
        List<String> expiredUuids = waitingQueueOperator.findExpired();
        if (!expiredUuids.isEmpty()) {
            waitingQueueOperator.removeAll(expiredUuids);
        }
        return expiredUuids.size();
    }

    @Override
    public void admitBatch() {
        int currentActive = activeUserPort.countActive();
        int availableSlots = Math.max(0, maxActiveUsers - currentActive);

        int remainingSeats = seatService.getAvailableCount();
        int toAdmit = Math.min(batchSize, Math.min(availableSlots, Math.max(0, remainingSeats - currentActive)));

        if (toAdmit <= 0) {
            return;
        }

        // 1. peek: FIFO 순서로 2배 후보 조회 → heartbeat 필터링 (삭제 안 함 → crash 시 유실 없음)
        List<String> uuids = waitingQueueOperator.peekAlive(toAdmit);

        if (uuids.isEmpty()) {
            return;
        }

        // 2. activate: active_user 키 생성 (파이프라이닝, 멱등 — 재실행해도 TTL만 갱신)
        activeUserPort.activateBatch(uuids, activeTtlSeconds);

        // 3. remove: activate 완료 후에야 큐에서 제거
        waitingQueueOperator.removeAll(uuids);
    }
}
