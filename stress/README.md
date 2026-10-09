# Stress / Load Testing (k6)

Performance tests for the FitSync API, written in [k6](https://k6.io/). They drive
the **API Gateway** exactly like the SPA does: authenticate once, then exercise the
hot read paths (`/api/v1/analytics/dashboard`, `/api/v1/auth/me`) at a set request rate.

The cloud targets are CloudFront addresses (staging: `https://staging.fitsyncapp.click`). CloudFront
forwards only `/api/*` to the gateway and answers every other path with the SPA's `index.html` (HTTP 200,
`text/html`). Actuator is VPC-internal only: the ALB health-checks the gateway on `/actuator/health`
inside the VPC, and nothing in this script calls `/actuator/*`. Instead:

- **Gateway check.** `setup()` sends `GET /api/v1/analytics/dashboard` without a token and requires a 401
  with a JSON content type and the gateway's `ApiError` body (`code` = `UNAUTHORIZED`, a `message`). The
  run stops at once if the answer is anything else, such as the SPA page.
- **JSON only.** Every check also requires `application/json`, and the `fitsync_html_responses`
  threshold (`count==0`) fails the run on a single HTML answer, so a CloudFront fallback can never count
  as success.
- **Expected statuses.** The probe's 401 (and a 429 when `EXPECT_RATE_LIMIT=true`) are marked as
  expected, so they do not count towards `http_req_failed`.

The thresholds in [`k6/load-test.js`](k6/load-test.js) are a real pass/fail gate —
k6 exits non-zero when they are breached, so the same script is both the report and
the gate used in CI.

## Profiles

The profiles set an arrival rate (iterations per minute), so the request rate stays the same however
fast the target answers. An authenticated iteration sends two reads (dashboard and current user).

| Profile  | Rate                                   | Purpose                                   |
|----------|----------------------------------------|-------------------------------------------|
| `smoke`  | 6 iterations/min for 30s               | wiring check                              |
| `load`   | ramp to `RATE_PER_MIN`, hold 2m, ~3m   | steady state, under the rate limit        |
| `stress` | ramp to 3 × `RATE_PER_MIN`, ~5m        | past the per-user limit (see below)       |

## Rate limit

The gateway limits about **120 requests a minute per user** and **10 a minute per IP** on the
unauthenticated auth routes, and answers the excess with **429**. The script signs in once and shares
that one user's token across every VU, so the per-user limit is the one a run meets.

- `RATE_PER_MIN` (default **50**) is the iterations per minute at the profile's peak. 50 × 2 reads = 100
  requests a minute for the user, under the limit, so the default `load` run measures the API rather than
  the limiter. Keep `RATE_PER_MIN` at 60 or below to stay under it.
- `EXPECT_RATE_LIMIT=true` turns a run into a demonstration of the limiter: a JSON 429 then counts as a
  correct answer, and the `fitsync_rate_limited` threshold requires at least one. Use it with the `stress`
  profile (150 iterations, 300 requests a minute at the default rate). Without it, a 429 fails the checks.
- The sign-in sends one `POST /api/v1/auth/bootstrap` per run, far below the per-IP limit. The gateway
  probe uses `/api/v1/analytics/dashboard`, outside `/api/v1/auth/**`, so it does not touch that limit.

## Run locally

Needs a running stack and an account of the Cognito pool the target uses (the demo accounts of
`scripts/demo-accounts.example.env` are accounts of that kind). The script signs in the way the app does:
Cognito `InitiateAuth` with `USER_PASSWORD_AUTH`, then `POST /api/v1/auth/bootstrap` for the gateway token. The
pool's app client therefore has to allow `USER_PASSWORD_AUTH`, and the target has to validate tokens of that pool.

```bash
# install k6: https://grafana.com/docs/k6/latest/set-up/install-k6/
k6 run -e BASE_URL=http://localhost:8088 \
       -e COGNITO_CLIENT_ID=<app client id of the pool> \
       -e USER_EMAIL=<account email> \
       -e USER_PASSWORD=<account password> \
       -e PROFILE=load \
       stress/k6/load-test.js
```

To skip the sign-in, pass `-e ACCESS_TOKEN=<gateway token>` taken from `/api/v1/auth/bootstrap`. It expires after
about two hours, so it suits a smoke run. There is no stored token secret; the CI job signs in with the
`STRESS_USER_*` secrets.

With none of `COGNITO_CLIENT_ID`, `USER_EMAIL`, `USER_PASSWORD` and `ACCESS_TOKEN` set, the run is
**anonymous**: every iteration sends one unauthenticated API read and expects the gateway's fast JSON
401. That exercises CloudFront and the gateway's edge (routing, TLS, the JWT filter), not the services
behind it, and the script says so in its output. Giving only some of the four values fails the run
instead, so a misconfigured job is not mistaken for an anonymous one. `COGNITO_REGION` (default `ap-southeast-1`) or `COGNITO_ENDPOINT` points the
script at another region or a local stand-in.

Or without installing k6, via Docker (mount the repo so artifacts are written back):

```bash
docker run --rm -i -v "$PWD:/work" -w /work \
  -e BASE_URL=https://your-host -e USER_EMAIL=... -e USER_PASSWORD=... -e PROFILE=stress \
  grafana/k6:0.49.0 run stress/k6/load-test.js
```

## Run in CI

The `stress-test` job in `.github/workflows/cd.yml` runs the same script against a live
target. It is **manual** (it needs a deployed environment): Actions → CD → **Run workflow**
with *Run k6 stress test against staging* ticked. It is configured with repository
variables and secrets:

- `STRESS_TARGET_URL` — base URL to hit. For staging this is `https://staging.fitsyncapp.click`: the ALB accepts CloudFront only, and CloudFront forwards `/api/*`
- `STRESS_USER_EMAIL`, `STRESS_USER_PASSWORD` — credentials of an account of the target's Cognito pool
- the job also has to pass the pool's app client id as `-e COGNITO_CLIENT_ID=...` (a new `STRESS_COGNITO_CLIENT_ID`
  variable); `.github/workflows/cd.yml` does not pass it yet (it is a protected file), so the CI job cannot
  sign in until that line is added
- `STRESS_PROFILE` — `smoke` | `load` | `stress` (default `load`)
- the job does not pass `RATE_PER_MIN` or `EXPECT_RATE_LIMIT` yet, so CI runs use the defaults (50 a
  minute, a 429 fails); add `-e RATE_PER_MIN=... -e EXPECT_RATE_LIMIT=...` to the `k6 run` line in
  `cd.yml` to change them

Artifacts produced: `stress/k6/summary.json`, `stress/k6/summary.txt` (attach these
to the report / CI demo video as proof of stress testing).
