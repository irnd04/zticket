package kr.jemi.zticket.ticket.infrastructure.out.persistence;

import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class TicketJpaEntityTest {

    @Nested
    @DisplayName("fromDomain() - 도메인 → JPA 엔티티 변환")
    class FromDomain {

        @Test
        @DisplayName("모든 필드가 정확히 매핑된다")
        void shouldMapAllFields() {
            LocalDateTime now = LocalDateTime.now();
            Ticket ticket = new Ticket(1L, 7, TicketStatus.PAID, "token-1", now, now);

            TicketJpaEntity entity = TicketJpaEntity.fromDomain(ticket);
            Ticket restored = entity.toDomain();

            assertThat(restored.getId()).isEqualTo(1L);
            assertThat(restored.getSeatNumber()).isEqualTo(7);
            assertThat(restored.getStatus()).isEqualTo(TicketStatus.PAID);
            assertThat(restored.getQueueToken()).isEqualTo("token-1");
            assertThat(restored.getCreatedAt()).isEqualTo(now);
            assertThat(restored.getUpdatedAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("update() - 도메인 변경사항 반영")
    class Update {

        @Test
        @DisplayName("sync된 도메인 객체로 update하면 SYNCED 상태가 반영된다")
        void shouldReflectSyncedStatus() {
            LocalDateTime now = LocalDateTime.now();
            Ticket original = new Ticket(1L, 7, TicketStatus.PAID, "token-1", now, now);
            TicketJpaEntity entity = TicketJpaEntity.fromDomain(original);

            original.sync();
            entity.update(original);

            Ticket restored = entity.toDomain();
            assertThat(restored.getStatus()).isEqualTo(TicketStatus.SYNCED);
            assertThat(restored.getUpdatedAt()).isAfterOrEqualTo(now);
        }
    }

    @Nested
    @DisplayName("toDomain() - JPA 엔티티 → 도메인 복원")
    class ToDomain {

        @Test
        @DisplayName("fromDomain → toDomain 왕복 변환 시 데이터가 보존된다")
        void shouldPreserveDataOnRoundTrip() {
            Ticket ticket = Ticket.create(1L, "token-1", 42);

            TicketJpaEntity entity = TicketJpaEntity.fromDomain(ticket);
            Ticket restored = entity.toDomain();

            assertThat(restored.getId()).isEqualTo(ticket.getId());
            assertThat(restored.getSeatNumber()).isEqualTo(ticket.getSeatNumber());
            assertThat(restored.getStatus()).isEqualTo(ticket.getStatus());
            assertThat(restored.getQueueToken()).isEqualTo(ticket.getQueueToken());
            assertThat(restored.getCreatedAt()).isEqualTo(ticket.getCreatedAt());
            assertThat(restored.getUpdatedAt()).isEqualTo(ticket.getUpdatedAt());
        }
    }
}
