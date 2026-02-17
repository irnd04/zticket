package kr.jemi.zticket.seat.domain;

public record Seat(SeatStatus status, String owner) {

    public Seat {
        if (status == SeatStatus.AVAILABLE && owner != null) {
            throw new IllegalArgumentException("AVAILABLE 좌석은 소유자가 없어야 합니다");
        }
        if (status != SeatStatus.AVAILABLE && (owner == null || owner.isBlank())) {
            throw new IllegalArgumentException(status + " 좌석은 소유자가 반드시 있어야 합니다");
        }
    }

    /**
     * 주어진 토큰의 사용자에게 결제 가능한 좌석인지 판단한다.
     * token이 null이면 소유자 비교 없이 AVAILABLE만 결제 가능으로 본다.
     */
    public boolean isAvailableFor(String token) {
        if (status == SeatStatus.AVAILABLE) {
            return true;
        }
        if (status == SeatStatus.HELD && token != null && token.equals(owner)) {
            return true;
        }
        return false;
    }
}
