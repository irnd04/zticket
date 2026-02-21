package kr.jemi.zticket.ticket.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Ticket {

    private final Long id;
    private final int seatNumber;
    private TicketStatus status;
    private final String queueToken;
    private final LocalDateTime createdAt;
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
    }

    public static Ticket create(long id, String queueToken, int seatNumber) {
        Ticket ticket = new Ticket(id, seatNumber, TicketStatus.PAID, queueToken,
                LocalDateTime.now(), null);
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
