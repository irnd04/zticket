package kr.jemi.zticket.ticket.infrastructure.out.persistence;

import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketJpaRepository extends JpaRepository<TicketJpaEntity, Long> {
    List<TicketJpaEntity> findByStatus(TicketStatus status);
}
