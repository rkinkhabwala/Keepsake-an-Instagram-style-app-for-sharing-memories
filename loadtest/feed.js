// Phase 3 acceptance 1: 1,000 virtual users scrolling the home feed keep p95 under 300 ms.
// Run with: docker compose --profile loadtest run --rm k6 (tokens come from the loadtest-seed service).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const tokens = new SharedArray('tokens', () => JSON.parse(open('/loadtest/tokens.json')));
const BASE_URL = __ENV.BASE_URL || 'http://backend:8080';
const PAGES = 5;

export const options = {
  scenarios: {
    scroll: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '1m', target: 1000 },
        { duration: __ENV.HOLD || '3m', target: 1000 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    'http_req_duration{name:feed}': ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

// Each iteration opens the app and scrolls a few pages, pausing to look at each one.
export default function () {
  const params = {
    headers: { Authorization: `Bearer ${tokens[(__VU - 1) % tokens.length]}` },
    tags: { name: 'feed' },
  };
  let cursor = null;
  for (let page = 0; page < PAGES; page++) {
    const response = http.get(`${BASE_URL}/api/feed${cursor ? `?cursor=${cursor}` : ''}`, params);
    const ok = check(response, { 'feed 200': (r) => r.status === 200 });
    if (!ok) break;
    cursor = response.json('nextCursor');
    sleep(1 + Math.random() * 2);
    if (!cursor) break;
  }
}
