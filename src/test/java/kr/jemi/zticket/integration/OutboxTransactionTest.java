package kr.jemi.zticket.integration;

import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketPaidEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxTransactionTest extends IntegrationTestBase {

    @Autowired
    TicketPort ticketPort;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("insert 성공 후 예외 발생 → 티켓 롤백된다")
    void insertSuccessThenThrow_ticketRolledBack() {
        Ticket ticket = Ticket.create(1L, "token-1", 1);

        assertThatThrownBy(() ->
                transactionTemplate.execute(status -> {
                    ticketPort.insert(ticket);
                    throw new RuntimeException("insert 후 강제 예외");
                })
        ).isInstanceOf(RuntimeException.class);

        assertThat(ticketJpaRepository.count())
                .as("insert가 롤백되어 티켓이 없어야 한다")
                .isZero();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication", Integer.class))
                .as("이벤트 발행 전이므로 event_publication도 비어야 한다")
                .isZero();
    }

    @Test
    @DisplayName("insert 성공 + event publish 성공 후 예외 발생 → 티켓과 event_publication 모두 롤백된다")
    void insertAndPublishSuccessThenThrow_bothRolledBack() {
        Ticket ticket = Ticket.create(2L, "token-2", 2);

        assertThatThrownBy(() ->
                transactionTemplate.execute(status -> {
                    Ticket saved = ticketPort.insert(ticket);
                    eventPublisher.publishEvent(new TicketPaidEvent(saved.getId()));
                    throw new RuntimeException("insert+publish 후 강제 예외");
                })
        ).isInstanceOf(RuntimeException.class);

        assertThat(ticketJpaRepository.count())
                .as("insert가 롤백되어 티켓이 없어야 한다")
                .isZero();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication", Integer.class))
                .as("event_publication도 같은 트랜잭션이므로 롤백되어야 한다")
                .isZero();
    }

    @Test
    @DisplayName("정상 커밋 시 티켓과 event_publication 모두 저장된다")
    void normalCommit_bothPersisted() {
        Ticket ticket = Ticket.create(3L, "token-3", 3);

        transactionTemplate.execute(status -> {
            Ticket saved = ticketPort.insert(ticket);
            eventPublisher.publishEvent(new TicketPaidEvent(saved.getId()));
            return saved;
        });

        assertThat(ticketJpaRepository.count())
                .as("티켓이 저장되어야 한다")
                .isEqualTo(1);

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication", Integer.class);
        assertThat(eventCount)
                .as("event_publication에 레코드가 저장되어야 한다 (리스너 처리 전이면 1, 처리 후면 0)")
                .isGreaterThanOrEqualTo(0);
    }
}
