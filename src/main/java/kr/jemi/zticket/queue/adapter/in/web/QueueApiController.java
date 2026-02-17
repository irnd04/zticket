package kr.jemi.zticket.queue.adapter.in.web;

import kr.jemi.zticket.queue.adapter.in.web.dto.QueueStatusResponse;
import kr.jemi.zticket.queue.adapter.in.web.dto.TokenResponse;
import kr.jemi.zticket.queue.application.port.in.EnterQueueUseCase;
import kr.jemi.zticket.queue.application.port.in.GetQueueTokenUseCase;
import kr.jemi.zticket.queue.domain.QueueToken;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueueApiController {

    private final EnterQueueUseCase enterQueueUseCase;
    private final GetQueueTokenUseCase getQueueTokenUseCase;

    public QueueApiController(EnterQueueUseCase enterQueueUseCase,
                              GetQueueTokenUseCase getQueueTokenUseCase) {
        this.enterQueueUseCase = enterQueueUseCase;
        this.getQueueTokenUseCase = getQueueTokenUseCase;
    }

    @PostMapping("/api/queues/tokens")
    public ResponseEntity<TokenResponse> enter() {
        QueueToken token = enterQueueUseCase.enter();
        return ResponseEntity.ok(new TokenResponse(token.uuid(), token.rank()));
    }

    @GetMapping("/api/queues/tokens/{uuid}")
    public ResponseEntity<QueueStatusResponse> getQueueToken(@PathVariable String uuid) {
        QueueToken token = getQueueTokenUseCase.getQueueToken(uuid);
        return ResponseEntity.ok(QueueStatusResponse.from(token));
    }
}
