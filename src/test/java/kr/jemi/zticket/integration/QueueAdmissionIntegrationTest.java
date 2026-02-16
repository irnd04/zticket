package kr.jemi.zticket.integration;

import kr.jemi.zticket.application.port.in.AdmitUsersUseCase;
import kr.jemi.zticket.application.port.in.EnterQueueUseCase;
import kr.jemi.zticket.application.port.in.GetQueueStatusUseCase;
import kr.jemi.zticket.application.port.out.ActiveUserPort;
import kr.jemi.zticket.domain.queue.QueueToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class QueueAdmissionIntegrationTest extends IntegrationTestBase {

    @Autowired
    EnterQueueUseCase enterQueueUseCase;

    @Autowired
    GetQueueStatusUseCase getQueueStatusUseCase;

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
    @DisplayName("상태 조회: WAITING -> ACTIVE -> EXPIRED 전체 라이프사이클")
    void status_lifecycle_waiting_active_expired() {
        QueueToken token = enterQueueUseCase.enter();

        assertThat(getQueueStatusUseCase.getStatus(token.uuid()).rank())
                .as("WAITING: rank > 0")
                .isPositive();

        admitUsersUseCase.admitBatch(1);

        assertThat(getQueueStatusUseCase.getStatus(token.uuid()).rank())
                .as("ACTIVE: rank == 0")
                .isZero();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(getQueueStatusUseCase.getStatus(token.uuid()).rank())
                                .as("EXPIRED: rank == -1")
                                .isEqualTo(-1)
                );
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
