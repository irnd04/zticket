package kr.jemi.zticket.queue.adapter.in.web.dto;

import kr.jemi.zticket.queue.domain.QueueToken;

public record QueueStatusResponse(String uuid, long rank, String status) {

    public static QueueStatusResponse from(QueueToken token) {
        return new QueueStatusResponse(token.uuid(), token.rank(), token.status().name());
    }
}
