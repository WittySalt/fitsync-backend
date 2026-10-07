# Stress / Load Testing (k6)

Performance tests for the FitSync API, written in [k6](https://k6.io/). They drive
the **API Gateway** exactly like the SPA does: authenticate once, then exercise the
hot read paths (`/api/v1/analytics/dashboard`, `/api/v1/auth/me`) plus the gateway
health probe under increasing concurrency.

The thresholds in [`k6/load-test.js`](k6/load-test.js) are a real pass/fail gate —
k6 exits non-zero when they are breached, so the same script is both the report and
the gate used in CI.

## Profiles

| Profile  | Load                 | Purpose                          |
|----------|----------------------|----------------------------------|
| `smoke`  | 1 VU, 30s            | sanity / wiring check            |
| `load`   | ramp → 20 VUs, ~3m   | expected steady-state traffic    |
| `stress` | ramp → 100 VUs, ~5m  | find the breaking point          |

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
about two hours, so it suits a smoke run. `COGNITO_REGION` (default `ap-southeast-1`) or `COGNITO_ENDPOINT` points the
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

- `STRESS_TARGET_URL` — base URL of the gateway/host to hit
- `STRESS_USER_EMAIL`, `STRESS_USER_PASSWORD` — credentials of an account of the target's Cognito pool
- the job also has to pass the pool's app client id as `-e COGNITO_CLIENT_ID=...` (a new `STRESS_COGNITO_CLIENT_ID`
  variable); `.github/workflows/cd.yml` does not pass it yet (it is a protected file), so the CI job cannot
  sign in until that line is added
- `STRESS_PROFILE` — `smoke` | `load` | `stress` (default `load`)

Artifacts produced: `stress/k6/summary.json`, `stress/k6/summary.txt` (attach these
to the report / CI demo video as proof of stress testing).
