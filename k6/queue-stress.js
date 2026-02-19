import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const pollSuccess = new Counter('poll_success');
const pollFail = new Counter('poll_fail');
const activated = new Counter('activated');

export const options = {
    scenarios: {
        queue_stress: {
            executor: 'constant-vus',
            vus: 5000,
            duration: '10m',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<3000'],
    },
};

export default function () {
    // 토큰 발급 (VU당 1회)
    const enterRes = http.post(`${BASE_URL}/api/queues/tokens`, null, {
        headers: { 'Content-Type': 'application/json' },
    });

    const entered = check(enterRes, {
        'enter: status 200': (r) => r.status === 200,
    });
    if (!entered) return;

    const uuid = enterRes.json('uuid');

    // ACTIVE될 때까지 폴링
    while (true) {
        const res = http.get(`${BASE_URL}/api/queues/tokens/${uuid}`);

        const ok = check(res, {
            'poll: status 200': (r) => r.status === 200,
        });

        if (ok) {
            pollSuccess.add(1);
            const status = res.json('status');
            if (status === 'ACTIVE' || status === 'SOLD_OUT') {
                activated.add(1);
                return;
            }
        } else {
            pollFail.add(1);
        }
    }
}
