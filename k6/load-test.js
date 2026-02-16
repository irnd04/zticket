import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// --- Custom Metrics ---
const purchaseSuccess = new Counter('purchase_success');
const purchaseFail = new Counter('purchase_fail');
const queueWaitTime = new Trend('queue_wait_time', true);

// --- Options ---
export const options = {
    scenarios: {
        ticket_rush: {
            executor: 'per-vu-iterations',
            vus: 5000,
            iterations: 1,          // VU당 1회만 실행
            maxDuration: '5m',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<3000'],
    },
};

export default function () {
    // === 1. 대기열 진입 ===
    const enterRes = http.post(`${BASE_URL}/api/queues/tokens`, null, {
        headers: { 'Content-Type': 'application/json' },
    });

    const entered = check(enterRes, {
        'enter: status 200': (r) => r.status === 200,
        'enter: uuid exists': (r) => r.json('uuid') !== undefined,
    });
    if (!entered) return;

    const uuid = enterRes.json('uuid');
    const rank = enterRes.json('rank');

    // === 2. 대기열 폴링 (ACTIVE될 때까지) ===
    const waitStart = Date.now();
    let active = false;

    for (let i = 0; i < 150; i++) {  // 최대 5분 (120 * 2초)
        sleep(2);

        const statusRes = http.get(`${BASE_URL}/api/queues/tokens/${uuid}`);
        const status = statusRes.json('status');

        if (status === 'ACTIVE') {
            active = true;
            break;
        }
        if (status === 'SOLD_OUT') {
            return;
        }
    }

    queueWaitTime.add(Date.now() - waitStart);
    if (!active) return;

    // === 3. 좌석 조회 ===
    const seatsRes = http.get(`${BASE_URL}/api/seats`);

    check(seatsRes, {
        'seats: status 200': (r) => r.status === 200,
    });

    const seats = seatsRes.json();
    const available = seats.filter((s) => s.status === 'available');

    if (available.length === 0) {
        return;  // 매진
    }

    // 랜덤 좌석 선택
    const seat = available[Math.floor(Math.random() * available.length)];

    // === 4. 티켓 구매 ===
    const purchaseRes = http.post(
        `${BASE_URL}/api/tickets`,
        JSON.stringify({ seatNumber: seat.seatNumber }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-Queue-Token': uuid,
            },
        }
    );

    const purchased = check(purchaseRes, {
        'purchase: status 200': (r) => r.status === 200,
    });

    if (purchased) {
        purchaseSuccess.add(1);
    } else {
        purchaseFail.add(1);
    }
}
