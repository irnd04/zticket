package kr.jemi.zticket.queue.application;

import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.queue.application.port.in.AdmitUsersUseCase;
import kr.jemi.zticket.queue.application.port.in.EnterQueueUseCase;
import kr.jemi.zticket.queue.application.port.in.GetQueueTokenUseCase;
import kr.jemi.zticket.queue.application.port.in.RemoveExpiredUseCase;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.queue.application.port.out.WaitingQueuePort;
import kr.jemi.zticket.queue.domain.QueueToken;
import kr.jemi.zticket.seat.application.SeatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QueueService implements EnterQueueUseCase, GetQueueTokenUseCase, AdmitUsersUseCase, RemoveExpiredUseCase {

    private final WaitingQueuePort waitingQueuePort;
    private final ActiveUserPort activeUserPort;
    private final SeatService seatService;
    private final long activeTtlSeconds;
    private final int maxActiveUsers;
    private final int batchSize;
    private final long queueTtlMs;

    public QueueService(WaitingQueuePort waitingQueuePort,
                        ActiveUserPort activeUserPort,
                        SeatService seatService,
                        @Value("${zticket.admission.active-ttl-seconds}") long activeTtlSeconds,
                        @Value("${zticket.admission.max-active-users}") int maxActiveUsers,
                        @Value("${zticket.admission.batch-size}") int batchSize,
                        @Value("${zticket.admission.queue-ttl-seconds}") long queueTtlSeconds) {
        this.waitingQueuePort = waitingQueuePort;
        this.activeUserPort = activeUserPort;
        this.seatService = seatService;
        this.activeTtlSeconds = activeTtlSeconds;
        this.maxActiveUsers = maxActiveUsers;
        this.batchSize = batchSize;
        this.queueTtlMs = queueTtlSeconds * 1000;
    }

    @Override
    public QueueToken enter() {
        if (seatService.getAvailableCount() <= 0) {
            throw new BusinessException(ErrorCode.SOLD_OUT);
        }
        String uuid = UUID.randomUUID().toString();
        long rank = waitingQueuePort.enqueue(uuid);
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
        Long rank = waitingQueuePort.getRank(uuid);
        if (rank == null) {
            throw new BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
        }
        waitingQueuePort.refreshScore(uuid);
        return QueueToken.waiting(uuid, rank);
    }

    @Override
    public long removeExpired() {
        return waitingQueuePort.removeExpired(System.currentTimeMillis() - queueTtlMs);
    }

    @Override
    public void admitBatch() {
        // 현재 active 유저 수를 확인하고 빈 슬롯만큼만 입장
        int currentActive = activeUserPort.countActive();
        int availableSlots = Math.max(0, maxActiveUsers - currentActive);

        // active 유저가 구매할 좌석을 보수적으로 차감
        int remainingSeats = seatService.getAvailableCount();
        int toAdmit = Math.min(batchSize, Math.min(availableSlots, Math.max(0, remainingSeats - currentActive)));

        if (toAdmit <= 0) {
            return;
        }

        // 1. peek: heartbeat 기준으로 살아있는 유저만 조회 (삭제 안 함 → crash 시 유실 없음)
        long cutoff = System.currentTimeMillis() - queueTtlMs;
        List<String> candidates = waitingQueuePort.peekBatch(toAdmit, cutoff);
        if (candidates.isEmpty()) {
            return;
        }

        // 2. activate: active_user 키 생성 (파이프라이닝, 멱등 — 재실행해도 TTL만 갱신)
        activeUserPort.activateBatch(candidates, activeTtlSeconds);

        // 3. remove: activate 완료 후에야 큐에서 제거
        waitingQueuePort.removeBatch(candidates);
    }
}
