package kr.jemi.zticket.common.dto;

import kr.jemi.zticket.common.exception.ErrorCode;

public record ErrorResponse(int status, String code, String message) {

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getStatus().value(),
                errorCode.name(),
                errorCode.getMessage()
        );
    }
}
