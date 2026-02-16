package kr.jemi.zticket.adapter.in.web;

import kr.jemi.zticket.adapter.in.web.dto.QueueStatusResponse;
import kr.jemi.zticket.adapter.in.web.dto.TokenResponse;
import kr.jemi.zticket.application.port.in.EnterQueueUseCase;
import kr.jemi.zticket.application.port.in.GetQueueStatusUseCase;
import kr.jemi.zticket.domain.queue.QueueToken;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue")
public class QueueApiController {

    private final EnterQueueUseCase enterQueueUseCase;
    private final GetQueueStatusUseCase getQueueStatusUseCase;

    public QueueApiController(EnterQueueUseCase enterQueueUseCase,
                              GetQueueStatusUseCase getQueueStatusUseCase) {
        this.enterQueueUseCase = enterQueueUseCase;
        this.getQueueStatusUseCase = getQueueStatusUseCase;
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> enter() {
        QueueToken token = enterQueueUseCase.enter();
        return ResponseEntity.ok(new TokenResponse(token.uuid(), token.rank()));
    }

    @GetMapping("/status/{uuid}")
    public ResponseEntity<QueueStatusResponse> getStatus(@PathVariable String uuid) {
        QueueToken token = getQueueStatusUseCase.getStatus(uuid);
        return ResponseEntity.ok(QueueStatusResponse.from(token));
    }
}
