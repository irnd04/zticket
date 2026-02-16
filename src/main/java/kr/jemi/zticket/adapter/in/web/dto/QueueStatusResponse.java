package kr.jemi.zticket.adapter.in.web.dto;

import kr.jemi.zticket.domain.queue.QueueToken;

public record QueueStatusResponse(String uuid, long rank, String status) {

    public static QueueStatusResponse from(QueueToken token) {
        String status;
        if (token.rank() == 0) status = "ACTIVE";
        else if (token.rank() < 0) status = "EXPIRED";
        else status = "WAITING";
        return new QueueStatusResponse(token.uuid(), token.rank(), status);
    }
}
