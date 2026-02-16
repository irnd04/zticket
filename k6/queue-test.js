import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// --- Custom Metrics ---
const enterSuccess = new Counter('enter_success');
const enterFail = new Counter('enter_fail');

// --- Options ---
export const options = {
    scenarios: {
        queue_flood: {
            executor: 'per-vu-iterations',
            vus: 5000,
            iterations: 1,
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

    if (entered) {
        enterSuccess.add(1);
    } else {
        enterFail.add(1);
    }
}
