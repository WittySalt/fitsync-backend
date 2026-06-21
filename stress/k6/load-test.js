// FitSync API load / stress test (k6).
//
// Drives the public API Gateway the way the SPA does: log in once, then hammer
// the hot read endpoints (dashboard, current user) plus the gateway health probe.
//
// Run locally (needs a running stack + a seeded user):
//   k6 run -e BASE_URL=http://localhost:8088 \
//          -e USER_EMAIL=alice@example.com -e USER_PASSWORD='ExampleOnlyPassword123!' \
//          stress/k6/load-test.js
//
// Profiles (override with -e PROFILE=smoke|load|stress, default load):
//   smoke  - 1 VU, 30s            (sanity / wiring check)
//   load   - ramp to 20 VUs, ~3m  (expected steady-state traffic)
//   stress - ramp to 100 VUs, ~5m (find the breaking point)
//
// Thresholds below are the pass/fail gate; k6 exits non-zero if they are breached,
// so this doubles as a CI quality gate, not just a report.

import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.3/index.js';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8088').replace(/\/$/, '');
const USER_EMAIL = __ENV.USER_EMAIL || 'alice@example.com';
const USER_PASSWORD = __ENV.USER_PASSWORD || 'ExampleOnlyPassword123!';
const PROFILE = __ENV.PROFILE || 'load';

const PROFILES = {
  smoke: { stages: [{ duration: '30s', target: 1 }] },
  load: {
    stages: [
      { duration: '30s', target: 20 }, // ramp up
      { duration: '2m', target: 20 },  // steady state
      { duration: '30s', target: 0 },  // ramp down
    ],
  },
  stress: {
    stages: [
      { duration: '1m', target: 50 },
      { duration: '2m', target: 100 },
      { duration: '1m', target: 100 },
      { duration: '1m', target: 0 },
    ],
  },
};

const dashboardTrend = new Trend('fitsync_dashboard_ms', true);

export const options = {
  scenarios: {
    api: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: (PROFILES[PROFILE] || PROFILES.load).stages,
      gracefulStop: '15s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],          // <1% of all requests may fail
    http_req_duration: ['p(95)<800'],        // 95% of requests under 800ms
    checks: ['rate>0.99'],                    // >99% of functional checks pass
    fitsync_dashboard_ms: ['p(95)<1000'],    // dashboard is the heaviest read
  },
};

// Log in once per VU and reuse the JWT for the whole run.
export function setup() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: USER_EMAIL, password: USER_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
  );
  if (res.status !== 200) {
    fail(
      `login failed (${res.status}) for ${USER_EMAIL} at ${BASE_URL} — ` +
        `seed the user first or pass -e USER_EMAIL/-e USER_PASSWORD. body=${res.body}`,
    );
  }
  const token = res.json('accessToken');
  if (!token) fail('login succeeded but no accessToken in response');
  return { token };
}

export default function (data) {
  const authHeaders = {
    headers: { Authorization: `Bearer ${data.token}` },
  };

  // Gateway liveness — cheap, unauthenticated.
  const health = http.get(`${BASE_URL}/actuator/health`, { tags: { name: 'health' } });
  check(health, { 'health 200': (r) => r.status === 200 });

  // Hot authenticated reads.
  const dashboard = http.get(`${BASE_URL}/api/v1/analytics/dashboard`, {
    ...authHeaders,
    tags: { name: 'dashboard' },
  });
  dashboardTrend.add(dashboard.timings.duration);
  check(dashboard, { 'dashboard 200': (r) => r.status === 200 });

  const me = http.get(`${BASE_URL}/api/v1/auth/me`, { ...authHeaders, tags: { name: 'me' } });
  check(me, { 'me 200': (r) => r.status === 200 });

  sleep(1); // model user think-time
}

// Write machine- and human-readable artifacts for CI / the demo video.
export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'stress/k6/summary.json': JSON.stringify(data, null, 2),
    'stress/k6/summary.txt': textSummary(data, { indent: ' ', enableColors: false }),
  };
}
