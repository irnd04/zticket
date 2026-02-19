package kr.jemi.zticket.queue.application;

import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.queue.application.port.out.WaitingQueuePort;
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
    private WaitingQueuePort waitingQueuePort;

    @Mock
    private ActiveUserPort activeUserPort;

    @Mock
    private SeatService seatService;

    private QueueService queueService;

    private static final long ACTIVE_TTL_SECONDS = 300L;
    private static final int MAX_ACTIVE_USERS = 500;
    private static final int BATCH_SIZE = 100;
    private static final long QUEUE_TTL_SECONDS = 180L;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(
                waitingQueuePort, activeUserPort, seatService,
                ACTIVE_TTL_SECONDS, MAX_ACTIVE_USERS, BATCH_SIZE, QUEUE_TTL_SECONDS);
    }

    @Nested
    @DisplayName("enter() - 대기열 진입")
    class Enter {

        @Test
        @DisplayName("UUID를 생성하고 대기열에 등록 후 순번을 반환한다")
        void shouldEnqueueAndReturnRank() {
            // given
            given(seatService.getAvailableCount()).willReturn(10);
            given(waitingQueuePort.enqueue(anyString())).willReturn(1L);

            // when
            QueueToken token = queueService.enter();

            // then
            assertThat(token.uuid()).isNotNull().isNotBlank();
            assertThat(token.rank()).isEqualTo(1L);
            then(waitingQueuePort).should().enqueue(token.uuid());
        }

        @Test
        @DisplayName("여러 번 enter()하면 매번 다른 UUID가 생성된다")
        void shouldGenerateUniqueUuids() {
            // given
            given(seatService.getAvailableCount()).willReturn(10);
            given(waitingQueuePort.enqueue(anyString())).willReturn(1L, 2L);

            // when
            QueueToken token1 = queueService.enter();
            QueueToken token2 = queueService.enter();

            // then
            assertThat(token1.uuid()).isNotEqualTo(token2.uuid());
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
            then(waitingQueuePort).should(never()).enqueue(anyString());
        }
    }

    @Nested
    @DisplayName("getQueueToken() - 대기열 토큰 조회")
    class GetQueueToken {

        @Test
        @DisplayName("active 유저는 ACTIVE 상태를 반환한다")
        void shouldReturnActiveStatus() {
            // given
            given(activeUserPort.isActive("uuid-1")).willReturn(true);

            // when
            QueueToken token = queueService.getQueueToken("uuid-1");

            // then
            assertThat(token.status()).isEqualTo(QueueStatus.ACTIVE);
            assertThat(token.uuid()).isEqualTo("uuid-1");
        }

        @Test
        @DisplayName("대기 중인 유저는 WAITING 상태와 순번을 반환한다")
        void shouldReturnWaitingStatus() {
            // given
            given(activeUserPort.isActive("uuid-1")).willReturn(false);
            given(seatService.getAvailableCount()).willReturn(10);
            given(waitingQueuePort.getRank("uuid-1")).willReturn(42L);

            // when
            QueueToken token = queueService.getQueueToken("uuid-1");

            // then
            assertThat(token.status()).isEqualTo(QueueStatus.WAITING);
            assertThat(token.rank()).isEqualTo(42L);
        }

        @Test
        @DisplayName("대기 중인 유저의 score를 갱신한다 (잠수 방지 heartbeat)")
        void shouldRefreshScoreForWaitingUser() {
            // given
            given(activeUserPort.isActive("uuid-1")).willReturn(false);
            given(seatService.getAvailableCount()).willReturn(10);
            given(waitingQueuePort.getRank("uuid-1")).willReturn(42L);

            // when
            queueService.getQueueToken("uuid-1");

            // then
            then(waitingQueuePort).should().refreshScore("uuid-1");
        }

        @Test
        @DisplayName("대기열에 없는 유저는 QUEUE_TOKEN_NOT_FOUND 예외를 던진다")
        void shouldThrowWhenTokenNotFound() {
            // given
            given(activeUserPort.isActive("uuid-1")).willReturn(false);
            given(seatService.getAvailableCount()).willReturn(10);
            given(waitingQueuePort.getRank("uuid-1")).willReturn(null);

            // when & then
            assertThatThrownBy(() -> queueService.getQueueToken("uuid-1"))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QUEUE_TOKEN_NOT_FOUND));
        }

        @Test
        @DisplayName("매진 시 SOLD_OUT 상태를 반환한다")
        void shouldReturnSoldOutWhenNoSeatsAvailable() {
            // given
            given(activeUserPort.isActive("uuid-1")).willReturn(false);
            given(seatService.getAvailableCount()).willReturn(0);

            // when
            QueueToken token = queueService.getQueueToken("uuid-1");

            // then
            assertThat(token.status()).isEqualTo(QueueStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("active 유저는 대기열 순번 조회를 하지 않는다 (불필요한 Redis 호출 방지)")
        void shouldNotCheckRankForActiveUser() {
            // given
            given(activeUserPort.isActive("uuid-1")).willReturn(true);

            // when
            queueService.getQueueToken("uuid-1");

            // then
            then(waitingQueuePort).should(never()).getRank(anyString());
        }
    }

    @Nested
    @DisplayName("admitBatch() - 3단계 배치 입장 (peek → activate → remove)")
    class AdmitBatch {

        @Test
        @DisplayName("peek → activate → remove 순서로 실행된다")
        void shouldExecuteThreePhasesInOrder() {
            // given
            List<String> candidates = List.of("uuid-1", "uuid-2", "uuid-3");
            given(activeUserPort.countActive()).willReturn(0);
            given(seatService.getAvailableCount()).willReturn(1000);
            given(waitingQueuePort.peekBatch(eq(BATCH_SIZE), anyLong())).willReturn(candidates);

            // when
            queueService.admitBatch();

            // then - 순서 검증: peek → activateBatch → remove
            InOrder inOrder = inOrder(waitingQueuePort, activeUserPort);
            inOrder.verify(waitingQueuePort).peekBatch(eq(BATCH_SIZE), anyLong());
            inOrder.verify(activeUserPort).activateBatch(candidates, ACTIVE_TTL_SECONDS);
            inOrder.verify(waitingQueuePort).removeBatch(candidates);
        }

        @Test
        @DisplayName("activate가 모두 완료된 후에만 큐에서 제거한다 (유실 방지)")
        void shouldRemoveOnlyAfterAllActivated() {
            // 핵심: peek은 삭제하지 않으므로 crash 시 유실 없음
            // activate 후에야 remove하므로 "큐에서 빠졌는데 active 안 된" 상태 불가
            List<String> candidates = List.of("uuid-1");
            given(activeUserPort.countActive()).willReturn(0);
            given(seatService.getAvailableCount()).willReturn(1000);
            given(waitingQueuePort.peekBatch(anyInt(), anyLong())).willReturn(candidates);

            // when
            queueService.admitBatch();

            // then
            InOrder inOrder = inOrder(activeUserPort, waitingQueuePort);
            inOrder.verify(activeUserPort).activateBatch(candidates, ACTIVE_TTL_SECONDS);
            inOrder.verify(waitingQueuePort).removeBatch(candidates);
        }
    }

    @Nested
    @DisplayName("admitBatch() - active 유저 상한 제어")
    class AdmitBatchCapacity {

        @Test
        @DisplayName("maxActiveUsers에 도달하면 입장시키지 않는다")
        void shouldNotAdmitWhenMaxActiveReached() {
            // given - 이미 500명(=MAX_ACTIVE_USERS) active
            given(activeUserPort.countActive()).willReturn(500);

            // when
            queueService.admitBatch();

            // then - peek도 호출하지 않음
            then(waitingQueuePort).should(never()).peekBatch(anyInt(), anyLong());
            then(activeUserPort).should(never()).activate(anyString(), anyLong());
        }

        @Test
        @DisplayName("빈 슬롯 수만큼만 입장시킨다")
        void shouldAdmitOnlyAvailableSlots() {
            // given - 현재 480명 active → 빈 슬롯 20개
            given(activeUserPort.countActive()).willReturn(480);
            given(seatService.getAvailableCount()).willReturn(1000);
            List<String> candidates = List.of("uuid-1", "uuid-2");
            given(waitingQueuePort.peekBatch(eq(20), anyLong())).willReturn(candidates);

            // when
            queueService.admitBatch();

            // then
            then(waitingQueuePort).should().peekBatch(eq(20), anyLong());
        }

        @Test
        @DisplayName("잔여 좌석에서 active 유저 수를 차감하여 입장 인원을 결정한다")
        void shouldSubtractActiveUsersFromRemainingSeats() {
            // given - 잔여 좌석 5개, active 3명 → 입장 가능 = 5 - 3 = 2명
            given(activeUserPort.countActive()).willReturn(3);
            given(seatService.getAvailableCount()).willReturn(5);
            List<String> candidates = List.of("uuid-1", "uuid-2");
            given(waitingQueuePort.peekBatch(eq(2), anyLong())).willReturn(candidates);

            // when
            queueService.admitBatch();

            // then - 잔여 좌석(5) - active(3) = 2명만 입장
            then(waitingQueuePort).should().peekBatch(eq(2), anyLong());
            then(activeUserPort).should().activateBatch(candidates, ACTIVE_TTL_SECONDS);
        }

        @Test
        @DisplayName("잔여 좌석이 active 유저 수 이하이면 입장시키지 않는다")
        void shouldNotAdmitWhenRemainingSeatsLessThanActive() {
            // given - 잔여 좌석 3개, active 5명 → 입장 가능 = max(0, 3 - 5) = 0명
            given(activeUserPort.countActive()).willReturn(5);
            given(seatService.getAvailableCount()).willReturn(3);

            // when
            queueService.admitBatch();

            // then - 좌석 부족으로 입장 없음
            then(waitingQueuePort).should(never()).peekBatch(anyInt(), anyLong());
        }

        @Test
        @DisplayName("maxActiveUsers를 초과하면 toAdmit이 0이 된다")
        void shouldHandleOverCapacity() {
            // given - active가 maxActiveUsers를 초과한 경우 (이론적으로 가능: TTL 보정 전)
            given(activeUserPort.countActive()).willReturn(510);

            // when
            queueService.admitBatch();

            // then - Math.max(0, 500 - 510) = 0이므로 아무것도 안 함
            then(waitingQueuePort).should(never()).peekBatch(anyInt(), anyLong());
        }
    }

    @Nested
    @DisplayName("admitBatch() - batchSize 상한 제어")
    class AdmitBatchSizeLimit {

        @Test
        @DisplayName("슬롯이 충분해도 batchSize를 초과하여 입장시키지 않는다")
        void shouldNotExceedBatchSize() {
            // given - active 0명, 잔여 좌석 1000개 → 슬롯 500개이지만 batchSize=100 제한
            given(activeUserPort.countActive()).willReturn(0);
            given(seatService.getAvailableCount()).willReturn(1000);
            given(waitingQueuePort.peekBatch(eq(BATCH_SIZE), anyLong())).willReturn(List.of("uuid-1"));

            // when
            queueService.admitBatch();

            // then - peekBatch에 BATCH_SIZE(100)가 전달됨
            then(waitingQueuePort).should().peekBatch(eq(BATCH_SIZE), anyLong());
        }

        @Test
        @DisplayName("빈 슬롯이 batchSize보다 적으면 빈 슬롯만큼만 입장시킨다")
        void shouldAdmitFewerThanBatchSizeWhenSlotsLimited() {
            // given - active 470명 → 빈 슬롯 30개 < batchSize(100)
            given(activeUserPort.countActive()).willReturn(470);
            given(seatService.getAvailableCount()).willReturn(1000);
            given(waitingQueuePort.peekBatch(eq(30), anyLong())).willReturn(List.of("uuid-1"));

            // when
            queueService.admitBatch();

            // then - batchSize가 아닌 빈 슬롯(30)이 전달됨
            then(waitingQueuePort).should().peekBatch(eq(30), anyLong());
        }
    }

    @Nested
    @DisplayName("removeExpired() - 잠수 유저 제거")
    class RemoveExpired {

        @Test
        @DisplayName("cutoff 기준으로 만료 유저를 제거하고 제거 수를 반환한다")
        void shouldRemoveExpiredUsers() {
            // given
            given(waitingQueuePort.removeExpired(anyLong())).willReturn(5L);

            // when
            long removed = queueService.removeExpired();

            // then
            assertThat(removed).isEqualTo(5L);
            then(waitingQueuePort).should().removeExpired(anyLong());
        }
    }

    @Nested
    @DisplayName("admitBatch() - 대기열 비어있음")
    class AdmitBatchEmptyQueue {

        @Test
        @DisplayName("대기열이 비어있으면 activate와 remove를 실행하지 않는다")
        void shouldDoNothingWhenQueueIsEmpty() {
            // given
            given(activeUserPort.countActive()).willReturn(0);
            given(seatService.getAvailableCount()).willReturn(1000);
            given(waitingQueuePort.peekBatch(eq(BATCH_SIZE), anyLong())).willReturn(List.of());

            // when
            queueService.admitBatch();

            // then
            then(activeUserPort).should(never()).activateBatch(anyList(), anyLong());
            then(waitingQueuePort).should(never()).removeBatch(anyList());
        }
    }

    @Nested
    @DisplayName("admitBatch() - 잠수 유저 자연 회수 시나리오")
    class StaleUserRecovery {

        @Test
        @DisplayName("잠수 유저 TTL 만료 후 다음 주기에 새 유저를 입장시킨다")
        void shouldAdmitNewUsersAfterStaleUserExpiry() {
            // 시나리오:
            // 1주기: 500명 active (full) → 입장 불가
            // 잠수 유저 TTL 만료 → active 490명으로 감소
            // 2주기: 빈 슬롯 10개 → 새 유저 10명 입장

            // 1주기
            given(activeUserPort.countActive()).willReturn(500);
            queueService.admitBatch();
            then(waitingQueuePort).should(never()).peekBatch(anyInt(), anyLong());

            // 2주기 - TTL 만료로 active 감소
            given(activeUserPort.countActive()).willReturn(490);
            given(seatService.getAvailableCount()).willReturn(1000);
            given(waitingQueuePort.peekBatch(eq(10), anyLong())).willReturn(
                    List.of("new-1", "new-2", "new-3"));

            queueService.admitBatch();

            // then - 새 유저 입장
            then(activeUserPort).should().activateBatch(
                    List.of("new-1", "new-2", "new-3"), ACTIVE_TTL_SECONDS);
        }
    }
}
