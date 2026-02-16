package kr.jemi.zticket.ticket.domain;

public enum TicketStatus {
    HELD,       // 좌석 선점 완료, 결제 대기 (향후 PG 연동용)
    PAID,       // 결제 완료, Redis 동기화 대기
    SYNCED      // 모든 처리 완료 (Redis-DB 동기화 완료)
}
