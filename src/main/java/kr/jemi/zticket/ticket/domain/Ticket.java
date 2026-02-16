package kr.jemi.zticket.ticket.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public class Ticket {

    private final Long id;
    private final String uuid;
    private final int seatNumber;
    private TicketStatus status;
    private final String queueToken;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Ticket(Long id, String uuid, int seatNumber, TicketStatus status, String queueToken,
                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.uuid = uuid;
        this.seatNumber = seatNumber;
        this.status = status;
        this.queueToken = queueToken;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Ticket create(String queueToken, int seatNumber) {
        return new Ticket(null, UUID.randomUUID().toString(), seatNumber, TicketStatus.PAID, queueToken,
                LocalDateTime.now(), null);
    }

    public void sync() {
        if (this.status != TicketStatus.PAID) {
            throw new IllegalStateException(
                    "PAID 상태에서만 동기화할 수 있습니다. 현재: " + this.status);
        }
        this.status = TicketStatus.SYNCED;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
