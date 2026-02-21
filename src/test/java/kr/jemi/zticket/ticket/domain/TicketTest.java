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
        @DisplayName("생성된 티켓은 createdAt과 updatedAt이 설정되어야 한다")
        void shouldHaveCreatedAtAndUpdatedAt() {
            LocalDateTime before = LocalDateTime.now();
            Ticket ticket = Ticket.create(1, "token-1", 7);
            LocalDateTime after = LocalDateTime.now();

            assertThat(ticket.getCreatedAt()).isBetween(before, after);
            assertThat(ticket.getUpdatedAt()).isBetween(before, after);
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
    @DisplayName("Ticket 검증")
    class Validation {

        @Test
        @DisplayName("좌석 번호가 0이면 생성에 실패한다")
        void shouldRejectZeroSeatNumber() {
            assertThatThrownBy(() -> Ticket.create(1, "token-1", 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("좌석 번호가 음수이면 생성에 실패한다")
        void shouldRejectNegativeSeatNumber() {
            assertThatThrownBy(() -> Ticket.create(1, "token-1", -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("queueToken이 null이면 생성에 실패한다")
        void shouldRejectNullQueueToken() {
            assertThatThrownBy(() -> Ticket.create(1, null, 7))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("status가 null이면 생성에 실패한다")
        void shouldRejectNullStatus() {
            assertThatThrownBy(() -> new Ticket(1L, 7, null, "token-1",
                    LocalDateTime.now(), LocalDateTime.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("domainEvents() - 도메인 이벤트")
    class DomainEvents {

        @Test
        @DisplayName("생성자로 복원된 티켓은 도메인 이벤트가 없다")
        void shouldHaveNoEventsWhenRestoredFromConstructor() {
            Ticket ticket = new Ticket(1L, 7, TicketStatus.PAID, "token-1",
                    LocalDateTime.now(), LocalDateTime.now());

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
        @DisplayName("SYNCED 상태에서 sync() 호출 시 멱등하게 무시된다")
        void shouldBeIdempotentWhenAlreadySynced() {
            Ticket ticket = Ticket.create(1, "token-1", 7);
            ticket.sync();

            ticket.sync();

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SYNCED);
        }

        @Test
        @DisplayName("sync()는 좌석번호와 토큰을 변경하지 않는다")
        void shouldNotChangeSeatNumberOrToken() {
            Ticket ticket = Ticket.create(1, "token-1", 7);

            ticket.sync();

            assertThat(ticket.getId()).isEqualTo(1);
            assertThat(ticket.getSeatNumber()).isEqualTo(7);
            assertThat(ticket.getQueueToken()).isEqualTo("token-1");
        }

        @Test
        @DisplayName("sync() 호출 시 updatedAt이 갱신된다")
        void shouldUpdateUpdatedAtOnSync() {
            Ticket ticket = Ticket.create(1, "token-1", 7);
            LocalDateTime initialUpdatedAt = ticket.getUpdatedAt();

            LocalDateTime before = LocalDateTime.now();
            ticket.sync();
            LocalDateTime after = LocalDateTime.now();

            assertThat(ticket.getUpdatedAt()).isBetween(before, after);
            assertThat(ticket.getUpdatedAt()).isAfterOrEqualTo(initialUpdatedAt);
        }

        @Test
        @DisplayName("sync() 후에도 모든 필드가 NotNull이다")
        void shouldPassValidationAfterSync() {
            Ticket ticket = Ticket.create(1, "token-1", 7);

            ticket.sync();

            assertThat(ticket.getStatus()).isNotNull();
            assertThat(ticket.getQueueToken()).isNotNull();
            assertThat(ticket.getCreatedAt()).isNotNull();
            assertThat(ticket.getUpdatedAt()).isNotNull();
        }
    }
}
