package kr.jemi.zticket.queue.application.service;

import io.hypersistence.tsid.TSID;
import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.queue.domain.QueueStatus;
import kr.jemi.zticket.queue.domain.QueueToken;
import kr.jemi.zticket.queue.application.port.out.AvailableSeatCountPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private WaitingQueueOperator waitingQueueOperator;

    @Mock
    private ActiveUserPort activeUserPort;

    @Mock
    private AvailableSeatCountPort availableSeatCountPort;

    private final TSID.Factory tsidFactory = TSID.Factory.newInstance256(0);

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(
                waitingQueueOperator, activeUserPort, availableSeatCountPort, tsidFactory);
    }

    @Nested
    @DisplayName("enter() - 대기열 진입")
    class Enter {

        @Test
        @DisplayName("토큰을 생성하고 대기열에 등록 후 순번을 반환한다")
        void shouldEnqueueAndReturnRank() {
            // given
            given(availableSeatCountPort.getAvailableCount()).willReturn(10);
            given(waitingQueueOperator.enqueue(anyString())).willReturn(1L);

            // when
            QueueToken token = queueService.enter();

            // then
            assertThat(token.token()).isNotNull().isNotBlank();
            assertThat(token.rank()).isEqualTo(1L);
            then(waitingQueueOperator).should().enqueue(token.token());
        }

        @Test
        @DisplayName("여러 번 enter()하면 매번 다른 토큰이 생성된다")
        void shouldGenerateUniqueTokens() {
            // given
            given(availableSeatCountPort.getAvailableCount()).willReturn(10);
            given(waitingQueueOperator.enqueue(anyString())).willReturn(1L, 2L);

            // when
            QueueToken token1 = queueService.enter();
            QueueToken token2 = queueService.enter();

            // then
            assertThat(token1.token()).isNotEqualTo(token2.token());
        }

        @Test
        @DisplayName("매진 시 SOLD_OUT 예외를 던진다")
        void shouldThrowWhenSoldOut() {
            // given
            given(availableSeatCountPort.getAvailableCount()).willReturn(0);

            // when & then
            assertThatThrownBy(() -> queueService.enter())
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SOLD_OUT));
            then(waitingQueueOperator).should(never()).enqueue(anyString());
        }
    }

    @Nested
    @DisplayName("getQueueToken() - 대기열 토큰 조회")
    class GetQueueToken {

        @Test
        @DisplayName("active 유저는 ACTIVE 상태를 반환한다")
        void shouldReturnActiveStatus() {
            // given
            given(activeUserPort.isActive("token-1")).willReturn(true);

            // when
            QueueToken token = queueService.getQueueToken("token-1");

            // then
            assertThat(token.status()).isEqualTo(QueueStatus.ACTIVE);
            assertThat(token.token()).isEqualTo("token-1");
        }

        @Test
        @DisplayName("대기 중인 유저는 WAITING 상태와 순번을 반환한다")
        void shouldReturnWaitingStatus() {
            // given
            given(activeUserPort.isActive("token-1")).willReturn(false);
            given(availableSeatCountPort.getAvailableCount()).willReturn(10);
            given(waitingQueueOperator.getRank("token-1")).willReturn(42L);

            // when
            QueueToken token = queueService.getQueueToken("token-1");

            // then
            assertThat(token.status()).isEqualTo(QueueStatus.WAITING);
            assertThat(token.rank()).isEqualTo(42L);
        }

        @Test
        @DisplayName("대기 중인 유저의 heartbeat를 갱신한다")
        void shouldRefreshHeartbeatForWaitingUser() {
            // given
            given(activeUserPort.isActive("token-1")).willReturn(false);
            given(availableSeatCountPort.getAvailableCount()).willReturn(10);
            given(waitingQueueOperator.getRank("token-1")).willReturn(42L);

            // when
            queueService.getQueueToken("token-1");

            // then
            then(waitingQueueOperator).should().refresh("token-1");
        }

        @Test
        @DisplayName("대기열에 없는 유저는 QUEUE_TOKEN_NOT_FOUND 예외를 던진다")
        void shouldThrowWhenTokenNotFound() {
            // given
            given(activeUserPort.isActive("token-1")).willReturn(false);
            given(availableSeatCountPort.getAvailableCount()).willReturn(10);
            given(waitingQueueOperator.getRank("token-1")).willReturn(null);

            // when & then
            assertThatThrownBy(() -> queueService.getQueueToken("token-1"))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QUEUE_TOKEN_NOT_FOUND));
        }

        @Test
        @DisplayName("매진 시 SOLD_OUT 상태를 반환한다")
        void shouldReturnSoldOutWhenNoSeatsAvailable() {
            // given
            given(activeUserPort.isActive("token-1")).willReturn(false);
            given(availableSeatCountPort.getAvailableCount()).willReturn(0);

            // when
            QueueToken token = queueService.getQueueToken("token-1");

            // then
            assertThat(token.status()).isEqualTo(QueueStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("active 유저는 대기열 순번 조회를 하지 않는다 (불필요한 Redis 호출 방지)")
        void shouldNotCheckRankForActiveUser() {
            // given
            given(activeUserPort.isActive("token-1")).willReturn(true);

            // when
            queueService.getQueueToken("token-1");

            // then
            then(waitingQueueOperator).should(never()).getRank(anyString());
        }
    }
}
