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
    public Ticket save(Ticket ticket) {
        TicketJpaEntity entity = ticket.getId() != null
                ? repository.findById(ticket.getId())
                        .map(existing -> {
                            existing.update(ticket);
                            return existing;
                        })
                        .orElseThrow(() -> new IllegalStateException(
                                "티켓을 찾을 수 없습니다: id=" + ticket.getId()))
                : TicketJpaEntity.fromDomain(ticket);
        return repository.save(entity).toDomain();
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
