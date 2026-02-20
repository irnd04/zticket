package kr.jemi.zticket.ticket.adapter.out.persistence;

import jakarta.persistence.*;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets", indexes = @Index(name = "idx_ticket_status", columnList = "status"))
public class TicketJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uuid;

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
        entity.uuid = ticket.getUuid();
        entity.seatNumber = ticket.getSeatNumber();
        entity.status = ticket.getStatus();
        entity.queueToken = ticket.getQueueToken();
        entity.createdAt = ticket.getCreatedAt();
        entity.updatedAt = ticket.getUpdatedAt();
        return entity;
    }

    public Ticket toDomain() {
        return new Ticket(id, uuid, seatNumber, status, queueToken, createdAt, updatedAt);
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public void update(Ticket ticket) {
        this.uuid = ticket.getUuid();
        this.seatNumber = ticket.getSeatNumber();
        this.status = ticket.getStatus();
        this.queueToken = ticket.getQueueToken();
        this.createdAt = ticket.getCreatedAt();
        this.updatedAt = ticket.getUpdatedAt();
    }

    public String getUuid() { return uuid; }
    public TicketStatus getStatus() { return status; }
}
