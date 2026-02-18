package kr.jemi.zticket.queue.application;

import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.queue.application.port.in.AdmitUsersUseCase;
import kr.jemi.zticket.queue.application.port.in.EnterQueueUseCase;
import kr.jemi.zticket.queue.application.port.in.GetQueueTokenUseCase;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.queue.application.port.out.WaitingQueuePort;
import kr.jemi.zticket.queue.domain.QueueToken;
import kr.jemi.zticket.seat.application.SeatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QueueService implements EnterQueueUseCase, GetQueueTokenUseCase, AdmitUsersUseCase {

    private final WaitingQueuePort waitingQueuePort;
    private final ActiveUserPort activeUserPort;
    private final SeatService seatService;
    private final long activeTtlSeconds;
    private final int maxActiveUsers;
    private final long queueTtlMs;

    public QueueService(WaitingQueuePort waitingQueuePort,
                        ActiveUserPort activeUserPort,
                        SeatService seatService,
                        @Value("${zticket.admission.active-ttl-seconds}") long activeTtlSeconds,
                        @Value("${zticket.admission.max-active-users}") int maxActiveUsers,
                        @Value("${zticket.admission.queue-ttl-seconds}") long queueTtlSeconds) {
        this.waitingQueuePort = waitingQueuePort;
        this.activeUserPort = activeUserPort;
        this.seatService = seatService;
        this.activeTtlSeconds = activeTtlSeconds;
        this.maxActiveUsers = maxActiveUsers;
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
    public void admitBatch() {
        // 폴링하지 않는 유령 유저 제거 (score < now - waitingTtl)
        waitingQueuePort.removeExpired(System.currentTimeMillis() - queueTtlMs);

        // 현재 active 유저 수를 확인하고 빈 슬롯만큼만 입장
        long currentActive = activeUserPort.countActive();
        int availableSlots = (int) Math.max(0, maxActiveUsers - currentActive);

        // 잔여 좌석보다 많이 입장시켜도 의미 없음
        int remainingSeats = (int) seatService.getAvailableCountNoCache();
        int toAdmit = Math.min(availableSlots, remainingSeats);

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
