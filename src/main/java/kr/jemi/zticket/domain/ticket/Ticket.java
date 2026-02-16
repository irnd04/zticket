package kr.jemi.zticket.domain.ticket;

import java.util.UUID;

public class Ticket {

    private final String uuid;
    private final int seatNumber;
    private TicketStatus status;
    private final String queueToken;

    public Ticket(String uuid, int seatNumber, TicketStatus status, String queueToken) {
        this.uuid = uuid;
        this.seatNumber = seatNumber;
        this.status = status;
        this.queueToken = queueToken;
    }

    public static Ticket create(String queueToken, int seatNumber) {
        return new Ticket(UUID.randomUUID().toString(), seatNumber, TicketStatus.PAID, queueToken);
    }

    public void sync() {
        if (this.status != TicketStatus.PAID) {
            throw new IllegalStateException(
                    "PAID 상태에서만 동기화할 수 있습니다. 현재: " + this.status);
        }
        this.status = TicketStatus.SYNCED;
    }

    public String getUuid() {
        return uuid;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public String getQueueToken() {
        return queueToken;
    }
}
