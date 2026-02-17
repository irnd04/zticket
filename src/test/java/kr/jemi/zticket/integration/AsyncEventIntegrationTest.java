package kr.jemi.zticket.integration;

import kr.jemi.zticket.ticket.domain.TicketPaidEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(OutputCaptureExtension.class)
class AsyncEventIntegrationTest extends IntegrationTestBase {

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("비동기 이벤트 리스너 실패 시 호출자에게 예외가 전파되지 않고 에러 로그가 남는다")
    void asyncFailureDoesNotPropagateAndLogsError(CapturedOutput output) {
        // given - 존재하지 않는 ticketUuid로 이벤트 발행
        // 리스너에서 IllegalStateException("티켓 없음: ...") 발생

        // when - 예외 전파 없이 정상 리턴
        eventPublisher.publishEvent(new TicketPaidEvent("non-existent-uuid"));

        // then - AsyncUncaughtExceptionHandler가 에러 로그를 남김
        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(output.getAll()).contains("비동기 작업 실패")
        );
    }
}
