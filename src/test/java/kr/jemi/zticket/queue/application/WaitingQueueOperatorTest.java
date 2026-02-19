package kr.jemi.zticket.queue.application;

import kr.jemi.zticket.queue.application.port.out.WaitingQueueHeartbeatPort;
import kr.jemi.zticket.queue.application.port.out.WaitingQueuePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class WaitingQueueOperatorTest {

    @Mock
    private WaitingQueuePort waitingQueuePort;

    @Mock
    private WaitingQueueHeartbeatPort waitingQueueHeartbeatPort;

    private WaitingQueueOperator operator;

    private static final long QUEUE_TTL_SECONDS = 60L;

    @BeforeEach
    void setUp() {
        operator = new WaitingQueueOperator(waitingQueuePort, waitingQueueHeartbeatPort, QUEUE_TTL_SECONDS);
    }

    @Nested
    @DisplayName("enqueue() - 대기열 등록")
    class Enqueue {

        @Test
        @DisplayName("대기열에 등록하고 heartbeat를 등록한 뒤 순번을 반환한다")
        void shouldEnqueueAndRegisterHeartbeat() {
            // given
            given(waitingQueuePort.enqueue("uuid-1")).willReturn(5L);

            // when
            long rank = operator.enqueue("uuid-1");

            // then
            assertThat(rank).isEqualTo(5L);
            then(waitingQueuePort).should().enqueue("uuid-1");
            then(waitingQueueHeartbeatPort).should().refresh("uuid-1");
        }

        @Test
        @DisplayName("enqueue → register 순서로 실행된다")
        void shouldEnqueueBeforeRegister() {
            // given
            given(waitingQueuePort.enqueue("uuid-1")).willReturn(1L);

            // when
            operator.enqueue("uuid-1");

            // then
            InOrder inOrder = inOrder(waitingQueuePort, waitingQueueHeartbeatPort);
            inOrder.verify(waitingQueuePort).enqueue("uuid-1");
            inOrder.verify(waitingQueueHeartbeatPort).refresh("uuid-1");
        }
    }

    @Nested
    @DisplayName("getRank() - 순번 조회")
    class GetRank {

        @Test
        @DisplayName("대기열에 있고 heartbeat가 살아있으면 순번을 반환한다")
        void shouldReturnRankWhenAlive() {
            // given
            given(waitingQueuePort.getRank("uuid-1")).willReturn(10L);
            given(waitingQueueHeartbeatPort.getScores(List.of("uuid-1")))
                    .willReturn(List.of(System.currentTimeMillis()));

            // when
            Long rank = operator.getRank("uuid-1");

            // then
            assertThat(rank).isEqualTo(10L);
        }

        @Test
        @DisplayName("대기열에 없으면 null을 반환한다")
        void shouldReturnNullWhenNotInQueue() {
            // given
            given(waitingQueuePort.getRank("uuid-1")).willReturn(null);

            // when
            Long rank = operator.getRank("uuid-1");

            // then
            assertThat(rank).isNull();
            then(waitingQueueHeartbeatPort).should(never()).getScores(anyList());
        }

        @Test
        @DisplayName("heartbeat가 만료된 유저는 null을 반환한다")
        void shouldReturnNullWhenHeartbeatExpired() {
            // given
            given(waitingQueuePort.getRank("uuid-1")).willReturn(10L);
            long expiredScore = System.currentTimeMillis() - (QUEUE_TTL_SECONDS * 1000) - 1;
            given(waitingQueueHeartbeatPort.getScores(List.of("uuid-1")))
                    .willReturn(List.of(expiredScore));

            // when
            Long rank = operator.getRank("uuid-1");

            // then
            assertThat(rank).isNull();
        }

        @Test
        @DisplayName("heartbeat score가 null이면 null을 반환한다")
        void shouldReturnNullWhenHeartbeatScoreIsNull() {
            // given
            given(waitingQueuePort.getRank("uuid-1")).willReturn(10L);
            List<Long> scores = new java.util.ArrayList<>();
            scores.add(null);
            given(waitingQueueHeartbeatPort.getScores(List.of("uuid-1"))).willReturn(scores);

            // when
            Long rank = operator.getRank("uuid-1");

            // then
            assertThat(rank).isNull();
        }
    }

    @Nested
    @DisplayName("refresh() - heartbeat 갱신")
    class Refresh {

        @Test
        @DisplayName("waitingQueueHeartbeatPort에 갱신을 위임한다")
        void shouldDelegateToWaitingQueueHeartbeatPort() {
            // when
            operator.refresh("uuid-1");

            // then
            then(waitingQueueHeartbeatPort).should().refresh("uuid-1");
        }
    }

    @Nested
    @DisplayName("peekAlive() - FIFO 순서로 살아있는 후보 조회")
    class PeekAlive {

        @Test
        @DisplayName("size의 2배를 peek한 뒤 heartbeat 필터링으로 size만큼 반환한다")
        void shouldPeekDoubleAndFilterByHeartbeat() {
            // given
            List<String> candidates = List.of("uuid-1", "uuid-2", "uuid-3", "uuid-4");
            given(waitingQueuePort.peek(4)).willReturn(candidates);
            long now = System.currentTimeMillis();
            long expired = now - (QUEUE_TTL_SECONDS * 1000) - 1;
            given(waitingQueueHeartbeatPort.getScores(candidates))
                    .willReturn(List.of(now, expired, now, now));

            // when
            List<String> result = operator.peekAlive(2);

            // then
            assertThat(result).containsExactly("uuid-1", "uuid-3");
            then(waitingQueuePort).should().peek(4); // size * 2
        }

        @Test
        @DisplayName("후보가 비어있으면 빈 리스트를 반환한다")
        void shouldReturnEmptyWhenNoCandidates() {
            // given
            given(waitingQueuePort.peek(4)).willReturn(List.of());

            // when
            List<String> result = operator.peekAlive(2);

            // then
            assertThat(result).isEmpty();
            then(waitingQueueHeartbeatPort).should(never()).getScores(anyList());
        }

        @Test
        @DisplayName("모든 후보가 살아있으면 size만큼만 반환한다")
        void shouldLimitToSize() {
            // given
            List<String> candidates = List.of("uuid-1", "uuid-2", "uuid-3", "uuid-4");
            given(waitingQueuePort.peek(4)).willReturn(candidates);
            long now = System.currentTimeMillis();
            given(waitingQueueHeartbeatPort.getScores(candidates))
                    .willReturn(List.of(now, now, now, now));

            // when
            List<String> result = operator.peekAlive(2);

            // then
            assertThat(result).containsExactly("uuid-1", "uuid-2");
        }

        @Test
        @DisplayName("FIFO 순서를 유지한다")
        void shouldPreserveFifoOrder() {
            // given
            List<String> candidates = List.of("uuid-1", "uuid-2", "uuid-3", "uuid-4", "uuid-5", "uuid-6");
            given(waitingQueuePort.peek(6)).willReturn(candidates);
            long now = System.currentTimeMillis();
            long expired = now - (QUEUE_TTL_SECONDS * 1000) - 1;
            // uuid-1 살아있음, uuid-2 만료, uuid-3 만료, uuid-4 살아있음, uuid-5 살아있음, uuid-6 살아있음
            given(waitingQueueHeartbeatPort.getScores(candidates))
                    .willReturn(List.of(now, expired, expired, now, now, now));

            // when
            List<String> result = operator.peekAlive(3);

            // then - FIFO 순서: uuid-1 → uuid-4 → uuid-5
            assertThat(result).containsExactly("uuid-1", "uuid-4", "uuid-5");
        }

        @Test
        @DisplayName("heartbeat score가 null인 후보는 필터링된다")
        void shouldFilterNullScores() {
            // given
            List<String> candidates = List.of("uuid-1", "uuid-2");
            given(waitingQueuePort.peek(2)).willReturn(candidates);
            long now = System.currentTimeMillis();
            List<Long> scores = new java.util.ArrayList<>();
            scores.add(now);
            scores.add(null);
            given(waitingQueueHeartbeatPort.getScores(candidates)).willReturn(scores);

            // when
            List<String> result = operator.peekAlive(1);

            // then
            assertThat(result).containsExactly("uuid-1");
        }
    }

    @Nested
    @DisplayName("findExpired() - 만료 유저 조회")
    class FindExpired {

        @Test
        @DisplayName("waitingQueueHeartbeatPort에 cutoff 기준으로 만료 유저를 조회한다")
        void shouldFindExpiredWithCutoff() {
            // given
            List<String> expired = List.of("uuid-1", "uuid-2");
            given(waitingQueueHeartbeatPort.findExpired(anyLong())).willReturn(expired);

            // when
            List<String> result = operator.findExpired();

            // then
            assertThat(result).containsExactly("uuid-1", "uuid-2");
            then(waitingQueueHeartbeatPort).should().findExpired(anyLong());
        }
    }

    @Nested
    @DisplayName("removeAll() - 대기열 + heartbeat 제거")
    class RemoveAll {

        @Test
        @DisplayName("대기열과 heartbeat에서 모두 제거한다")
        void shouldRemoveFromBoth() {
            // given
            List<String> uuids = List.of("uuid-1", "uuid-2");

            // when
            operator.removeAll(uuids);

            // then
            then(waitingQueuePort).should().removeAll(uuids);
            then(waitingQueueHeartbeatPort).should().removeAll(uuids);
        }
    }
}
