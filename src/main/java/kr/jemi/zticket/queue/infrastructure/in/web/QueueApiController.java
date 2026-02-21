package kr.jemi.zticket.queue.infrastructure.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.jemi.zticket.queue.infrastructure.in.web.dto.QueueStatusResponse;
import kr.jemi.zticket.queue.infrastructure.in.web.dto.TokenResponse;
import kr.jemi.zticket.queue.application.port.in.EnterQueueUseCase;
import kr.jemi.zticket.queue.application.port.in.GetQueueTokenUseCase;
import kr.jemi.zticket.queue.domain.QueueToken;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Queue", description = "대기열 진입 및 상태 조회")
@RestController
public class QueueApiController {

    private final EnterQueueUseCase enterQueueUseCase;
    private final GetQueueTokenUseCase getQueueTokenUseCase;

    public QueueApiController(EnterQueueUseCase enterQueueUseCase,
                              GetQueueTokenUseCase getQueueTokenUseCase) {
        this.enterQueueUseCase = enterQueueUseCase;
        this.getQueueTokenUseCase = getQueueTokenUseCase;
    }

    @Operation(summary = "대기열 진입", description = "대기열에 진입하고 대기열 토큰과 대기 순번을 반환합니다.")
    @PostMapping("/api/queues/tokens")
    public ResponseEntity<TokenResponse> enter() {
        QueueToken queueToken = enterQueueUseCase.enter();
        return ResponseEntity.ok(new TokenResponse(queueToken.token(), queueToken.rank()));
    }

    @Operation(summary = "대기열 상태 조회", description = "대기열 토큰으로 현재 대기 상태와 순번을 조회합니다.")
    @GetMapping("/api/queues/tokens/{token}")
    public ResponseEntity<QueueStatusResponse> getQueueToken(@PathVariable String token) {
        QueueToken queueToken = getQueueTokenUseCase.getQueueToken(token);
        return ResponseEntity.ok(QueueStatusResponse.from(queueToken));
    }
}
