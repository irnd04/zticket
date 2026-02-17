package kr.jemi.zticket.queue.application.port.in;

import kr.jemi.zticket.queue.domain.QueueToken;

public interface GetQueueTokenUseCase {

    QueueToken getQueueToken(String uuid);
}
