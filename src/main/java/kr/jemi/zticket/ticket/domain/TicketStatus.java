package kr.jemi.zticket.ticket.domain;

public enum TicketStatus {
    PAID,       // 결제 완료, Redis 동기화 대기
    SYNCED      // 모든 처리 완료 (Redis-DB 동기화 완료)
}
