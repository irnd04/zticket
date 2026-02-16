package kr.jemi.zticket.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    QUEUE_ALREADY_ENTERED(409, "이미 대기열에 진입했습니다"),
    QUEUE_TOKEN_NOT_FOUND(404, "대기열 토큰을 찾을 수 없습니다"),
    NOT_ACTIVE_USER(403, "입장이 허용되지 않은 사용자입니다"),
    SEAT_ALREADY_HELD(409, "이미 선점된 좌석이 포함되어 있습니다"),
    SEAT_HOLD_FAILED(500, "좌석 선점에 실패했습니다"),
    SEAT_CONFIRM_FAILED(500, "좌석 확정에 실패했습니다"),
    TICKET_NOT_FOUND(404, "티켓을 찾을 수 없습니다"),
    INVALID_SEAT_NUMBERS(400, "유효하지 않은 좌석 번호입니다"),
    INTERNAL_ERROR(500, "내부 서버 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(int statusCode, String message) {
        this.status = HttpStatus.valueOf(statusCode);
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
