package kr.jemi.zticket.ticket.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TicketTest {

    @Nested
    @DisplayName("Ticket.create()")
    class Create {

        @Test
        @DisplayName("생성된 티켓은 PAID 상태여야 한다")
        void shouldCreateWithPaidStatus() {
            Ticket ticket = Ticket.create(1, "token-1", 7);

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
        }

        @Test
        @DisplayName("생성된 티켓은 전달받은 ID를 가져야 한다")
        void shouldHaveGivenId() {
            Ticket ticket = Ticket.create(1, "token-1", 7);

            assertThat(ticket.getId()).isEqualTo(1);
        }

        @Test
        @DisplayName("생성된 티켓은 전달받은 좌석번호와 큐토큰을 가져야 한다")
        void shouldPreserveSeatNumberAndQueueToken() {
            Ticket ticket = Ticket.create(1, "token-1", 42);

            assertThat(ticket.getSeatNumber()).isEqualTo(42);
            assertThat(ticket.getQueueToken()).isEqualTo("token-1");
        }

        @Test
        @DisplayName("생성된 티켓은 createdAt이 설정되고 updatedAt은 null이어야 한다")
        void shouldHaveCreatedAtAndNullUpdatedAt() {
            LocalDateTime before = LocalDateTime.now();
            Ticket ticket = Ticket.create(1, "token-1", 7);
            LocalDateTime after = LocalDateTime.now();

            assertThat(ticket.getCreatedAt()).isBetween(before, after);
            assertThat(ticket.getUpdatedAt()).isNull();
        }

        @Test
        @DisplayName("생성 시 TicketPaidEvent 도메인 이벤트가 등록된다")
        void shouldRegisterTicketPaidEvent() {
            Ticket ticket = Ticket.create(1, "token-1", 7);

            List<Object> events = ticket.pullEvents();
            assertThat(events).hasSize(1);
            assertThat(events.getFirst()).isInstanceOf(TicketPaidEvent.class);
            assertThat(((TicketPaidEvent) events.getFirst()).ticketId())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("pullEvents() 호출 시 이벤트가 비워진다")
        void shouldClearDomainEvents() {
            Ticket ticket = Ticket.create(1, "token-1", 7);
            assertThat(ticket.pullEvents()).isNotEmpty();
            assertThat(ticket.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("domainEvents() - 도메인 이벤트")
    class DomainEvents {

        @Test
        @DisplayName("생성자로 복원된 티켓은 도메인 이벤트가 없다")
        void shouldHaveNoEventsWhenRestoredFromConstructor() {
            Ticket ticket = new Ticket(1L, 7, TicketStatus.PAID, "token-1",
                    LocalDateTime.now(), null);

            assertThat(ticket.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Ticket.sync() - 상태 전이")
    class Sync {

        @Test
        @DisplayName("PAID 상태에서 sync() 호출 시 SYNCED로 전환된다")
        void shouldTransitionFromPaidToSynced() {
            Ticket ticket = Ticket.create(1, "token-1", 7);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);

            ticket.sync();

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SYNCED);
        }

        @Test
        @DisplayName("SYNCED 상태에서 sync() 호출 시 IllegalStateException이 발생한다")
        void shouldThrowWhenSyncFromSynced() {
            Ticket ticket = Ticket.create(1, "token-1", 7);
            ticket.sync();

            assertThatThrownBy(ticket::sync)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PAID 상태에서만 동기화");
        }

        @Test
        @DisplayName("sync()는 좌석번호와 UUID를 변경하지 않는다")
        void shouldNotChangeSeatNumberOrUuid() {
            Ticket ticket = Ticket.create(1, "token-1", 7);

            ticket.sync();

            assertThat(ticket.getId()).isEqualTo(1);
            assertThat(ticket.getSeatNumber()).isEqualTo(7);
            assertThat(ticket.getQueueToken()).isEqualTo("token-1");
        }

        @Test
        @DisplayName("sync() 호출 시 updatedAt이 설정된다")
        void shouldSetUpdatedAtOnSync() {
            Ticket ticket = Ticket.create(1, "token-1", 7);
            assertThat(ticket.getUpdatedAt()).isNull();

            LocalDateTime before = LocalDateTime.now();
            ticket.sync();
            LocalDateTime after = LocalDateTime.now();

            assertThat(ticket.getUpdatedAt()).isBetween(before, after);
        }
    }
}
