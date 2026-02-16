package kr.jemi.zticket.ticket.adapter.out.persistence;

import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TicketJpaRepository extends JpaRepository<TicketJpaEntity, Long> {
    Optional<TicketJpaEntity> findByUuid(String uuid);
    List<TicketJpaEntity> findByStatus(TicketStatus status);
}
