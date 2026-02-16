package kr.jemi.zticket.adapter.out.persistence;

import jakarta.persistence.*;
import kr.jemi.zticket.domain.ticket.Ticket;
import kr.jemi.zticket.domain.ticket.TicketStatus;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
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
        entity.createdAt = LocalDateTime.now();
        return entity;
    }

    public Ticket toDomain() {
        return new Ticket(uuid, seatNumber, status, queueToken);
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public String getUuid() { return uuid; }
    public TicketStatus getStatus() { return status; }
}
