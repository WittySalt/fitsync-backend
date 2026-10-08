# Scripts

Operational scripts for the backend. The ones for recording the demo are described below.
The seed and backfill SQL files carry their own usage notes in their headers.

## Recording day

Everything runs on the local stack: infra from `docker-compose.yml` (postgres, minio), the app
stack from `docker-compose.prod.yml` with the `events` profile (redis, kafka and eight services),
project name `fitsync-app`, env file `.env`. Nothing is deployed remotely. Run the commands from
the backend directory in PowerShell 5.1.

One time before the day: copy `scripts\demo-accounts.example.env` to `scripts\demo-accounts.env`
(git-ignored) and put the three real addresses in it (steady, drifting, fresh). Every script also
takes `-DryRun`, which prints the docker commands and runs nothing.

Also one time: notification-service connects as its own `notification_svc` role. Create it in the
local postgres and put the same password in `.env` as `NOTIFICATION_DB_PASSWORD`:

```powershell
docker exec -i fitsync-postgres psql -U fitsync -d fitsync -c "CREATE ROLE notification_svc LOGIN PASSWORD '<password>'; CREATE SCHEMA IF NOT EXISTS notification; GRANT USAGE, CREATE ON SCHEMA notification TO notification_svc;"
```

Locally it only logs emails (`FITSYNC_NOTIFY_EMAIL_ENABLED` stays false).

1. Bring the stack up and wait for all 12 containers to be healthy.

   ```powershell
   powershell -NoProfile -File scripts\demo-up.ps1
   ```

2. Reseed the three accounts (run after 08:00 Singapore time so today's breakfast is not in the
   future). This rebuilds the data of those three accounts only, restarts analytics-service and
   recommendation-service, waits for health and prints the checklist.

   ```powershell
   powershell -NoProfile -File scripts\demo-reseed.ps1
   ```

3. Run the coach evaluation against the live stack for the steady account. It needs a sign-in
   token for that account in a file, and the model provider the stack is configured with.

   ```powershell
   python scripts\coach-eval\run_coach_eval.py --base http://localhost:8088 --token-file .local\demo.jwt --out .local\coach-eval.md
   ```

4. Sign in with each account in turn: steady first, then drifting, then fresh.

5. Eyeball checklist, steady account unless noted:
   - Home: the guide card has something true to say for the morning.
   - Workout Today: the plan week shows Monday done and today as planned.
   - Nutrition Diary: last Sunday reads as a fasted day, yesterday is complete, today shows breakfast only.
   - Recipes: Cook for tonight lists recipes and the history shows three cooks.
   - Progress: the week rule line, the streak, the energy view and the weight trend.
   - Me: Member since about 60 days ago, the footprint grid filled in, badges earned.
   - Achievements: the paths show progress and the kitchen path counts three cooks.
   - Drifting account: Home and Progress read as slipping. Fresh account: each page shows its empty state.
   - Admin console: sign in with the admin account.

6. When finished, stop the app stack and keep the data. Add `-Infra` to stop postgres and minio too.

   ```powershell
   powershell -NoProfile -File scripts\demo-down.ps1
   ```

What the steady seed contains for a Tuesday: meals through Monday, breakfast and one glass of water
today, sessions through Monday, one past Sunday as a fasted day, three recipe cooks with the cook
counter, the streak and energy projections, and an empty badge ledger that the service fills on the
first read. The seed needs the system recipe library to be imported and stops with a clear message
when it is not.
