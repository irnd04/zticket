package kr.jemi.zticket.adapter.out.persistence;

import kr.jemi.zticket.domain.ticket.Ticket;
import kr.jemi.zticket.domain.ticket.TicketStatus;
import kr.jemi.zticket.application.port.out.TicketPersistencePort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Component
public class TicketJpaAdapter implements TicketPersistencePort {

    private final TicketJpaRepository repository;

    public TicketJpaAdapter(TicketJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Ticket save(Ticket ticket) {
        TicketJpaEntity entity = repository.findByUuid(ticket.getUuid())
                .map(existing -> {
                    existing.setStatus(ticket.getStatus());
                    return existing;
                })
                .orElseGet(() -> TicketJpaEntity.fromDomain(ticket));
        repository.save(entity);
        return ticket;
    }

    @Override
    public Optional<Ticket> findByUuid(String uuid) {
        return repository.findByUuid(uuid).map(TicketJpaEntity::toDomain);
    }

    @Override
    public List<Ticket> findByStatus(TicketStatus status) {
        return repository.findByStatus(status).stream()
                .map(TicketJpaEntity::toDomain)
                .toList();
    }
}
