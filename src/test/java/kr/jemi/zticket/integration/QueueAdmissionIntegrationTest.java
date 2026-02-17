package kr.jemi.zticket.integration;

import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.common.exception.ErrorCode;
import kr.jemi.zticket.queue.application.port.in.AdmitUsersUseCase;
import kr.jemi.zticket.queue.application.port.in.EnterQueueUseCase;
import kr.jemi.zticket.queue.application.port.in.GetQueueTokenUseCase;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.queue.domain.QueueStatus;
import kr.jemi.zticket.queue.domain.QueueToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueAdmissionIntegrationTest extends IntegrationTestBase {

    @Autowired
    EnterQueueUseCase enterQueueUseCase;

    @Autowired
    GetQueueTokenUseCase getQueueTokenUseCase;

    @Autowired
    AdmitUsersUseCase admitUsersUseCase;

    @Autowired
    ActiveUserPort activeUserPort;

    @Test
    @DisplayName("대기열 진입 + 순번: 여러 유저 enter -> 고유 순번 할당")
    void enter_and_rank_order() {
        QueueToken t1 = enterQueueUseCase.enter();
        QueueToken t2 = enterQueueUseCase.enter();
        QueueToken t3 = enterQueueUseCase.enter();

        assertThat(List.of(t1, t2, t3))
                .extracting(QueueToken::rank)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("배치 입장: enter 후 admitBatch -> active 상태 전환")
    void admitBatch_activates_users() {
        List<QueueToken> tokens = List.of(
                enterQueueUseCase.enter(),
                enterQueueUseCase.enter(),
                enterQueueUseCase.enter()
        );

        admitUsersUseCase.admitBatch(2);

        assertThat(tokens)
                .filteredOn(t -> activeUserPort.isActive(t.uuid()))
                .as("3명 중 2명만 active")
                .hasSize(2);
    }

    @Test
    @DisplayName("상태 조회: WAITING -> ACTIVE 라이프사이클")
    void status_lifecycle_waiting_active() {
        QueueToken token = enterQueueUseCase.enter();

        assertThat(getQueueTokenUseCase.getQueueToken(token.uuid()).status())
                .as("WAITING")
                .isEqualTo(QueueStatus.WAITING);

        admitUsersUseCase.admitBatch(1);

        assertThat(getQueueTokenUseCase.getQueueToken(token.uuid()).status())
                .as("ACTIVE")
                .isEqualTo(QueueStatus.ACTIVE);
    }

    @Test
    @DisplayName("active TTL 만료 후 상태 조회 시 QUEUE_TOKEN_NOT_FOUND 예외")
    void status_after_active_ttl_expired_throws() {
        QueueToken token = enterQueueUseCase.enter();
        admitUsersUseCase.admitBatch(1);

        // active 키 직접 삭제하여 TTL 만료 시뮬레이션
        redisTemplate.delete("active_user:" + token.uuid());

        assertThatThrownBy(() -> getQueueTokenUseCase.getQueueToken(token.uuid()))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QUEUE_TOKEN_NOT_FOUND));
    }

    @Test
    @DisplayName("입장 상한: maxActiveUsers(10) 도달 시 추가 입장 불가")
    void admitBatch_respects_max_active_users() {
        List<QueueToken> tokens = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            tokens.add(enterQueueUseCase.enter());
        }

        admitUsersUseCase.admitBatch(15);

        assertThat(activeUserPort.countActive())
                .as("active 유저 수")
                .isEqualTo(10);

        assertThat(tokens)
                .filteredOn(t -> activeUserPort.isActive(t.uuid()))
                .as("15명 중 10명만 active")
                .hasSize(10);
    }
}
