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
   - Home: the streak, ending yesterday; the weekly goal is six sessions and every session counts,
     mobility included, so on Friday it reads four in, two to go. There is no Getting started
     card for steady and drifting, and no tour starts by itself for them.
   - Workout Today: "this week" counts the days trained against those trained or still planned (4 of 6
     on Friday, 5 of 6 on Saturday); the plan days before today are done and today's plan session is still to start
     (no plan session on Tuesday, Wednesday and Sunday, nor on Thursday once the Thursday run was
     moved to Tuesday). Every rest day of the last two weeks has a short mobility session.
   - Nutrition Diary: every day logged, last Sunday a fasted day, today breakfast only after 07:30.
   - Recipes: three cooks in the history, Leftovers offers Salmon Avocado Salad (1) and Spanish
     Chicken (2).
   - Progress: the week rule line, the weight trend with yesterday's weigh-in, the energy view.
   - Me and Achievements: Member since the first day of the seeded history (59 days before the run
     day, 11 Aug for a Friday 9 Oct run), footprint filled from that day, badges earned, among them
     Full week and Four full weeks.
   - Home month panel: the last full month (September for an October run) reads 3 of 3 full weeks on
     goal; the current month has no full week until its first Sunday has passed.
   - Drifting account (Alex Tan): Home and Progress read as slipping. Fresh account (Sam Lee): empty
     states, first tour.
   - Admin console: sign in with the admin account.

6. When finished, stop the app stack and keep the data. Add `-Infra` to stop postgres and minio too.

   ```powershell
   powershell -NoProfile -File scripts\demo-down.ps1
   ```

## Cloud reseed

`scripts\demo-reseed-cloud.ps1` seeds the same three accounts on the staging or prod AWS
environment. It copies `demo-cloud-remote.sh` and the seed to a private temp dir on the EC2 host
with scp over SSM Session Manager (the host has no SSH port open), runs the seed there as the RDS master user (the password is read from Secrets Manager on
the host and never printed), restarts analytics-service and recommendation-service, waits until both
are healthy and prints the same checklist as the local script. The temp dir is removed afterwards.

Without `-Apply` the script is a dry run. It only reads: it checks each account and the system
catalogues and prints the rows each account has now, which are the rows the seed would replace.
Because the seed deletes the account's data, always run the dry run first.

Before the first run:

- Install the AWS CLI v2 and the Session Manager plugin, and use AWS credentials that may start an
  `AWS-StartSSHSession` session to the host (the `ssm_operator_policy_arn` output of that workspace,
  or an admin profile). `infra\terraform\RUNBOOK.md` "SSH over SSM" has the install steps and an
  `~/.ssh/config` example for connecting by hand.
- Copy `scripts\demo-cloud.example.env` to `scripts\demo-cloud.env` (git-ignored) and fill in the
  EC2 instance id (`terraform output -raw ec2_instance_id`), the SSH key path and the app secret id
  of each environment. An older file with `*_HOST` lines is refused with a message naming the new
  key. The addresses come from
  `scripts\demo-accounts.env`, as for the local script.
- Sign in once on that environment with every account, so it exists in `auth.app_users`.
- No account may have a pending deletion. Signing in again cancels it.
- None of them may be the admin account. The script refuses to seed an ADMIN.
- The system catalogues must be present: the USDA foods (nutrition V900/V901 seeds) and the system
  recipe library. The exercise catalogue is optional, because the seed brings its own exercises when
  it is missing.

The dry run stops with a message naming the account or catalogue that blocks the run.

```powershell
powershell -NoProfile -File scripts\demo-reseed-cloud.ps1 -Env staging
```

When it passes, run it again with `-Apply`. `-Now 2026-10-09T09:30` pins the run time (Singapore,
default now) and `-Persona steady,drifting` seeds only those accounts.

```powershell
powershell -NoProfile -File scripts\demo-reseed-cloud.ps1 -Env staging -Apply
powershell -NoProfile -File scripts\demo-reseed-cloud.ps1 -Env prod -Apply
```

## Coach evaluation on a cloud environment

The coach evaluation runs against staging or prod the same way it runs locally, with `--base` set
to the environment's address and a sign-in token of the steady account on that environment. Two
limits stand in the way, and the run handles one of them while the other has to be raised by hand.

The API gateway allows each user 120 requests a minute and answers the rest with a 429 (code
`RATE_LIMITED`). A full run makes a little over 200 calls, three for each chat question, so it
would hit that limit in about a minute. Pass `--pace 0.6` to space the calls 0.6 s apart, which
keeps a run at 100 a minute at most. A 429 that still gets through is waited out and the call is tried
again, up to six times: the wait is the `Retry-After` header, else the seconds in the message, else
5 s. The console prints each retry, and the report header gives the total.

The coach also has its own per-user limit of 30 turns an hour and 150 a day, and one run starts
about 77 (one per question and one per draft). Its 429 (code `COACH_LIMIT`) is waited out only
when it frees within 60 s. A full hourly window fails the run with a message naming the setting,
as before. Raise the limit on the target for the run with a temporary compose override on the
host, the same way it is done locally, and remove it afterwards:

```yaml
# docker-compose.eval.yml, next to the compose file on the host; do not commit it
services:
  recommendation-service:
    environment:
      FITSYNC_COACH_TURNS_PER_HOUR: "500"
      FITSYNC_COACH_TURNS_PER_DAY: "1000"
```

Recreate recommendation-service with both files (`docker compose -f docker-compose.prod.yml -f
docker-compose.eval.yml up -d recommendation-service`), run the evaluation, then recreate it with
the main file alone so the normal limits apply again.

```powershell
python scripts\coach-eval\run_coach_eval.py --now 2026-10-09T09:30 --base https://staging.fitsyncapp.click --pace 0.6 --token-file .local\staging-steady.jwt --out .local\coach-eval-staging.md
```

`--now` is the reseed's run time, as printed by `demo-reseed-cloud.ps1`. `--pace` defaults to 0,
which is right for a local stack.

## What the demo accounts show

Names and dates. The seed gives an account a person's name only where its stored name is blank, a
persona word (steady, drifting, fresh, any case) or the local part of its email (any case), which
sign-up fills in when no name is given: steady becomes Jamie Lim, drifting Alex Tan and fresh
Sam Lee, in the profile (display, first and last name), the auth account and notification-service's
copy. A real name, such as steady's own, is kept. steady and drifting are members since the first day of
the seeded history, 59 days before the run day at 06:00; fresh keeps its real creation date, since it
has just onboarded and has nothing to line up with.

steady trains the plan's week (Monday and Friday strength, a Thursday run, Saturday badminton), with
the Thursday run done on Tuesday in the current week once Tuesday has passed. Every rest day of the
last 14 days carries a 20 or 25 minute mobility session outside the plan, so the streak runs back
from yesterday for two weeks or more. Its weekly goal is six sessions (four plan days and two or three
rest days a week, every session counting), and its training preference says six days too. Every
completed week before that holds six sessions or more: a week with only the plan's days gets a
15-minute evening mobility session at 19:45 after the plan session of one or more of its plan days, so
the badges and the month panel count those weeks on goal while the active days and the streak stay as
they are. The first week of the history is partial and stays short. Meals are logged every day (the most recent past Sunday is a
fasted day), weigh-ins fall every two or three days with the latest yesterday, and three recipe
cooks (3, 6 and 9 days ago) leave two dishes in Leftovers. Today has breakfast (506 kcal, 31 g
protein) once 07:30 has passed, and the first glass of water once 08:00 has. The seed needs the
system recipe library and stops with a clear message when it is not imported.

drifting followed the same plan until 13 days ago. In the last 12 days it has two shorter, lighter
sessions (10 and 4 days ago, each done for the latest plan day of its week), meals on three days
(10, 8 and 4 days ago), no weigh-in since 10 days ago and nothing at all in the last three days.
Its weight has crept back up and its goal date is six weeks out, so the goal pace reads behind. Its
weekly goal stays at four sessions, which the slip falls short of.

fresh has no history, and its first-run guide state is cleared, so the first tour starts again.

Checked on a database built from the migrations, each account seeded twice per run time. "This
week" counts the sessions before today, then the plan days done out of 4. "Goal of 6" is what Home
says of steady's weekly goal, which counts every session.

| Run (SGT) | steady streak | steady this week   | steady goal of 6 | steady today plan  | drifting this week | drifting last week       |
| --------- | ------------- | ------------------ | ---------------- | ------------------ | ------------------ | ------------------------ |
| Mon 10:00 | 14            | 0, 0/4             | six to go        | Full Body Strength | 0, 0/4             | 1 session, 1 active day  |
| Tue 10:00 | 15            | 1, 1/4             | five to go       | none               | 0, 0/4             | 1 session, 2 active days |
| Wed 10:00 | 14            | 2, 2/4             | four to go       | none               | 0, 0/4             | 1 session, 2 active days |
| Thu 10:00 | 14            | 3, 2/4 (run moved) | three to go      | none               | 0, 0/4             | 2 sessions, 3 active days |
| Fri 02:00 | 15            | 4, 2/4             | two to go        | Full Body Strength | 1, 1/4             | 1 session, 2 active days |
| Fri 10:00 | 15            | 4, 2/4             | two to go        | Full Body Strength | 1, 1/4             | 1 session, 2 active days |
| Sat 02:00 | 16            | 5, 3/4             | one to go        | Badminton          | 1, 1/4             | 1 session, 2 active days |
| Sat 10:00 | 16            | 5, 3/4             | one to go        | Badminton          | 1, 1/4             | 1 session, 2 active days |
| Sun 10:00 | 17            | 6, 4/4             | met              | none               | 1, 1/4             | 2 sessions, 3 active days |

Workout's "this week" counts days trained against days trained or still planned, so it reads 4 of 6 on
Friday and 5 of 6 on Saturday, in step with the goal. Early in the week it counts only the plan days
still ahead (0 of 4 on Monday), because rest-day mobility is not planned in advance; Home then reads
"your goal is six sessions".

On every row steady's last full week had 7 sessions on 7 active days, every earlier full week 6 or more, and
the badges Full week and Four full weeks are earned (checked with BadgeCalculator on Friday 02:00 and
Saturday 10:00, together with First workout, 10 and 50 workouts, First and Five records, a 7-day
streak, 7 and 30 days logged, First meal, First weigh-in and First cook); its latest weigh-in is from
yesterday, its 30-day weight change is -1.6 kg and Leftovers holds two dishes; today has no meal at
02:00 and its breakfast at 10:00. drifting's streak is 0, its latest weigh-in is 10 days old, its
last meal 4 days old, its 30-day weight change +0.4 kg, and today has no meals.

On Friday 9 October 2026, seeded at 02:00, steady shows a 15-day streak. This week holds Monday's
strength, Tuesday's moved run and mobility on Wednesday and Thursday (2 of 4 plan days), and
Friday's Full Body Strength is waiting to start: four sessions in against the goal of six, two to go,
and Workout reads 4 of 6. Today has no meals yet, the last weigh-in was
yesterday, and two dishes wait in Leftovers. The coach sees last week's goal met (7 active days
against 6), the weight goal on course and the 22.5 kg bench press record. drifting shows a streak
of 0 and Monday's session only (1 of 4, Thursday missed), with Full Body Strength today, no weigh-in
for 10 days and no meals for 4. Its coach sees last week short of the goal (2 active days against
4), a logging reminder and the goal pace behind.

On Saturday 10 October 2026 steady's streak is 16, Friday's strength is done (3 of 4 plan days, five
sessions against the goal of six) and Badminton is today's plan. For drifting today's plan is Badminton and Thursday and Friday read as missed; the
rest is as on Friday.
