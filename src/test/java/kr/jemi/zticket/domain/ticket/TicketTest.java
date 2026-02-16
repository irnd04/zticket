package kr.jemi.zticket.domain.ticket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class TicketTest {

    @Nested
    @DisplayName("Ticket.create()")
    class Create {

        @Test
        @DisplayName("생성된 티켓은 PAID 상태여야 한다")
        void shouldCreateWithPaidStatus() {
            Ticket ticket = Ticket.create("token-1", 7);

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
        }

        @Test
        @DisplayName("생성된 티켓은 UUID가 할당되어야 한다")
        void shouldHaveUuid() {
            Ticket ticket = Ticket.create("token-1", 7);

            assertThat(ticket.getUuid()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("생성된 티켓은 전달받은 좌석번호와 큐토큰을 가져야 한다")
        void shouldPreserveSeatNumberAndQueueToken() {
            Ticket ticket = Ticket.create("token-1", 42);

            assertThat(ticket.getSeatNumber()).isEqualTo(42);
            assertThat(ticket.getQueueToken()).isEqualTo("token-1");
        }

        @Test
        @DisplayName("두 번 생성하면 서로 다른 UUID를 가져야 한다")
        void shouldGenerateUniqueUuids() {
            Ticket ticket1 = Ticket.create("token-1", 1);
            Ticket ticket2 = Ticket.create("token-2", 2);

            assertThat(ticket1.getUuid()).isNotEqualTo(ticket2.getUuid());
        }

        @Test
        @DisplayName("생성된 티켓은 createdAt이 설정되고 updatedAt은 null이어야 한다")
        void shouldHaveCreatedAtAndNullUpdatedAt() {
            LocalDateTime before = LocalDateTime.now();
            Ticket ticket = Ticket.create("token-1", 7);
            LocalDateTime after = LocalDateTime.now();

            assertThat(ticket.getCreatedAt()).isBetween(before, after);
            assertThat(ticket.getUpdatedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("Ticket.sync() - 상태 전이")
    class Sync {

        @Test
        @DisplayName("PAID 상태에서 sync() 호출 시 SYNCED로 전환된다")
        void shouldTransitionFromPaidToSynced() {
            Ticket ticket = Ticket.create("token-1", 7);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);

            ticket.sync();

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SYNCED);
        }

        @Test
        @DisplayName("SYNCED 상태에서 sync() 호출 시 IllegalStateException이 발생한다")
        void shouldThrowWhenSyncFromSynced() {
            Ticket ticket = Ticket.create("token-1", 7);
            ticket.sync(); // PAID → SYNCED

            assertThatThrownBy(ticket::sync)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PAID 상태에서만 동기화");
        }

        @Test
        @DisplayName("HELD 상태에서 sync() 호출 시 IllegalStateException이 발생한다")
        void shouldThrowWhenSyncFromHeld() {
            Ticket ticket = new Ticket(null, "uuid-1", 7, TicketStatus.HELD, "token-1",
                    LocalDateTime.now(), null);

            assertThatThrownBy(ticket::sync)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PAID 상태에서만 동기화");
        }

        @Test
        @DisplayName("sync()는 좌석번호와 UUID를 변경하지 않는다")
        void shouldNotChangeSeatNumberOrUuid() {
            Ticket ticket = Ticket.create("token-1", 7);
            String originalUuid = ticket.getUuid();

            ticket.sync();

            assertThat(ticket.getUuid()).isEqualTo(originalUuid);
            assertThat(ticket.getSeatNumber()).isEqualTo(7);
            assertThat(ticket.getQueueToken()).isEqualTo("token-1");
        }

        @Test
        @DisplayName("sync() 호출 시 updatedAt이 설정된다")
        void shouldSetUpdatedAtOnSync() {
            Ticket ticket = Ticket.create("token-1", 7);
            assertThat(ticket.getUpdatedAt()).isNull();

            LocalDateTime before = LocalDateTime.now();
            ticket.sync();
            LocalDateTime after = LocalDateTime.now();

            assertThat(ticket.getUpdatedAt()).isBetween(before, after);
        }
    }
}
