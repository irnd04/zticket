package kr.jemi.zticket.application.port.in;

import kr.jemi.zticket.domain.queue.QueueToken;

public interface GetQueueStatusUseCase {

    QueueToken getStatus(String uuid);
}
