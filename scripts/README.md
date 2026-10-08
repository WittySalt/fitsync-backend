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

2. Reseed the three accounts. It runs at any hour: the three accounts share one run time (Singapore)
   and nothing is written after it, so a run at 02:00 gives today no meals yet and one after 07:30
   gives today its breakfast. This rebuilds the data of those three accounts only, restarts
   analytics-service and recommendation-service (with `.env` from this checkout, else from the main
   `backend` checkout, or `-EnvFile`), waits for health and prints the checklist and the coach-eval
   command with the run time.

   ```powershell
   powershell -NoProfile -File scripts\demo-reseed.ps1
   ```

3. Run the coach evaluation against the live stack for the steady account, on the same day as
   the reseed, with `--now` set to the reseed's run time (the reseed prints the command). It needs
   a sign-in token for that account in a file, and the model provider the stack is configured
   with. The expected numbers that move with the weekday and the hour (the streak, this week's and
   last week's training, today's breakfast) are worked out for that run time; `--print-facts`
   shows them and `--self-test` checks that arithmetic without a stack.

   ```powershell
   python scripts\coach-eval\run_coach_eval.py --now 2026-10-09T02:00 --base http://localhost:8088 --token-file .local\demo.jwt --out .local\coach-eval.md
   ```

4. Sign in with each account in turn: steady first, then drifting, then fresh.

5. Eyeball checklist, steady account unless noted (numbers in the table below):
   - Home: the streak, ending yesterday; the guide card fits the hour.
   - Workout Today: the plan days before today are done and today's plan session is still to start
     (no plan session on Tuesday, Wednesday and Sunday, nor on Thursday once the Thursday run was
     moved to Tuesday). Every rest day of the last two weeks has a short mobility session.
   - Nutrition Diary: every day logged, last Sunday a fasted day, today breakfast only after 07:30.
   - Recipes: three cooks in the history, Leftovers offers Salmon Avocado Salad (1) and Spanish
     Chicken (2).
   - Progress: the week rule line, the weight trend with yesterday's weigh-in, the energy view.
   - Me and Achievements: Member since about 60 days ago, footprint filled, badges earned.
   - Drifting account: Home and Progress read as slipping. Fresh account: empty states, first tour.
   - Admin console: sign in with the admin account.

6. When finished, stop the app stack and keep the data. Add `-Infra` to stop postgres and minio too.

   ```powershell
   powershell -NoProfile -File scripts\demo-down.ps1
   ```

## What the demo accounts show

steady trains the plan's week (Monday and Friday strength, a Thursday run, Saturday badminton), with
the Thursday run done on Tuesday in the current week once Tuesday has passed. Every rest day of the
last 14 days carries a 20 or 25 minute mobility session outside the plan, so the streak runs back
from yesterday for two weeks or more. Meals are logged every day (the most recent past Sunday is a
fasted day), weigh-ins fall every two or three days with the latest yesterday, and three recipe
cooks (3, 6 and 9 days ago) leave two dishes in Leftovers. Today has breakfast (506 kcal, 31 g
protein) once 07:30 has passed, and the first glass of water once 08:00 has. The seed needs the
system recipe library and stops with a clear message when it is not imported.

drifting followed the same plan until 13 days ago. In the last 12 days it has two shorter, lighter
sessions (10 and 4 days ago, each done for the latest plan day of its week), meals on three days
(10, 8 and 4 days ago), no weigh-in since 10 days ago and nothing at all in the last three days.
Its weight has crept back up and its goal date is six weeks out, so the goal pace reads behind.

fresh has no history, and its first-run guide state is cleared, so the first tour starts again.

Checked on a database built from the migrations, each account seeded twice per run time. "This
week" counts the sessions before today, then the plan days done out of 4.

| Run (SGT) | steady streak | steady this week   | steady today plan  | drifting this week | drifting last week       |
| --------- | ------------- | ------------------ | ------------------ | ------------------ | ------------------------ |
| Mon 10:00 | 14            | 0, 0/4             | Full Body Strength | 0, 0/4             | 1 session, 1 active day  |
| Tue 10:00 | 15            | 1, 1/4             | none               | 0, 0/4             | 1 session, 2 active days |
| Wed 10:00 | 14            | 2, 2/4             | none               | 0, 0/4             | 1 session, 2 active days |
| Thu 10:00 | 14            | 3, 2/4 (run moved) | none               | 0, 0/4             | 2 sessions, 3 active days |
| Fri 02:00 | 15            | 4, 2/4             | Full Body Strength | 1, 1/4             | 1 session, 2 active days |
| Fri 10:00 | 15            | 4, 2/4             | Full Body Strength | 1, 1/4             | 1 session, 2 active days |
| Sat 02:00 | 16            | 5, 3/4             | Badminton          | 1, 1/4             | 1 session, 2 active days |
| Sat 10:00 | 16            | 5, 3/4             | Badminton          | 1, 1/4             | 1 session, 2 active days |
| Sun 10:00 | 17            | 6, 4/4             | none               | 1, 1/4             | 2 sessions, 3 active days |

On every row steady's last full week had 7 sessions on 7 active days, its latest weigh-in is from
yesterday, its 30-day weight change is -1.6 kg and Leftovers holds two dishes; today has no meal at
02:00 and its breakfast at 10:00. drifting's streak is 0, its latest weigh-in is 10 days old, its
last meal 4 days old, its 30-day weight change +0.4 kg, and today has no meals.

On Friday 9 October 2026, seeded at 02:00, steady shows a 15-day streak. This week holds Monday's
strength, Tuesday's moved run and mobility on Wednesday and Thursday (2 of 4 plan days), and
Friday's Full Body Strength is waiting to start. Today has no meals yet, the last weigh-in was
yesterday, and two dishes wait in Leftovers. The coach sees last week's goal met (7 active days
against 4), the weight goal on course and the 22.5 kg bench press record. drifting shows a streak
of 0 and Monday's session only (1 of 4, Thursday missed), with Full Body Strength today, no weigh-in
for 10 days and no meals for 4. Its coach sees last week short of the goal (2 active days against
4), a logging reminder and the goal pace behind.

On Saturday 10 October 2026 steady's streak is 16, Friday's strength is done (3 of 4) and Badminton
is today's plan. For drifting today's plan is Badminton and Thursday and Friday read as missed; the
rest is as on Friday.
