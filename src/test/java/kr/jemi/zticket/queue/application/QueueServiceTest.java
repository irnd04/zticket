package kr.jemi.zticket.queue.application;

import io.hypersistence.tsid.TSID;
import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.queue.domain.QueueStatus;
import kr.jemi.zticket.queue.domain.QueueToken;
import kr.jemi.zticket.seat.application.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
    private SeatService seatService;

    private final TSID.Factory tsidFactory = TSID.Factory.newInstance256(0);

    private QueueService queueService;

    private static final long ACTIVE_TTL_SECONDS = 300L;
    private static final int MAX_ACTIVE_USERS = 500;
    private static final int BATCH_SIZE = 100;
    private static final int FIND_EXPIRED_BATCH_SIZE = 5000;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(
                waitingQueueOperator, activeUserPort, seatService, tsidFactory,
                ACTIVE_TTL_SECONDS, MAX_ACTIVE_USERS, BATCH_SIZE);
    }

    @Nested
    @DisplayName("enter() - 대기열 진입")
    class Enter {

        @Test
        @DisplayName("토큰을 생성하고 대기열에 등록 후 순번을 반환한다")
        void shouldEnqueueAndReturnRank() {
            // given
            given(seatService.getAvailableCount()).willReturn(10);
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
            given(seatService.getAvailableCount()).willReturn(10);
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
            given(seatService.getAvailableCount()).willReturn(0);

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
            given(seatService.getAvailableCount()).willReturn(10);
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
            given(seatService.getAvailableCount()).willReturn(10);
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
            given(seatService.getAvailableCount()).willReturn(10);
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
            given(seatService.getAvailableCount()).willReturn(0);

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

    @Nested
    @DisplayName("admitBatch() - removeExpired → peek → activate → remove")
    class AdmitBatch {

        @Test
        @DisplayName("removeExpired → peek → activate → remove 순서로 실행된다")
        void shouldExecuteFourPhasesInOrder() {
            // given
            List<String> expired = List.of("expired-1");
            List<String> candidates = List.of("token-1", "token-2", "token-3");
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(expired, List.of());
            given(activeUserPort.countActive()).willReturn(0);
            given(seatService.getAvailableCount()).willReturn(1000);
            given(waitingQueueOperator.peek(BATCH_SIZE)).willReturn(candidates);

            // when
            queueService.admitBatch();

            // then
            InOrder inOrder = inOrder(waitingQueueOperator, activeUserPort);
            inOrder.verify(waitingQueueOperator).findExpired(FIND_EXPIRED_BATCH_SIZE);
            inOrder.verify(waitingQueueOperator).removeAll(expired);
            inOrder.verify(waitingQueueOperator).peek(BATCH_SIZE);
            inOrder.verify(activeUserPort).activateBatch(candidates, ACTIVE_TTL_SECONDS);
            inOrder.verify(waitingQueueOperator).removeAll(candidates);
        }

        @Test
        @DisplayName("activate가 모두 완료된 후에만 큐에서 제거한다 (유실 방지)")
        void shouldRemoveOnlyAfterAllActivated() {
            List<String> candidates = List.of("token-1");
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(0);
            given(seatService.getAvailableCount()).willReturn(1000);
            given(waitingQueueOperator.peek(anyInt())).willReturn(candidates);

            // when
            queueService.admitBatch();

            // then
            InOrder inOrder = inOrder(activeUserPort, waitingQueueOperator);
            inOrder.verify(activeUserPort).activateBatch(candidates, ACTIVE_TTL_SECONDS);
            inOrder.verify(waitingQueueOperator).removeAll(candidates);
        }

        @Test
        @DisplayName("잠수 유저가 없어도 removeAll은 빈 리스트로 호출된다")
        void shouldCallRemoveAllWithEmptyListWhenNoExpired() {
            // given
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(0);
            given(seatService.getAvailableCount()).willReturn(1000);
            given(waitingQueueOperator.peek(BATCH_SIZE)).willReturn(List.of("token-1"));

            // when
            queueService.admitBatch();

            // then - removeAll 2회: expired 빈 리스트 1회 + 입장 후 1회
            then(waitingQueueOperator).should(times(2)).removeAll(anyList());
        }
    }

    @Nested
    @DisplayName("admitBatch() - active 유저 상한 제어")
    class AdmitBatchCapacity {

        @Test
        @DisplayName("maxActiveUsers에 도달하면 입장시키지 않는다")
        void shouldNotAdmitWhenMaxActiveReached() {
            // given
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(500);

            // when
            queueService.admitBatch();

            // then
            then(waitingQueueOperator).should(never()).peek(anyInt());
            then(activeUserPort).should(never()).activate(anyString(), anyLong());
        }

        @Test
        @DisplayName("빈 슬롯 수만큼만 입장시킨다")
        void shouldAdmitOnlyAvailableSlots() {
            // given - 현재 480명 active → 빈 슬롯 20개
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(480);
            given(seatService.getAvailableCount()).willReturn(1000);
            List<String> candidates = List.of("token-1", "token-2");
            given(waitingQueueOperator.peek(20)).willReturn(candidates);

            // when
            queueService.admitBatch();

            // then
            then(waitingQueueOperator).should().peek(20);
        }

        @Test
        @DisplayName("잔여 좌석에서 active 유저 수를 차감하여 입장 인원을 결정한다")
        void shouldSubtractActiveUsersFromRemainingSeats() {
            // given - 잔여 좌석 5개, active 3명 → 입장 가능 = 2명
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(3);
            given(seatService.getAvailableCount()).willReturn(5);
            List<String> candidates = List.of("token-1", "token-2");
            given(waitingQueueOperator.peek(2)).willReturn(candidates);

            // when
            queueService.admitBatch();

            // then
            then(waitingQueueOperator).should().peek(2);
            then(activeUserPort).should().activateBatch(candidates, ACTIVE_TTL_SECONDS);
        }

        @Test
        @DisplayName("잔여 좌석이 active 유저 수 이하이면 입장시키지 않는다")
        void shouldNotAdmitWhenRemainingSeatsLessThanActive() {
            // given
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(5);
            given(seatService.getAvailableCount()).willReturn(3);

            // when
            queueService.admitBatch();

            // then
            then(waitingQueueOperator).should(never()).peek(anyInt());
        }

        @Test
        @DisplayName("maxActiveUsers를 초과하면 toAdmit이 0이 된다")
        void shouldHandleOverCapacity() {
            // given
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(510);

            // when
            queueService.admitBatch();

            // then
            then(waitingQueueOperator).should(never()).peek(anyInt());
        }
    }

    @Nested
    @DisplayName("admitBatch() - batchSize 상한 제어")
    class AdmitBatchSizeLimit {

        @Test
        @DisplayName("슬롯이 충분해도 batchSize를 초과하여 입장시키지 않는다")
        void shouldNotExceedBatchSize() {
            // given
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(0);
            given(seatService.getAvailableCount()).willReturn(1000);
            given(waitingQueueOperator.peek(BATCH_SIZE)).willReturn(List.of("token-1"));

            // when
            queueService.admitBatch();

            // then
            then(waitingQueueOperator).should().peek(BATCH_SIZE);
        }

        @Test
        @DisplayName("빈 슬롯이 batchSize보다 적으면 빈 슬롯만큼만 입장시킨다")
        void shouldAdmitFewerThanBatchSizeWhenSlotsLimited() {
            // given - active 470명 → 빈 슬롯 30개
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(470);
            given(seatService.getAvailableCount()).willReturn(1000);
            given(waitingQueueOperator.peek(30)).willReturn(List.of("token-1"));

            // when
            queueService.admitBatch();

            // then
            then(waitingQueueOperator).should().peek(30);
        }
    }

    @Nested
    @DisplayName("admitBatch() - 대기열 비어있음")
    class AdmitBatchEmptyQueue {

        @Test
        @DisplayName("대기열이 비어있으면 activate와 remove를 실행하지 않는다")
        void shouldDoNothingWhenQueueIsEmpty() {
            // given
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(0);
            given(seatService.getAvailableCount()).willReturn(1000);
            given(waitingQueueOperator.peek(BATCH_SIZE)).willReturn(List.of());

            // when
            queueService.admitBatch();

            // then
            then(activeUserPort).should(never()).activateBatch(anyList(), anyLong());
            then(waitingQueueOperator).should(times(1)).removeAll(List.of());
        }
    }

    @Nested
    @DisplayName("admitBatch() - 잠수 유저 자연 회수 시나리오")
    class StaleUserRecovery {

        @Test
        @DisplayName("잠수 유저 TTL 만료 후 다음 주기에 새 유저를 입장시킨다")
        void shouldAdmitNewUsersAfterStaleUserExpiry() {
            // 1주기 - maxActive 도달
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(500);
            queueService.admitBatch();
            then(waitingQueueOperator).should(never()).peek(anyInt());

            // 2주기 - TTL 만료로 active 감소
            given(activeUserPort.countActive()).willReturn(490);
            given(seatService.getAvailableCount()).willReturn(1000);
            List<String> newCandidates = List.of("new-1", "new-2", "new-3");
            given(waitingQueueOperator.peek(10)).willReturn(newCandidates);

            queueService.admitBatch();

            // then
            then(activeUserPort).should().activateBatch(
                    List.of("new-1", "new-2", "new-3"), ACTIVE_TTL_SECONDS);
        }
    }
}
