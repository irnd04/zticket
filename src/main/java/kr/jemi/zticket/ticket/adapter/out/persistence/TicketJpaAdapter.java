package kr.jemi.zticket.ticket.adapter.out.persistence;

import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Component
public class TicketJpaAdapter implements TicketPort {

    private final TicketJpaRepository repository;

    public TicketJpaAdapter(TicketJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Ticket insert(Ticket ticket) {
        TicketJpaEntity entity
            = TicketJpaEntity.fromDomain(ticket);
        return repository.save(entity).toDomain();
    }

    @Transactional
    public void update(Ticket ticket) {
        TicketJpaEntity entity = repository.findById(ticket.getId())
            .orElseThrow(() ->
                new IllegalStateException("티켓을 찾을 수 없습니다: id=" + ticket.getId()));
        entity.update(ticket);
    }

    @Override
    public Optional<Ticket> findById(long ticketId) {
        return repository.findById(ticketId).map(TicketJpaEntity::toDomain);
    }

    @Override
    public List<Ticket> findByStatus(TicketStatus status) {
        return repository.findByStatus(status).stream()
                .map(TicketJpaEntity::toDomain)
                .toList();
    }
}
