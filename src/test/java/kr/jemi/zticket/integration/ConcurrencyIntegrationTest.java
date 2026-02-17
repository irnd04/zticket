package kr.jemi.zticket.integration;

import kr.jemi.zticket.ticket.adapter.out.persistence.TicketJpaEntity;
import kr.jemi.zticket.ticket.application.port.in.PurchaseTicketUseCase;
import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import kr.jemi.zticket.ticket.application.port.out.TicketPort;
import kr.jemi.zticket.common.exception.BusinessException;
import kr.jemi.zticket.ticket.domain.Ticket;
import kr.jemi.zticket.ticket.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class ConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired
    PurchaseTicketUseCase purchaseTicketUseCase;

    @Autowired
    ActiveUserPort activeUserPort;

    @Autowired
    TicketPort ticketPort;

    @Test
    @DisplayName("동시 구매 경쟁: 100개 스레드 중 정확히 1개만 성공")
    void concurrent_purchase_only_one_succeeds() throws InterruptedException {
        int seatNumber = 1;
        int threadCount = 100;

        List<String> tokens = IntStream.range(0, threadCount)
                .mapToObj(i -> "token-" + i)
                .toList();
        tokens.forEach(t -> activeUserPort.activate(t, 300));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (String token : tokens) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    purchaseTicketUseCase.purchase(token, seatNumber);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(successCount).as("성공 수").hasValue(1);
        assertThat(failCount).as("실패 수").hasValue(threadCount - 1);

        // 비동기 후처리 완료 대기
        await().atMost(5, SECONDS).untilAsserted(() -> {
            String winnerToken = redisTemplate.opsForValue().get("seat:" + seatNumber);

            assertThat(winnerToken)
                    .as("Redis seat 키")
                    .startsWith("paid:");

            // 성공한 토큰 추출
            String winner = winnerToken.substring("paid:".length());

            assertThat(ticketPort.findByStatus(TicketStatus.SYNCED))
                    .as("DB SYNCED 티켓")
                    .singleElement()
                    .extracting(Ticket::getSeatNumber)
                    .isEqualTo(seatNumber);

            assertThat(activeUserPort.isActive(winner))
                    .as("active 유저 제거")
                    .isFalse();
        });
    }

    @Test
    @DisplayName("seatNumber UNIQUE 제약: 같은 좌석번호 중복 INSERT 실패")
    void duplicate_seatNumber_throws_DataIntegrityViolation() {
        ticketJpaRepository.saveAndFlush(TicketJpaEntity.fromDomain(Ticket.create("token-1", 1)));

        assertThatThrownBy(() ->
                ticketJpaRepository.saveAndFlush(TicketJpaEntity.fromDomain(Ticket.create("token-2", 1)))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
