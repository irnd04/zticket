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
            given(waitingQueuePort.enqueue("token-1")).willReturn(5L);

            // when
            long rank = operator.enqueue("token-1");

            // then
            assertThat(rank).isEqualTo(5L);
            then(waitingQueuePort).should().enqueue("token-1");
            then(waitingQueueHeartbeatPort).should().refresh("token-1");
        }

        @Test
        @DisplayName("enqueue → refresh 순서로 실행된다")
        void shouldEnqueueBeforeRegister() {
            // given
            given(waitingQueuePort.enqueue("token-1")).willReturn(1L);

            // when
            operator.enqueue("token-1");

            // then
            InOrder inOrder = inOrder(waitingQueuePort, waitingQueueHeartbeatPort);
            inOrder.verify(waitingQueuePort).enqueue("token-1");
            inOrder.verify(waitingQueueHeartbeatPort).refresh("token-1");
        }
    }

    @Nested
    @DisplayName("getRank() - 순번 조회")
    class GetRank {

        @Test
        @DisplayName("대기열에 있으면 순번을 반환한다")
        void shouldReturnRankWhenAlive() {
            // given
            given(waitingQueuePort.getRank("token-1")).willReturn(10L);

            // when
            Long rank = operator.getRank("token-1");

            // then
            assertThat(rank).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("refresh() - heartbeat 갱신")
    class Refresh {

        @Test
        @DisplayName("waitingQueueHeartbeatPort에 갱신을 위임한다")
        void shouldDelegateToWaitingQueueHeartbeatPort() {
            // when
            operator.refresh("token-1");

            // then
            then(waitingQueueHeartbeatPort).should().refresh("token-1");
        }
    }

    @Nested
    @DisplayName("peek() - FIFO 순서로 후보 조회")
    class Peek {

        @Test
        @DisplayName("waitingQueuePort에 peek을 위임한다")
        void shouldDelegateToWaitingQueuePort() {
            // given
            List<String> candidates = List.of("token-1", "token-2");
            given(waitingQueuePort.peek(2)).willReturn(candidates);

            // when
            List<String> result = operator.peek(2);

            // then
            assertThat(result).containsExactly("token-1", "token-2");
            then(waitingQueuePort).should().peek(2);
        }
    }

    @Nested
    @DisplayName("findExpired() - 만료 유저 조회")
    class FindExpired {

        @Test
        @DisplayName("waitingQueueHeartbeatPort에 cutoff과 size를 전달하여 만료 유저를 조회한다")
        void shouldFindExpiredWithCutoffAndSize() {
            // given
            List<String> expired = List.of("token-1", "token-2");
            given(waitingQueueHeartbeatPort.findExpired(anyLong(), eq(100))).willReturn(expired);

            // when
            List<String> result = operator.findExpired(100);

            // then
            assertThat(result).containsExactly("token-1", "token-2");
            then(waitingQueueHeartbeatPort).should().findExpired(anyLong(), eq(100));
        }
    }

    @Nested
    @DisplayName("removeAll() - 대기열 + heartbeat 제거")
    class RemoveAll {

        @Test
        @DisplayName("대기열과 heartbeat에서 모두 제거한다")
        void shouldRemoveFromBoth() {
            // given
            List<String> tokens = List.of("token-1", "token-2");

            // when
            operator.removeAll(tokens);

            // then
            then(waitingQueuePort).should().removeAll(tokens);
            then(waitingQueueHeartbeatPort).should().removeAll(tokens);
        }
    }
}
