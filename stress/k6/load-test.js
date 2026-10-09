// FitSync API load / stress test (k6).
//
// Drives the public API the way the SPA does: sign in once, then call the hot read endpoints (dashboard,
// current user) through the target, which for staging is CloudFront (https://staging.fitsyncapp.click).
// CloudFront forwards only /api/* to the gateway and answers every other path with the SPA's index.html
// (HTTP 200, text/html). Actuator is VPC-internal only, so nothing here calls /actuator/*: the gateway is
// proven alive by an API call it must reject itself (401 with a JSON ApiError body), and every check
// requires a JSON answer, so an HTML fallback can never count as success.
//
// Sign-in is the one the app uses. Cognito InitiateAuth (USER_PASSWORD_AUTH) returns an id token, and
// POST /api/v1/auth/bootstrap trades it for the gateway token that every other call carries. The user must
// be a real account of the pool the target uses, and the pool's app client must allow USER_PASSWORD_AUTH.
//
// Run:
//   k6 run -e BASE_URL=https://staging.fitsyncapp.click \
//          -e COGNITO_CLIENT_ID=<app client id of the pool> \
//          -e USER_EMAIL=<account email> -e USER_PASSWORD=<account password> \
//          stress/k6/load-test.js
//
// Environment:
//   BASE_URL           CloudFront or gateway base URL (default http://localhost:8088)
//   COGNITO_CLIENT_ID  required for the sign-in
//   COGNITO_REGION     default ap-southeast-1 (or set COGNITO_ENDPOINT for another endpoint)
//   USER_EMAIL, USER_PASSWORD   the account to sign in with
//   ACCESS_TOKEN       a gateway token from /api/v1/auth/bootstrap; skips the sign-in (it expires after
//                      about two hours, so it suits a smoke run rather than a long stress run)
//   With none of COGNITO_CLIENT_ID, USER_EMAIL, USER_PASSWORD and ACCESS_TOKEN set, the run is ANONYMOUS:
//   it only sends unauthenticated API reads, which the gateway rejects with a fast JSON 401. That measures
//   CloudFront plus the gateway's edge, not the services behind it. A partial set fails instead.
//
//   PROFILE            smoke | load | stress (default load)
//   RATE_PER_MIN       iterations per minute at the profile's peak (default 50). An authenticated
//                      iteration sends two API reads with the one user's token, so 50 keeps that user at
//                      100 requests a minute, under the gateway's per-user limit of about 120.
//   EXPECT_RATE_LIMIT  true to demonstrate the limiter: a JSON 429 then counts as a correct answer and
//                      the run must see at least one (fitsync_rate_limited). Default false: a 429 fails.
//
// Profiles (arrival rate, so the request rate does not depend on latency):
//   smoke  - 6 iterations a minute for 30s       (wiring check)
//   load   - ramp to RATE_PER_MIN, hold 2m, ~3m  (steady state, under the rate limit by default)
//   stress - ramp to 3 x RATE_PER_MIN, ~5m       (past the per-user limit: use EXPECT_RATE_LIMIT=true,
//                                                 or raise RATE_PER_MIN against a target without one)
//
// Thresholds below are the pass/fail gate; k6 exits non-zero if they are breached.

import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.3/index.js';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8088').replace(/\/$/, '');
const USER_EMAIL = __ENV.USER_EMAIL || '';
const USER_PASSWORD = __ENV.USER_PASSWORD || '';
const COGNITO_CLIENT_ID = __ENV.COGNITO_CLIENT_ID || '';
const COGNITO_REGION = __ENV.COGNITO_REGION || 'ap-southeast-1';
const COGNITO_ENDPOINT = __ENV.COGNITO_ENDPOINT || `https://cognito-idp.${COGNITO_REGION}.amazonaws.com/`;
const PRESET_TOKEN = __ENV.ACCESS_TOKEN || '';
const PROFILE = __ENV.PROFILE || 'load';
const RATE_PER_MIN = Math.max(1, parseInt(__ENV.RATE_PER_MIN || '50', 10) || 50);
const EXPECT_RATE_LIMIT = (__ENV.EXPECT_RATE_LIMIT || 'false').toLowerCase() === 'true';
const ANONYMOUS = !PRESET_TOKEN && !COGNITO_CLIENT_ID && !USER_EMAIL && !USER_PASSWORD;

// An API read path behind the gateway's JWT filter, outside /api/v1/auth/** (whose unauthenticated routes
// carry the stricter per-IP limit). Without a token the gateway itself must answer 401.
const PROBE_PATH = '/api/v1/analytics/dashboard';

function arrival(stages) {
  return {
    executor: 'ramping-arrival-rate',
    startRate: 0,
    timeUnit: '1m',
    preAllocatedVUs: 10,
    maxVUs: 100,
    stages,
    gracefulStop: '15s',
  };
}

const PROFILES = {
  smoke: {
    executor: 'constant-arrival-rate',
    rate: 6,
    timeUnit: '1m',
    duration: '30s',
    preAllocatedVUs: 2,
    maxVUs: 5,
  },
  load: arrival([
    { duration: '30s', target: RATE_PER_MIN },
    { duration: '2m', target: RATE_PER_MIN },
    { duration: '30s', target: 0 },
  ]),
  stress: arrival([
    { duration: '1m', target: RATE_PER_MIN },
    { duration: '2m', target: RATE_PER_MIN * 3 },
    { duration: '1m', target: RATE_PER_MIN * 3 },
    { duration: '1m', target: 0 },
  ]),
};

const dashboardTrend = new Trend('fitsync_dashboard_ms', true);
// Any HTML answer means the request never reached the gateway (CloudFront's SPA fallback).
const htmlResponses = new Counter('fitsync_html_responses');
const rateLimited = new Counter('fitsync_rate_limited');

const thresholds = {
  http_req_failed: ['rate<0.01'],          // unexpected statuses, <1% (expected 401/429 are excluded below)
  http_req_duration: ['p(95)<800'],        // 95% of requests under 800ms
  checks: ['rate>0.99'],                   // >99% of functional checks pass
  fitsync_dashboard_ms: ['p(95)<1000'],    // dashboard is the heaviest read
  fitsync_html_responses: ['count==0'],    // never accept the SPA page as an API answer
};
if (EXPECT_RATE_LIMIT) {
  thresholds.fitsync_rate_limited = ['count>0']; // the demonstration needs the limiter to engage
}

export const options = {
  scenarios: { api: PROFILES[PROFILE] || PROFILES.load },
  thresholds,
};

function isJson(r) {
  const type = r.headers['Content-Type'] || r.headers['content-type'] || '';
  return type.toLowerCase().startsWith('application/json');
}

function track(r) {
  const type = (r.headers['Content-Type'] || r.headers['content-type'] || '').toLowerCase();
  if (type.startsWith('text/html')) htmlResponses.add(1);
  if (r.status === 429) rateLimited.add(1);
}

// The gateway's ApiError: {"code":"...","message":"...","path":"...",...}.
function isApiError(r, code) {
  if (!isJson(r)) return false;
  try {
    const body = r.json();
    return body !== null && body.code === code && typeof body.message === 'string';
  } catch (e) {
    return false;
  }
}

// A 429 is a correct answer only when the run is meant to show the limiter, and only as the gateway's JSON.
function acceptableRateLimit(r) {
  return EXPECT_RATE_LIMIT && r.status === 429 && isJson(r);
}

const READ_STATUSES = EXPECT_RATE_LIMIT ? http.expectedStatuses(200, 429) : http.expectedStatuses(200);
const PROBE_STATUSES = EXPECT_RATE_LIMIT ? http.expectedStatuses(401, 429) : http.expectedStatuses(401);

function probeGateway(tagName) {
  const r = http.get(`${BASE_URL}${PROBE_PATH}`, {
    tags: { name: tagName },
    responseCallback: PROBE_STATUSES,
  });
  track(r);
  return r;
}

function gatewayAnswered(r) {
  return (r.status === 401 && isApiError(r, 'UNAUTHORIZED')) || acceptableRateLimit(r);
}

// Sign in once and share the gateway token with every VU, the way the SPA does it.
export function setup() {
  const probe = probeGateway('gateway_probe_setup');
  if (!(probe.status === 401 && isApiError(probe, 'UNAUTHORIZED'))) {
    fail(
      `the gateway did not answer at ${BASE_URL}${PROBE_PATH} (status ${probe.status}, ` +
        `content type ${probe.headers['Content-Type'] || 'none'}); an HTML 200 is CloudFront's SPA fallback`,
    );
  }

  if (ANONYMOUS) {
    console.warn(
      'ANONYMOUS run: no token and no credentials, so only unauthenticated API reads are sent. They measure ' +
        'CloudFront and the gateway edge (fast JSON 401), not the services behind it.',
    );
    return { token: '' };
  }
  if (PRESET_TOKEN) {
    return { token: PRESET_TOKEN };
  }
  if (!COGNITO_CLIENT_ID || !USER_EMAIL || !USER_PASSWORD) {
    fail(
      'sign-in needs -e COGNITO_CLIENT_ID, -e USER_EMAIL and -e USER_PASSWORD ' +
        '(or -e ACCESS_TOKEN with a token from /api/v1/auth/bootstrap, or none of them for an anonymous run)',
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
  track(bootstrap);
  if (bootstrap.status !== 200 || !isJson(bootstrap)) {
    fail(`bootstrap failed (${bootstrap.status}) at ${BASE_URL}; the target must use the same pool as the client id`);
  }
  const token = bootstrap.json('accessToken');
  if (!token) fail('bootstrap succeeded but no accessToken in response');
  return { token };
}

export default function (data) {
  if (!data.token) {
    // Anonymous: the gateway must reject the read itself, as JSON.
    const r = probeGateway('gateway_probe');
    check(r, { 'gateway answered 401 ApiError': gatewayAnswered });
    sleep(1);
    return;
  }

  // k6's JS compiler rejects object spread ({ ...authHeaders }), so keep this as a
  // plain headers object and pass it explicitly as `headers:` below.
  const authHeaders = { Authorization: `Bearer ${data.token}` };

  const dashboard = http.get(`${BASE_URL}/api/v1/analytics/dashboard`, {
    headers: authHeaders,
    tags: { name: 'dashboard' },
    responseCallback: READ_STATUSES,
  });
  track(dashboard);
  if (dashboard.status === 200) dashboardTrend.add(dashboard.timings.duration);
  check(dashboard, {
    'dashboard 200 JSON': (r) => (r.status === 200 && isJson(r)) || acceptableRateLimit(r),
  });

  const me = http.get(`${BASE_URL}/api/v1/auth/me`, {
    headers: authHeaders,
    tags: { name: 'me' },
    responseCallback: READ_STATUSES,
  });
  track(me);
  check(me, { 'me 200 JSON': (r) => (r.status === 200 && isJson(r)) || acceptableRateLimit(r) });

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
