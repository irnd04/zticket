package kr.jemi.zticket.ticket.adapter.out.persistence;

import jakarta.persistence.*;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets", indexes = @Index(name = "idx_ticket_status", columnList = "status"))
public class TicketJpaEntity {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(nullable = false)
    private String queueToken;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected TicketJpaEntity() {}

    public static TicketJpaEntity fromDomain(Ticket ticket) {
        TicketJpaEntity entity = new TicketJpaEntity();
        entity.id = ticket.getId();
        entity.seatNumber = ticket.getSeatNumber();
        entity.status = ticket.getStatus();
        entity.queueToken = ticket.getQueueToken();
        entity.createdAt = ticket.getCreatedAt();
        entity.updatedAt = ticket.getUpdatedAt();
        return entity;
    }

    public Ticket toDomain() {
        return new Ticket(id, seatNumber, status, queueToken, createdAt, updatedAt);
    }

    public void update(Ticket ticket) {
        this.seatNumber = ticket.getSeatNumber();
        this.status = ticket.getStatus();
        this.queueToken = ticket.getQueueToken();
        this.createdAt = ticket.getCreatedAt();
        this.updatedAt = ticket.getUpdatedAt();
    }

    public TicketStatus getStatus() { return status; }
}
