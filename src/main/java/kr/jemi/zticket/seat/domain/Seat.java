package kr.jemi.zticket.seat.domain;

import jakarta.validation.constraints.NotNull;
import kr.jemi.zticket.common.validation.SelfValidating;

public record Seat(@NotNull SeatStatus status, String owner) implements SelfValidating {

    public Seat(SeatStatus status, String owner) {
        this.status = status;
        this.owner = owner;
        validate();
    }

    private void validate() {
        validateSelf();
        switch (status) {
            case AVAILABLE -> {
                if (owner != null) {
                    throw new IllegalArgumentException("AVAILABLE 좌석은 소유자가 없어야 합니다");
                }
            }
            case HELD, PAID -> {
                if (owner == null || owner.isBlank()) {
                    throw new IllegalArgumentException(status + " 좌석은 소유자가 반드시 있어야 합니다");
                }
            }
        }
    }

    /**
     * 주어진 토큰의 사용자에게 결제 가능한 좌석인지 판단한다.
     * token이 null이면 소유자 비교 없이 AVAILABLE만 결제 가능으로 본다.
     */
    public boolean isAvailableFor(String token) {
        return switch (status) {
            case AVAILABLE -> true;
            case HELD -> token != null && token.equals(owner);
            case PAID -> false;
        };
    }
}
