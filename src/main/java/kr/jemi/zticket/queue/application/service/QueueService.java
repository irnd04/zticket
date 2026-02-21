package kr.jemi.zticket.queue.application.service;

import io.hypersistence.tsid.TSID;
import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.queue.application.port.in.EnterQueueUseCase;
import kr.jemi.zticket.queue.application.port.in.GetQueueTokenUseCase;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.queue.application.port.out.AvailableSeatCountPort;
import kr.jemi.zticket.queue.domain.QueueToken;
import org.springframework.stereotype.Service;

@Service
public class QueueService implements EnterQueueUseCase, GetQueueTokenUseCase {

    private final WaitingQueueOperator waitingQueueOperator;
    private final ActiveUserPort activeUserPort;
    private final AvailableSeatCountPort availableSeatCountPort;
    private final TSID.Factory tsidFactory;

    public QueueService(WaitingQueueOperator waitingQueueOperator,
                        ActiveUserPort activeUserPort,
                        AvailableSeatCountPort availableSeatCountPort,
                        TSID.Factory tsidFactory) {
        this.waitingQueueOperator = waitingQueueOperator;
        this.activeUserPort = activeUserPort;
        this.availableSeatCountPort = availableSeatCountPort;
        this.tsidFactory = tsidFactory;
    }

    @Override
    public QueueToken enter() {
        if (availableSeatCountPort.getAvailableCount() <= 0) {
            throw new BusinessException(ErrorCode.SOLD_OUT);
        }
        String token = tsidFactory.generate().encode(62);
        long rank = waitingQueueOperator.enqueue(token);
        return QueueToken.waiting(token, rank);
    }

    @Override
    public QueueToken getQueueToken(String token) {
        if (activeUserPort.isActive(token)) {
            return QueueToken.active(token);
        }
        if (availableSeatCountPort.getAvailableCount() <= 0) {
            return QueueToken.soldOut(token);
        }
        Long rank = waitingQueueOperator.getRank(token);
        if (rank == null) {
            throw new BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
        }
        waitingQueueOperator.refresh(token);
        return QueueToken.waiting(token, rank);
    }
}
