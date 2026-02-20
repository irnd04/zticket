import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const enterSuccess = new Counter('enter_success');
const enterFail = new Counter('enter_fail');

export const options = {
    discardResponseBodies: true,
    scenarios: {
        enter_stress: {
            executor: 'constant-vus',
            vus: 500,
            duration: '10m',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<3000'],
    },
};

export default function () {
    const res = http.post(`${BASE_URL}/api/queues/tokens`, null, {
        headers: { 'Content-Type': 'application/json' },
    });

    const ok = check(res, {
        'status 200': (r) => r.status === 200,
    });

    if (ok) {
        enterSuccess.add(1);
    } else {
        enterFail.add(1);
    }

}
