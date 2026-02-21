package kr.jemi.zticket.queue.application.service;

import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.queue.application.port.out.AvailableSeatCountPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AdmissionServiceTest {

    @Mock
    private WaitingQueueOperator waitingQueueOperator;

    @Mock
    private ActiveUserPort activeUserPort;

    @Mock
    private AvailableSeatCountPort availableSeatCountPort;

    private AdmissionService admissionService;

    private static final long ACTIVE_TTL_SECONDS = 300L;
    private static final int MAX_ACTIVE_USERS = 500;
    private static final int BATCH_SIZE = 100;
    private static final int FIND_EXPIRED_BATCH_SIZE = 5000;

    @BeforeEach
    void setUp() {
        admissionService = new AdmissionService(
                waitingQueueOperator, activeUserPort, availableSeatCountPort,
                ACTIVE_TTL_SECONDS, MAX_ACTIVE_USERS, BATCH_SIZE);
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
            given(availableSeatCountPort.getAvailableCount()).willReturn(1000);
            given(waitingQueueOperator.peek(BATCH_SIZE)).willReturn(candidates);

            // when
            admissionService.admitBatch();

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
            given(availableSeatCountPort.getAvailableCount()).willReturn(1000);
            given(waitingQueueOperator.peek(anyInt())).willReturn(candidates);

            // when
            admissionService.admitBatch();

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
            given(availableSeatCountPort.getAvailableCount()).willReturn(1000);
            given(waitingQueueOperator.peek(BATCH_SIZE)).willReturn(List.of("token-1"));

            // when
            admissionService.admitBatch();

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
            admissionService.admitBatch();

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
            given(availableSeatCountPort.getAvailableCount()).willReturn(1000);
            List<String> candidates = List.of("token-1", "token-2");
            given(waitingQueueOperator.peek(20)).willReturn(candidates);

            // when
            admissionService.admitBatch();

            // then
            then(waitingQueueOperator).should().peek(20);
        }

        @Test
        @DisplayName("잔여 좌석에서 active 유저 수를 차감하여 입장 인원을 결정한다")
        void shouldSubtractActiveUsersFromRemainingSeats() {
            // given - 잔여 좌석 5개, active 3명 → 입장 가능 = 2명
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(3);
            given(availableSeatCountPort.getAvailableCount()).willReturn(5);
            List<String> candidates = List.of("token-1", "token-2");
            given(waitingQueueOperator.peek(2)).willReturn(candidates);

            // when
            admissionService.admitBatch();

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
            given(availableSeatCountPort.getAvailableCount()).willReturn(3);

            // when
            admissionService.admitBatch();

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
            admissionService.admitBatch();

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
            given(availableSeatCountPort.getAvailableCount()).willReturn(1000);
            given(waitingQueueOperator.peek(BATCH_SIZE)).willReturn(List.of("token-1"));

            // when
            admissionService.admitBatch();

            // then
            then(waitingQueueOperator).should().peek(BATCH_SIZE);
        }

        @Test
        @DisplayName("빈 슬롯이 batchSize보다 적으면 빈 슬롯만큼만 입장시킨다")
        void shouldAdmitFewerThanBatchSizeWhenSlotsLimited() {
            // given - active 470명 → 빈 슬롯 30개
            given(waitingQueueOperator.findExpired(FIND_EXPIRED_BATCH_SIZE)).willReturn(List.of());
            given(activeUserPort.countActive()).willReturn(470);
            given(availableSeatCountPort.getAvailableCount()).willReturn(1000);
            given(waitingQueueOperator.peek(30)).willReturn(List.of("token-1"));

            // when
            admissionService.admitBatch();

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
            given(availableSeatCountPort.getAvailableCount()).willReturn(1000);
            given(waitingQueueOperator.peek(BATCH_SIZE)).willReturn(List.of());

            // when
            admissionService.admitBatch();

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
            admissionService.admitBatch();
            then(waitingQueueOperator).should(never()).peek(anyInt());

            // 2주기 - TTL 만료로 active 감소
            given(activeUserPort.countActive()).willReturn(490);
            given(availableSeatCountPort.getAvailableCount()).willReturn(1000);
            List<String> newCandidates = List.of("new-1", "new-2", "new-3");
            given(waitingQueueOperator.peek(10)).willReturn(newCandidates);

            admissionService.admitBatch();

            // then
            then(activeUserPort).should().activateBatch(
                    List.of("new-1", "new-2", "new-3"), ACTIVE_TTL_SECONDS);
        }
    }
}
