package kr.jemi.zticket.ticket.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import kr.jemi.zticket.common.validation.SelfValidating;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Ticket implements SelfValidating {

    private final long id;
    @Min(1)
    private final int seatNumber;
    @NotNull
    private TicketStatus status;
    @NotNull
    private final String queueToken;
    @NotNull
    private final LocalDateTime createdAt;
    @NotNull
    private LocalDateTime updatedAt;

    private final List<Object> events = new ArrayList<>();

    public Ticket(long id, int seatNumber, TicketStatus status, String queueToken,
                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.seatNumber = seatNumber;
        this.status = status;
        this.queueToken = queueToken;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        validateSelf();
    }

    public static Ticket create(long id, String queueToken, int seatNumber) {
        LocalDateTime now = java.time.LocalDateTime.now();
        Ticket ticket = new Ticket(id, seatNumber, TicketStatus.PAID, queueToken,
                now, now);
        ticket.registerEvent(new TicketPaidEvent(id));
        return ticket;
    }

    private void registerEvent(Object event) {
        events.add(event);
    }

    public List<Object> pullEvents() {
        List<Object> result = List.copyOf(events);
        events.clear();
        return result;
    }

    public void sync() {
        if (this.status == TicketStatus.SYNCED) {
            return;
        }
        if (this.status != TicketStatus.PAID) {
            throw new IllegalStateException(
                    "PAID 상태에서만 동기화할 수 있습니다. 현재: " + this.status);
        }
        this.status = TicketStatus.SYNCED;
        this.updatedAt = LocalDateTime.now();
        validateSelf();
    }

    public Long getId() {
        return id;
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
