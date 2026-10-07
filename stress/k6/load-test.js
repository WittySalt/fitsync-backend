// FitSync API load / stress test (k6).
//
// Drives the public API Gateway the way the SPA does: sign in once, then hammer
// the hot read endpoints (dashboard, current user) plus the gateway health probe.
//
// Sign-in is the one the app uses. Cognito InitiateAuth (USER_PASSWORD_AUTH) returns an id token, and
// POST /api/v1/auth/bootstrap trades it for the gateway token that every other call carries. The old
// /api/v1/auth/login with a sample account no longer exists. The user must be a real account of the pool
// the target uses, and the pool's app client must allow USER_PASSWORD_AUTH.
//
// Run (needs a running stack, the pool's app client id and an account of that pool):
//   k6 run -e BASE_URL=https://your-host \
//          -e COGNITO_CLIENT_ID=<app client id of the pool> \
//          -e USER_EMAIL=<account email> -e USER_PASSWORD=<account password> \
//          stress/k6/load-test.js
//
// Environment:
//   BASE_URL           gateway or CloudFront host (default http://localhost:8088)
//   COGNITO_CLIENT_ID  required unless ACCESS_TOKEN is given
//   COGNITO_REGION     default ap-southeast-1 (or set COGNITO_ENDPOINT for another endpoint)
//   USER_EMAIL, USER_PASSWORD   required unless ACCESS_TOKEN is given
//   ACCESS_TOKEN       a gateway token from /api/v1/auth/bootstrap; skips the sign-in (it expires after
//                      about two hours, so it suits a smoke run rather than a long stress run)
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
const USER_EMAIL = __ENV.USER_EMAIL || '';
const USER_PASSWORD = __ENV.USER_PASSWORD || '';
const COGNITO_CLIENT_ID = __ENV.COGNITO_CLIENT_ID || '';
const COGNITO_REGION = __ENV.COGNITO_REGION || 'ap-southeast-1';
const COGNITO_ENDPOINT = __ENV.COGNITO_ENDPOINT || `https://cognito-idp.${COGNITO_REGION}.amazonaws.com/`;
const PRESET_TOKEN = __ENV.ACCESS_TOKEN || '';
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

// Sign in once and share the gateway token with every VU, the way the SPA does it.
export function setup() {
  if (PRESET_TOKEN) {
    return { token: PRESET_TOKEN };
  }
  if (!COGNITO_CLIENT_ID || !USER_EMAIL || !USER_PASSWORD) {
    fail(
      'sign-in needs -e COGNITO_CLIENT_ID, -e USER_EMAIL and -e USER_PASSWORD ' +
        '(or -e ACCESS_TOKEN with a token from /api/v1/auth/bootstrap)',
    );
  }

  // 1. Cognito InitiateAuth with the user's password, as the login page does.
  const cognito = http.post(
    COGNITO_ENDPOINT,
    JSON.stringify({
      AuthFlow: 'USER_PASSWORD_AUTH',
      ClientId: COGNITO_CLIENT_ID,
      AuthParameters: { USERNAME: USER_EMAIL, PASSWORD: USER_PASSWORD },
    }),
    {
      headers: {
        'Content-Type': 'application/x-amz-json-1.1',
        'X-Amz-Target': 'AWSCognitoIdentityProviderService.InitiateAuth',
      },
      tags: { name: 'cognito_sign_in' },
    },
  );
  if (cognito.status !== 200) {
    fail(`Cognito sign-in failed (${cognito.status}) for ${USER_EMAIL}; check the client id, the pool and the account`);
  }
  const idToken = cognito.json('AuthenticationResult.IdToken');
  if (!idToken) fail('Cognito answered without an id token (a challenge such as MFA cannot be scripted)');

  // 2. The gateway token the rest of the API expects.
  const bootstrap = http.post(`${BASE_URL}/api/v1/auth/bootstrap`, '{}', {
    headers: { Authorization: `Bearer ${idToken}`, 'Content-Type': 'application/json' },
    tags: { name: 'bootstrap' },
  });
  if (bootstrap.status !== 200) {
    fail(`bootstrap failed (${bootstrap.status}) at ${BASE_URL}; the target must use the same pool as the client id`);
  }
  const token = bootstrap.json('accessToken');
  if (!token) fail('bootstrap succeeded but no accessToken in response');
  return { token };
}

export default function (data) {
  // k6's JS compiler rejects object spread ({ ...authHeaders }), so keep this as a
  // plain headers object and pass it explicitly as `headers:` below.
  const authHeaders = { Authorization: `Bearer ${data.token}` };

  // Gateway liveness — cheap, unauthenticated.
  const health = http.get(`${BASE_URL}/actuator/health`, { tags: { name: 'health' } });
  check(health, { 'health 200': (r) => r.status === 200 });

  // Hot authenticated reads.
  const dashboard = http.get(`${BASE_URL}/api/v1/analytics/dashboard`, {
    headers: authHeaders,
    tags: { name: 'dashboard' },
  });
  dashboardTrend.add(dashboard.timings.duration);
  check(dashboard, { 'dashboard 200': (r) => r.status === 200 });

  const me = http.get(`${BASE_URL}/api/v1/auth/me`, { headers: authHeaders, tags: { name: 'me' } });
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
