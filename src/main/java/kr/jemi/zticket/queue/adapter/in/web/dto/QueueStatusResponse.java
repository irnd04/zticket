package kr.jemi.zticket.queue.adapter.in.web.dto;

import kr.jemi.zticket.queue.domain.QueueToken;

public record QueueStatusResponse(String token, long rank, String status) {

    public static QueueStatusResponse from(QueueToken queueToken) {
        return new QueueStatusResponse(queueToken.token(), queueToken.rank(), queueToken.status().name());
    }
}
