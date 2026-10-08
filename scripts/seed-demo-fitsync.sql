-- ============================================================================
-- FitSync demo seed — 60 days of meal + workout + body + analytics data,
-- across three personas, for weekly/monthly views, trends, records and badges.
-- ----------------------------------------------------------------------------
-- This is NOT a Flyway migration. It is a one-button RESET + seed you run
-- manually against the shared `fitsync` database (local docker-compose OR RDS).
--
-- !!! DESTRUCTIVE !!! For the account matched by the `email` parameter below,
-- it FIRST DELETES ALL of that account's data (meals, workouts, body metrics,
-- goals, custom foods, plans, preferences, health readiness, analytics, coach
-- conversations, ...), then re-imports the demo. It KEEPS only the account
-- itself (auth.app_users) and the user_profiles main record. This is
-- irreversible — back up first if running against a database you care about.
--
-- Why not a Flyway db/migration file:
--   * Each service's Flyway only owns its own schema; this script writes across
--     auth / user_domain / nutrition / workout / analytics / recommendation in
--     one transaction.
--   * It is tied to a real account that only exists in your deployed DB, and
--     Flyway runs in CI / H2 / every environment.
--
-- PARAMETERS (psql -v):
--   email    the account to target. Defaults to 2579706652@qq.com.
--   persona  which situation to generate. One of: steady, drifting, fresh.
--            Defaults to steady. An unknown persona, or an email that matches
--            no account, stops the script before anything is changed.
--   now      the run time as Singapore wall-clock time, '2026-10-09T02:00'.
--            Defaults to the current time. Every date comes from it, and no
--            row is written after it. demo-reseed.ps1 passes one value to all
--            three accounts.
--
-- HOW TO RUN  (must connect as a role that can see ALL schemas — the RDS master
-- user, or the local Postgres superuser; a per-service user like nutrition_svc
-- can only see its own schema and will fail):
--
--   Reset the default account back to the steady persona:
--     docker exec -i fitsync-postgres \
--       psql -U fitsync -d fitsync -v ON_ERROR_STOP=1 \
--       < backend/scripts/seed-demo-fitsync.sql
--
--   Seed a different account as someone whose habits are slipping:
--     docker exec -i fitsync-postgres \
--       psql -U fitsync -d fitsync -v ON_ERROR_STOP=1 \
--       -v email=someone@example.com -v persona=drifting \
--       < backend/scripts/seed-demo-fitsync.sql
--
--   Seed a freshly onboarded account with no history yet:
--     docker exec -i fitsync-postgres \
--       psql -U fitsync -d fitsync -v ON_ERROR_STOP=1 \
--       -v email=new@example.com -v persona=fresh \
--       < backend/scripts/seed-demo-fitsync.sql
--
--   (If Postgres is RDS-managed even locally, use `psql "host=... ..." -f ...`
--   instead of `docker exec -i ... < ...`, with the same -v arguments.)
--
-- The script resolves the user automatically from the email parameter, so it
-- works against whichever database actually holds the account. Re-running is
-- safe: it resets the account back to its initial (just-registered) state
-- every time, then re-imports the demo — so you always get the same dataset
-- for a given persona.
--
-- Recommended: run while the backend services are idle (or stopped) so no
-- in-flight Kafka/outbox event re-projects analytics rows after the reset.
--
-- The three personas:
--   steady    Someone for whom things are going well: four plan sessions a
--             week, a short mobility session on every rest day of the last
--             14 days (a streak of two weeks or more ending yesterday),
--             progressive overload, meals every day, a weigh-in every two or
--             three days (the latest yesterday) and a steady downward trend.
--   drifting  Someone who did well until 13 days ago and is slipping: two
--             short sessions and three days of meals in the last 12 days,
--             nothing in the last three, no weigh-in for 10 days, weight
--             creeping back up and the goal pace behind.
--   fresh     Someone who has just finished onboarding: a profile and
--             preferences, one goal, and otherwise empty history, so the
--             empty states can be checked.
--
-- Recording-day shape (steady): history runs through YESTERDAY. Today carries a
--              breakfast (07:30) and the first sip of water (08:00) only, each written
--              only once its time has passed, and today's plan session is not done.
--              One past Sunday is a FAST day (a FAST meal, no items) for the fasted-day
--              line, three recipes are cooked (recipe_cooks plus the analytics cook
--              counter), the badge ledger is cleared so the service writes it fresh on
--              the first read, and the streak and energy projections are filled in.
--              It can run at any hour: before 07:30 today simply has nothing logged yet.
--
-- Demo window: a ROLLING 60-day window that always ends on "today" (the
--              current date in Asia/Singapore) so the Dashboard "Today"/streak
--              panels are never stale. d_start = today - 59, d_today = today.
--              Every date, week number and weight/weight-lifted number below
--              is generated relative to those two dates, so the script never
--              goes stale and can be re-run on any date.
-- Timezone:    Asia/Singapore (all wall-clock times below are local to it).
-- ============================================================================

\if :{?email}
\else
  \set email 2579706652@qq.com
\endif
\if :{?persona}
\else
  \set persona steady
\endif
\if :{?now}
\else
  \set now ''
\endif

begin;

-- ----------------------------------------------------------------------------
-- 0. Resolve the target account and the run parameters ONCE. Everything
--    downstream joins demo_config / demo_days. The email reaches SQL only
--    through psql's quoted-literal substitution (:'email'), never by string
--    building, so it cannot be used to inject SQL.
-- ----------------------------------------------------------------------------
create temporary table demo_config on commit drop as
select
    u.id                                                           as user_id,
    'Asia/Singapore'::text                                         as tz,
    (r.now_local::date - 59)                                       as d_start,
    r.now_local::date                                              as d_today,
    r.now_local                                                    as now_local,
    (r.now_local at time zone 'Asia/Singapore')                    as now_ts,
    lower(:'persona')                                              as persona
from auth.app_users u
-- The run's one "now", as Singapore wall-clock time. Every date and every cut-off for today is taken
-- from it, so nothing is written after it. -v now='2026-10-09 02:00' pins it, for testing a run day.
cross join (select coalesce(nullif(:'now', '')::timestamp,
                            date_trunc('minute', now() at time zone 'Asia/Singapore')) as now_local) r
where u.email = :'email';

do $$
declare
    v_persona text;
begin
    if not exists (select 1 from demo_config) then
        raise exception
            'No FitSync account found for the given email. Pass -v email="''<address>''" to target an existing account.';
    end if;

    select persona into v_persona from demo_config;
    if v_persona not in ('steady', 'drifting', 'fresh') then
        raise exception
            'Unknown persona "%". Must be one of: steady, drifting, fresh.', v_persona;
    end if;
end $$;

-- ----------------------------------------------------------------------------
-- 1. RESET — delete ALL existing data for this account across every schema,
--    so the seed below lands on a clean slate. KEEPS only auth.app_users and
--    the user_profiles main record. Delete order respects FKs; rows with an
--    ON DELETE CASCADE parent (meal_items, set logs, plan exercises, training
--    preference equipment/focus, health-readiness injury areas, food servings/
--    barcodes/nutrient rows, coach turns/tool invocations, ...) are removed
--    automatically with their parent.
--
--    !!! This removes the user's REAL data, not just demo rows. Irreversible.
-- ----------------------------------------------------------------------------
do $$
declare
    v_user uuid;
    v_persona text;
begin
    select user_id, persona into v_user, v_persona from demo_config;
    raise notice 'RESET: deleting all existing data for user_id=% (persona=%, keeping account + profile)', v_user, v_persona;
end $$;

-- 1a. Nutrition
delete from nutrition.meal_logs                    where user_id      in (select user_id from demo_config);  -- -> meal_items -> nutrient details
delete from nutrition.water_logs                   where user_id      in (select user_id from demo_config);
delete from nutrition.meal_templates               where user_id      in (select user_id from demo_config);  -- -> meal_template_items
delete from nutrition.recipes                      where user_id      in (select user_id from demo_config);  -- -> recipe_ingredients
delete from nutrition.recipe_cooks                 where user_id      in (select user_id from demo_config);  -- cooks of system recipes, which the recipes delete below does not reach
delete from nutrition.food_favorites               where user_id      in (select user_id from demo_config);
delete from nutrition.nutrition_targets            where user_id      in (select user_id from demo_config);
delete from nutrition.nutrition_preference_projections where user_id  in (select user_id from demo_config);
delete from nutrition.custom_foods                 where user_id      in (select user_id from demo_config);
delete from nutrition.food_catalog_items           where owner_user_id in (select user_id from demo_config); -- -> servings/barcodes/nutrient profiles/details

-- 1b. Workout — sessions & plans BEFORE exercises (exercise_id FK has no cascade)
delete from workout.workout_sessions               where user_id      in (select user_id from demo_config);  -- -> exercise logs -> set logs
delete from workout.workout_plans                  where user_id      in (select user_id from demo_config);  -- -> plan exercises -> set targets
delete from workout.exercises                      where owner_user_id in (select user_id from demo_config);  -- -> instructions/images/secondary muscles

-- 1c. User domain — keep app_users (auth) and user_profiles (below)
delete from user_domain.body_metric_entries        where user_id      in (select user_id from demo_config);
delete from user_domain.user_goals                 where user_id      in (select user_id from demo_config);
delete from user_domain.user_app_preferences       where user_id      in (select user_id from demo_config);
delete from user_domain.user_training_preferences  where user_id      in (select user_id from demo_config);  -- -> equipment / focus areas
delete from user_domain.user_nutrition_preferences where user_id      in (select user_id from demo_config);
delete from user_domain.user_notification_preferences where user_id   in (select user_id from demo_config);
delete from user_domain.user_health_readiness      where user_id      in (select user_id from demo_config);  -- -> injury areas
delete from user_domain.account_data_export_requests where user_id    in (select user_id from demo_config);
-- The first-run guide's saved state (V9). Cleared for the fresh persona only, so its first tour starts by
-- itself again on the next sign-in; steady and drifting keep theirs, so a tour does not open over a
-- recording. Guarded on the table existing, since it arrived in a later user-service migration.
do $$
begin
    if exists (select 1 from information_schema.tables
                where table_schema = 'user_domain' and table_name = 'user_guide_state') then
        delete from user_domain.user_guide_state
         where user_id in (select user_id from demo_config where persona = 'fresh');
    end if;
end $$;

-- 1d. Analytics read-models + processed-event log for this user
delete from analytics.analytics_meal_contributions      where user_id in (select user_id from demo_config);
delete from analytics.analytics_workout_contributions   where user_id in (select user_id from demo_config);
delete from analytics.analytics_user_daily_nutrition    where user_id in (select user_id from demo_config);
delete from analytics.analytics_user_daily_workout      where user_id in (select user_id from demo_config);
delete from analytics.analytics_user_daily_body         where user_id in (select user_id from demo_config);
delete from analytics.analytics_user_goal_progress      where user_id in (select user_id from demo_config);
delete from analytics.analytics_user_dashboard_snapshot where user_id in (select user_id from demo_config);
delete from analytics.analytics_processed_events        where user_id in (select user_id from demo_config);
-- These tables arrived in later analytics migrations, so each is guarded on existing. The badge ledger
-- is derived from the data seeded below, so it is cleared here and the service writes it fresh on the
-- first read; a stale row would otherwise keep a badge the new data no longer earns.
do $$
declare
    t text;
begin
    foreach t in array array['analytics_user_workout_records', 'analytics_user_achievements',
                             'analytics_user_cooks', 'analytics_user_streak_rollup',
                             'analytics_user_energy_profiles'] loop
        if exists (select 1 from information_schema.tables where table_schema = 'analytics' and table_name = t) then
            execute format('delete from analytics.%I where user_id in (select user_id from demo_config)', t);
        end if;
    end loop;
end $$;

-- 1e. recommendation-service projection + coach conversation history, when that
--     service's schema is present. recommendation-service lives on a feature
--     branch, so a database built from `main` has no `recommendation` schema
--     at all. Guarded so this script stays runnable either way, and each coach
--     table is additionally guarded because it postdates the rest of the
--     schema (a database built from an older branch has recommendation_context
--     but not the coach tables yet).
do $$
begin
    if exists (select 1 from pg_namespace where nspname = 'recommendation') then
        delete from recommendation.recommendation_result
         where user_id in (select user_id from demo_config);
        delete from recommendation.recommendation_processed_events
         where user_id in (select user_id from demo_config);
        delete from recommendation.recommendation_context
         where user_id in (select user_id from demo_config);

        if exists (select 1 from pg_tables where schemaname = 'recommendation' and tablename = 'tool_invocation') then
            delete from recommendation.tool_invocation ti
             using recommendation.coach_turn t
             join recommendation.coach_thread th on th.thread_id = t.thread_id
             where ti.turn_id = t.turn_id
               and th.user_id in (select user_id from demo_config);
        end if;
        if exists (select 1 from pg_tables where schemaname = 'recommendation' and tablename = 'coach_turn') then
            delete from recommendation.coach_turn t
             using recommendation.coach_thread th
             where t.thread_id = th.thread_id
               and th.user_id in (select user_id from demo_config);
        end if;
        if exists (select 1 from pg_tables where schemaname = 'recommendation' and tablename = 'coach_generated_artifact') then
            delete from recommendation.coach_generated_artifact
             where user_id in (select user_id from demo_config);
        end if;
        if exists (select 1 from pg_tables where schemaname = 'recommendation' and tablename = 'coach_page_reading') then
            delete from recommendation.coach_page_reading
             where user_id in (select user_id from demo_config);
        end if;
        if exists (select 1 from pg_tables where schemaname = 'recommendation' and tablename = 'coach_thread') then
            delete from recommendation.coach_thread
             where user_id in (select user_id from demo_config);
        end if;
    end if;
end $$;

-- Note: per-service outbox_events (messaging infra) are intentionally NOT
-- touched. Run while services are idle so no stale event re-projects.

-- ----------------------------------------------------------------------------
-- 2. Profile — make sure height + timezone + onboarding are set so BMI /
--    dashboard render, for every persona (fresh included: the product owner
--    wants a profile that looks like it just finished onboarding).
-- ----------------------------------------------------------------------------
update user_domain.user_profiles p
set height_cm = 170.00
from demo_config c
where p.user_id = c.user_id and p.height_cm is null;

update user_domain.user_profiles p
set timezone = c.tz
from demo_config c
where p.user_id = c.user_id and (p.timezone is null or p.timezone = '');

update user_domain.user_profiles p
set onboarding_completed = true
from demo_config c
where p.user_id = c.user_id and p.onboarding_completed = false;

-- The Me page shows "Member since" from the profile's creation time (UserProfileView.createdAt), and the
-- footprint grid starts there. For steady and drifting it is set to the first day of the seeded history,
-- d_start at 06:00, before that day's first session (06:45) and weigh-in (07:00), so Me, the footprint
-- and the 60 days of data begin on the same day; an earlier real creation date would leave weeks of
-- empty grid before the history. It moves with the run day and is never after it. The fresh persona keeps
-- its real creation time: it has just onboarded and has no history to line up with.
update user_domain.user_profiles p
set date_time_created = (c.d_start + time '06:00') at time zone c.tz
from demo_config c
where p.user_id = c.user_id and c.persona in ('steady', 'drifting');

-- Display names. Home greets by first name, and the demo accounts were registered with a persona word
-- as their name ("Good evening, drifting."; the fresh account even carries "steady"). A name that is
-- blank or any persona word (steady, drifting, fresh, in any case) is replaced by the persona's person:
-- steady Jamie Lim, drifting Alex Tan, fresh Sam Lee. A real name, such as steady's own, is never
-- overwritten. The same rule is applied to the auth account's name, which signed-in tokens carry, and to
-- notification-service's copy of it.
create temporary table demo_name on commit drop as
select c.user_id, n.display_name, n.first_name, n.last_name
from demo_config c
join (values ('steady',   'Jamie Lim', 'Jamie', 'Lim'),
             ('drifting', 'Alex Tan',  'Alex',  'Tan'),
             ('fresh',    'Sam Lee',   'Sam',   'Lee')) n(persona, display_name, first_name, last_name)
  on n.persona = c.persona;

-- True when a stored name is a placeholder: blank, or one of the persona words.
create or replace function pg_temp.demo_placeholder_name(v text) returns boolean
language sql immutable as $f$
    select coalesce(btrim(v), '') = '' or lower(btrim(v)) in ('steady', 'drifting', 'fresh')
$f$;

-- The profile: a placeholder display name takes the whole person (first and last name included where
-- those are placeholders too); a real display name keeps everything, except a placeholder first name,
-- which then takes the display name's first word.
update user_domain.user_profiles p
set display_name = n.display_name,
    first_name   = case when pg_temp.demo_placeholder_name(p.first_name) then n.first_name else p.first_name end,
    last_name    = case when pg_temp.demo_placeholder_name(p.last_name)  then n.last_name  else p.last_name  end
from demo_name n
where p.user_id = n.user_id
  and pg_temp.demo_placeholder_name(p.display_name);

update user_domain.user_profiles p
set first_name = split_part(btrim(p.display_name), ' ', 1)
from demo_name n
where p.user_id = n.user_id
  and pg_temp.demo_placeholder_name(p.first_name)
  and not pg_temp.demo_placeholder_name(p.display_name);

update auth.app_users u
set display_name = n.display_name
from demo_name n
where u.id = n.user_id
  and pg_temp.demo_placeholder_name(u.display_name);

do $$
begin
    if exists (select 1 from information_schema.tables
                where table_schema = 'notification' and table_name = 'notification_recipients') then
        update notification.notification_recipients r
        set display_name = n.display_name
        from demo_name n
        where r.user_id = n.user_id
          and pg_temp.demo_placeholder_name(r.display_name);
    end if;
end $$;

-- ----------------------------------------------------------------------------
-- 2b. demo_days — one row per calendar day in [d_start, d_today], carrying
--     every value the rest of the script needs: the ISO weekday, an
--     ISO-week index counted from the Monday on/before d_start (w), how many
--     weeks that is from the most recently FULLY completed week (m, clamped
--     to >= 0 so the still-forming current week shares its tier with the last
--     completed one), whether it is a scheduled training day, the lift
--     weights / run distance for that day, and whether meals are logged that
--     day. Populated once per persona so the workout, meal and water sections
--     below are plain, auditable SQL instead of three copies of the same
--     week/weekday arithmetic.
-- ----------------------------------------------------------------------------
create temporary table demo_days on commit drop as
select
    c.user_id, c.tz, c.persona,
    g::date                                                   as d,
    (g::date - c.d_start)::int                                as idx,
    extract(isodow from g::date)::int                         as dow,
    ((g::date - mo.monday_of_start))::int / 7                 as w,
    (((c.d_today - mo.monday_of_start))::int / 7 - 1)         as w_last,
    greatest(
        (((c.d_today - mo.monday_of_start))::int / 7 - 1)
        - ((g::date - mo.monday_of_start))::int / 7,
        0
    )                                                          as m
from demo_config c
cross join lateral (
    select (c.d_start - (extract(isodow from c.d_start)::int - 1))::date as monday_of_start
) mo
cross join generate_series(c.d_start, c.d_today, interval '1 day') as g;

alter table demo_days add column tier int;
update demo_days set tier = ceil(m / 2.0)::int;

alter table demo_days add column is_training_day boolean not null default false;
alter table demo_days add column skip_session    boolean not null default false;
alter table demo_days add column workout_kind    text;
alter table demo_days add column plan_dow        int;
alter table demo_days add column duration_minutes int;
alter table demo_days add column bench_w         numeric(6,2);
alter table demo_days add column squat_w         numeric(6,2);
alter table demo_days add column row_w           numeric(6,2);
alter table demo_days add column run_km          numeric(6,2);
alter table demo_days add column meal_day        boolean not null default false;
alter table demo_days add column meal_mult       numeric(5,3) not null default 1.0;
alter table demo_days add column shake_day       boolean not null default false;
alter table demo_days add column fast_day         boolean not null default false;

-- o: whole days before today (0 is today, 1 yesterday). The recent story is laid out by o rather than
-- by weekday, so that it reads the same on whatever day the seed runs.
alter table demo_days add column o int;
update demo_days dd set o = (c.d_today - dd.d) from demo_config c;

-- STEADY: the plan's week (Monday/Friday strength, a Thursday run, Saturday badminton) every week; one
-- Thursday (week 0) and one Saturday (week 1) dropped in the first three weeks, so the record is not a
-- wall of perfect weeks. Lift weights rise every two weeks going forward in time, floored at 10 kg.
update demo_days
set is_training_day = dow in (1, 4, 5, 6),
    workout_kind = case dow when 1 then 'STRENGTH' when 5 then 'STRENGTH'
                             when 4 then 'CARDIO'   when 6 then 'SPORT' end,
    duration_minutes = case when dow in (1, 5) then 50 when dow = 4 then 35 when dow = 6 then 60 end,
    skip_session = (w = 0 and dow = 4) or (w = 1 and dow = 6)
where persona = 'steady';

-- One moved session: the Thursday run is done on the Tuesday of the same week
-- and still counts for the plan's Thursday. It sits in the current week once
-- Tuesday has passed, and in the last completed week before that, so that a
-- moved session exists whatever day the script runs on.
update demo_days dd
set is_training_day  = (dd.dow = 2),
    workout_kind     = case when dd.dow = 2 then 'CARDIO' end,
    duration_minutes = case when dd.dow = 2 then 35 end,
    plan_dow         = case when dd.dow = 2 then 4 end
from demo_config c
where dd.persona = 'steady'
  and dd.dow in (2, 4)
  and dd.w = dd.w_last + case when extract(isodow from c.d_today)::int >= 3 then 1 else 0 end;

-- Light days: over the last 14 days every day without a plan session carries a short morning mobility
-- session (20 or 25 minutes, no plan day), so the streak is a healthy run ending yesterday and the last
-- two weeks have no empty day. These sessions do not count toward the plan's days.
update demo_days
set is_training_day  = true,
    skip_session     = false,
    workout_kind     = 'MOBILITY',
    plan_dow         = null,
    duration_minutes = case when idx % 2 = 0 then 20 else 25 end
where persona = 'steady'
  and o between 1 and 14
  and not (is_training_day and not skip_session);

update demo_days
set bench_w = greatest(10, round(22.50 - 2.50 * tier, 2)),
    squat_w = greatest(10, round(18.00 - 2.00 * tier, 2)),
    row_w   = greatest(10, round(20.00 - 2.00 * tier, 2)),
    run_km  = greatest(4.00, round(5.75 - 0.25 * tier, 2))
where persona = 'steady';

update demo_days
set meal_day  = true,
    meal_mult = case idx % 3 when 0 then 1.03 when 1 then 1.10 else 1.17 end,
    shake_day = is_training_day and not skip_session and workout_kind <> 'MOBILITY'
where persona = 'steady';

-- One past Sunday is a fasted day: the most recent Sunday before today. The day carries a FAST meal
-- and no foods, so it is not a meal day.
update demo_days dd
set fast_day = true,
    meal_day = false,
    shake_day = false
from demo_config c
where dd.persona = 'steady'
  and dd.d = c.d_today - case when extract(isodow from c.d_today)::int = 7
                              then 7 else extract(isodow from c.d_today)::int end;

-- DRIFTING: the same plan and a good record until 13 days ago, then a slip over the last 12 days. In the
-- slip there are two sessions (10 and 4 days ago), each shorter and lighter, done for the most recent
-- plan day of its week (Monday at the latest), so most plan days of the slip go missed; meals are logged
-- on three days only (10, 8 and 4 days ago, heavier); there is no weigh-in after 10 days ago and nothing
-- at all in the last three days. No seven-day week of the slip holds four active days, so the weekly
-- recap reads short of the four-day goal on any run day, and the streak is 0.
update demo_days
set is_training_day = case when o >= 13 then dow in (1, 4, 5, 6) else o in (10, 4) end,
    plan_dow = case when o >= 13 then null
                    when dow in (1, 2, 3) then 1 when dow = 4 then 4 when dow = 5 then 5 else 6 end,
    skip_session = false
where persona = 'drifting';

update demo_days
set workout_kind = case coalesce(plan_dow, dow) when 1 then 'STRENGTH' when 5 then 'STRENGTH'
                                                when 4 then 'CARDIO'   when 6 then 'SPORT' end,
    duration_minutes = case
        when o <= 12 then case coalesce(plan_dow, dow) when 4 then 25 when 6 then 45 else 40 end
        when dow in (1, 5) then 50 when dow = 4 then 35 else 60
    end
where persona = 'drifting' and is_training_day;

update demo_days
set bench_w = greatest(10, round(20.00 - 2.50 * tier - case when o <= 12 then 2.50 else 0 end, 2)),
    squat_w = greatest(10, round(16.00 - 2.00 * tier - case when o <= 12 then 2.00 else 0 end, 2)),
    row_w   = greatest(10, round(18.00 - 2.00 * tier - case when o <= 12 then 2.00 else 0 end, 2)),
    run_km  = greatest(3.00, round(5.25 - 0.25 * tier - case when o <= 12 then 1.25 else 0 end, 2))
where persona = 'drifting';

update demo_days
set meal_day  = o >= 13 or o in (10, 8, 4),
    meal_mult = case
        when o <= 12 then 1.25 + (idx % 3) * 0.03
        else case idx % 3 when 0 then 1.03 when 1 then 1.10 else 1.17 end
    end
where persona = 'drifting';
update demo_days set shake_day = meal_day and is_training_day where persona = 'drifting';

-- FRESH: no training days, no meal days — demo_days keeps its all-false /
-- 1.0 defaults, so the sections below naturally seed nothing for this
-- account beyond the profile/preferences/goal handled explicitly.

-- ============================================================================
-- 3. NUTRITION — meals + items on meal_day rows (never on d_today, which is
--    excluded from demo_days' iteration window below); an extra post-workout
--    shake on shake_day rows. meal_id / meal_item_id are deterministic (md5)
--    so re-runs and the contribution roll-ups line up. Foods are the ones the
--    script already uses; meal_mult (from demo_days) rotates quantities to
--    vary daily calories/protein instead of inventing new foods.
-- ============================================================================

-- 3a. meal_logs
insert into nutrition.meal_logs
    (meal_id, user_id, meal_type, name, source, consumed_at, timezone, note,
     date_time_created, date_time_updated, created_by, updated_by, version)
with meals(code, meal_type, at_t, name, note, workout_only) as (values
    ('BFAST'::text,  'BREAKFAST'::text, time '07:30', 'Breakfast'::text,          'Morning fuel before the day.'::text,        false),
    ('LUNCH',        'LUNCH',           time '12:30', 'Lunch',                     'Balanced midday meal.',                     false),
    ('SNACK',        'SNACK',           time '16:00', 'Afternoon snack',           'Light pick-me-up.',                         false),
    ('DINNER',       'DINNER',          time '19:00', 'Dinner',                    'Main evening meal.',                        false),
    ('POSTWO',       'SNACK',           time '20:30', 'Post-workout shake',        'Recovery shake after training.',            true)
)
select
    md5(dd.user_id::text || dd.d::text || m.code)::uuid,
    dd.user_id,
    m.meal_type,
    m.name,
    'MANUAL',
    (dd.d + m.at_t) at time zone dd.tz,
    dd.tz,
    m.note,
    (dd.d + m.at_t) at time zone dd.tz,
    (dd.d + m.at_t) at time zone dd.tz,
    'demo-seed', 'demo-seed', 0
from demo_days dd
cross join meals m
where dd.persona <> 'fresh'
  and (dd.d < (select d_today from demo_config) or m.code = 'BFAST')  -- today has its breakfast only
  and (dd.d + m.at_t) <= (select now_local from demo_config)           -- and only once its time has passed
  and dd.meal_day
  and (m.workout_only = false or dd.shake_day);

-- 3a-2. The fasted day: one FAST meal and no items.
insert into nutrition.meal_logs
    (meal_id, user_id, meal_type, name, source, consumed_at, timezone, note,
     date_time_created, date_time_updated, created_by, updated_by, version)
select
    md5(dd.user_id::text || dd.d::text || 'FAST')::uuid,
    dd.user_id, 'FAST', 'Fasted day', 'MANUAL',
    (dd.d + time '12:00') at time zone dd.tz, dd.tz, null,
    (dd.d + time '12:00') at time zone dd.tz,
    (dd.d + time '12:00') at time zone dd.tz,
    'demo-seed', 'demo-seed', 0
from demo_days dd
where dd.fast_day;

-- 3b. meal_items — every item is linked to a real USDA SR Legacy catalogue food. The quantity is
--     stored in grams (unit 'g') times demo_days.meal_mult, and calories and macros are recomputed
--     from the food's nutrient profile for those grams, so the item agrees with what logging the
--     same food through the app would store. The friendly food_name stays for the screen.
--     The snapshot columns (grams, saturated fat, water, vitamins) are filled by 3d below.
create temporary table demo_foods on commit drop as
select * from (values
    ('rolled_oats'::text,   'Rolled Oats'::text,           'Cereals, oats, regular and quick, not fortified, dry'::text,                      '60 g'::text),
    ('banana',              'Banana',                      'Bananas, raw',                                                                    '1 medium (118 g)'),
    ('greek_yogurt',        'Greek Yogurt',                'Yogurt, Greek, plain, nonfat (Includes foods for USDA''s Food Distribution Program)', '1 cup (170 g)'),
    ('chicken_breast',      'Grilled Chicken Breast',      'Chicken, broilers or fryers, breast, meat only, cooked, roasted',                 '150 g'),
    ('white_rice',          'Steamed White Rice',          'Rice, white, long-grain, regular, enriched, cooked',                              '1 bowl (200 g)'),
    ('mixed_vegetables',    'Stir-fried Vegetables',       'Vegetables, mixed, frozen, cooked, boiled, drained, without salt',                '150 g'),
    ('almonds',             'Almonds',                     'Nuts, almonds',                                                                   '30 g'),
    ('apple',               'Apple',                       'Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)',   '1 medium (182 g)'),
    ('salmon',              'Baked Salmon',                'Fish, salmon, Atlantic, farmed, cooked, dry heat',                                '150 g'),
    ('sweet_potato',        'Sweet Potato',                'Sweet potato, cooked, baked in skin, flesh, without salt',                        '200 g'),
    ('salad',               'Garden Salad',                'Lettuce, cos or romaine, raw',                                                    '120 g'),
    ('whey_shake',          'Whey Protein Shake',          'Beverages, Protein powder whey based',                                            '1 scoop (35 g)'),
    ('berries',             'Mixed Berries',               'Blueberries, raw',                                                                '100 g'),
    ('coffee',              'Coffee',                      'Beverages, coffee, brewed, prepared with tap water',                              '1 mug (240 ml)'),
    ('black_tea',           'Black Tea',                   'Beverages, tea, black, brewed, prepared with tap water',                          '1 cup (240 ml)'),
    ('orange_juice',        'Orange Juice',                'Orange juice, raw (Includes foods for USDA''s Food Distribution Program)',        '1 glass (250 ml)')
) as t(food_key, food_name, catalog_name, snap_label);

-- The catalogue food and the nutrient profile behind each demo food. The profile must be stated per
-- grams, because the quantities below are grams.
create temporary table demo_food_basis on commit drop as
select df.food_key, df.food_name, df.snap_label, f.food_id, f.category_code,
       p.basis_quantity, p.calories, p.protein_grams, p.carbs_grams, p.fat_grams,
       p.fiber_grams, p.sugar_grams, p.sodium_mg
  from demo_foods df
  join nutrition.food_catalog_items f
    on f.display_name = df.catalog_name and f.scope = 'GLOBAL' and f.source_dataset = 'USDA_SR'
  join lateral (
        select fp.* from nutrition.food_nutrient_profiles fp
         where fp.food_id = f.food_id and fp.active and fp.basis_quantity > 0
           and lower(fp.basis_unit) in ('g', 'gram', 'grams')
         order by fp.date_effective_from desc nulls last, fp.nutrient_profile_id
         limit 1
       ) p on true;

do $$
begin
    if (select count(*) from demo_food_basis) <> (select count(*) from demo_foods) then
        raise exception
            'The USDA catalogue is missing some demo foods (found % of %). Apply the V900 and V901 seeds to the nutrition schema first.',
            (select count(*) from demo_food_basis), (select count(*) from demo_foods);
    end if;
end $$;

insert into nutrition.meal_items
    (meal_item_id, meal_id, custom_food_id, external_food_ref, food_name, brand_name,
     quantity, unit, serving_size, serving_unit, calories, protein_grams, carbs_grams,
     fat_grams, fiber_grams, sugar_grams, sodium_mg, date_time_created, date_time_updated,
     created_by, updated_by, version, food_catalog_item_id, food_serving_id, barcode,
     snapshot_serving_label, category_code, source_dataset)
with meals(code, at_t, workout_only) as (values
    ('BFAST'::text, time '07:30', false),
    ('LUNCH',       time '12:30', false),
    ('SNACK',       time '16:00', false),
    ('DINNER',      time '19:00', false),
    ('POSTWO',      time '20:30', true)
),
items(code, slot, food_key, grams) as (values
    -- breakfast
    ('BFAST'::text, 1, 'rolled_oats'::text,    60.00::numeric),
    ('BFAST', 2, 'banana',            118.00),
    ('BFAST', 3, 'greek_yogurt',      170.00),
    -- lunch
    ('LUNCH', 1, 'chicken_breast',    150.00),
    ('LUNCH', 2, 'white_rice',        200.00),
    ('LUNCH', 3, 'mixed_vegetables',  150.00),
    -- afternoon snack
    ('SNACK', 1, 'almonds',            30.00),
    ('SNACK', 2, 'apple',             182.00),
    -- dinner
    ('DINNER',1, 'salmon',            150.00),
    ('DINNER',2, 'sweet_potato',      200.00),
    ('DINNER',3, 'salad',             120.00),
    -- post-workout shake (training days only)
    ('POSTWO',1, 'whey_shake',         35.00),
    ('POSTWO',2, 'berries',           100.00)
),
sized as (
    select dd.user_id, dd.d, dd.tz, it.code, it.slot, m.at_t, fb.*,
           round(it.grams * dd.meal_mult, 2) as qty
      from demo_days dd
      join items it on true
      join meals m on m.code = it.code
      join demo_food_basis fb on fb.food_key = it.food_key
     where dd.persona <> 'fresh'
       and (dd.d < (select d_today from demo_config) or it.code = 'BFAST')
       and (dd.d + m.at_t) <= (select now_local from demo_config)
       and dd.meal_day
       and (m.workout_only = false or dd.shake_day)
)
select
    md5(z.user_id::text || z.d::text || z.code || z.slot::text)::uuid,
    md5(z.user_id::text || z.d::text || z.code)::uuid,
    null, null, z.food_name, null,
    z.qty, 'g', z.qty, 'g',
    round(z.calories * z.qty / z.basis_quantity)::int,
    round(z.protein_grams * z.qty / z.basis_quantity, 2),
    round(z.carbs_grams   * z.qty / z.basis_quantity, 2),
    round(z.fat_grams     * z.qty / z.basis_quantity, 2),
    round(z.fiber_grams   * z.qty / z.basis_quantity, 2),
    round(z.sugar_grams   * z.qty / z.basis_quantity, 2),
    round(z.sodium_mg     * z.qty / z.basis_quantity, 2),
    (z.d + z.at_t) at time zone z.tz,
    (z.d + z.at_t) at time zone z.tz,
    'demo-seed', 'demo-seed', 0, z.food_id, null, null, z.snap_label, z.category_code, 'USDA_SR'
from sized z;

-- 3b-2. One drink a day with lunch, rotating coffee, black tea and orange juice, so that the water
--       that drinks add shows on the Diary. Drinks are not scaled by meal_mult.
insert into nutrition.meal_items
    (meal_item_id, meal_id, custom_food_id, external_food_ref, food_name, brand_name,
     quantity, unit, serving_size, serving_unit, calories, protein_grams, carbs_grams,
     fat_grams, fiber_grams, sugar_grams, sodium_mg, date_time_created, date_time_updated,
     created_by, updated_by, version, food_catalog_item_id, food_serving_id, barcode,
     snapshot_serving_label, category_code, source_dataset)
with drinks(rot, food_key, grams) as (values
    (0, 'coffee'::text,       240.00::numeric),
    (1, 'black_tea',          240.00),
    (2, 'orange_juice',       250.00)
)
select
    md5(dd.user_id::text || dd.d::text || 'LUNCH' || '4')::uuid,
    md5(dd.user_id::text || dd.d::text || 'LUNCH')::uuid,
    null, null, fb.food_name, null,
    dr.grams, 'g', dr.grams, 'g',
    round(fb.calories * dr.grams / fb.basis_quantity)::int,
    round(fb.protein_grams * dr.grams / fb.basis_quantity, 2),
    round(fb.carbs_grams   * dr.grams / fb.basis_quantity, 2),
    round(fb.fat_grams     * dr.grams / fb.basis_quantity, 2),
    round(fb.fiber_grams   * dr.grams / fb.basis_quantity, 2),
    round(fb.sugar_grams   * dr.grams / fb.basis_quantity, 2),
    round(fb.sodium_mg     * dr.grams / fb.basis_quantity, 2),
    (dd.d + time '12:30') at time zone dd.tz,
    (dd.d + time '12:30') at time zone dd.tz,
    'demo-seed', 'demo-seed', 0, fb.food_id, null, null, fb.snap_label, fb.category_code, 'USDA_SR'
from demo_days dd
join drinks dr on dr.rot = dd.idx % 3
join demo_food_basis fb on fb.food_key = dr.food_key
where dd.persona <> 'fresh'
  and dd.d < (select d_today from demo_config)
  and dd.meal_day;

-- 3d. Snapshot columns. The same fill the stand-alone backfill-nutrition-snapshots.sql script does for
--     old meals, limited here to this account's meals: grams, saturated fat, water from drinks, the
--     category and the vitamin and mineral rows, all taken from the catalogue foods linked above.
-- 3d-1. grams_consumed
update nutrition.meal_items mi
   set grams_consumed = x.grams
  from (
        select i.meal_item_id,
               case
                   when s.grams_equivalent is not null
                       then round(i.quantity * s.grams_equivalent, 2)
                   when lower(i.unit) in ('g', 'gram', 'grams')
                       then round(i.quantity, 2)
                   when lower(i.unit) = 'ml'
                       then round(i.quantity * coalesce(f.density_grams_per_ml, 1), 2)
               end as grams
          from nutrition.meal_items i
          left join nutrition.food_servings s on s.serving_id = i.food_serving_id
          left join nutrition.food_catalog_items f on f.food_id = i.food_catalog_item_id
         where i.grams_consumed is null
           and i.meal_id in (select ml.meal_id from nutrition.meal_logs ml
                              join demo_config c on c.user_id = ml.user_id)
       ) x
 where mi.meal_item_id = x.meal_item_id
   and x.grams is not null;

-- The portion of every catalogue-linked item against its food's nutrient profile: how many times the
-- profile's basis (for example 100 g) the item holds. A millilitre basis is reached from grams through
-- the density. This list is rebuilt on every run and dropped at the end.
create temp table snapshot_scale as
select i.meal_item_id,
       i.food_catalog_item_id as food_id,
       p.saturated_fat_grams,
       p.water_g,
       f.category_code,
       f.counts_toward_water,
       case
           when lower(p.basis_unit) in ('g', 'gram', 'grams')
               then i.grams_consumed / p.basis_quantity
           when lower(p.basis_unit) = 'ml'
               then i.grams_consumed / coalesce(f.density_grams_per_ml, 1) / p.basis_quantity
       end as factor
  from nutrition.meal_items i
  join nutrition.food_catalog_items f on f.food_id = i.food_catalog_item_id
  join lateral (
        select fp.basis_quantity, fp.basis_unit, fp.saturated_fat_grams, fp.water_g
          from nutrition.food_nutrient_profiles fp
         where fp.food_id = f.food_id
           and fp.active
           and fp.basis_quantity > 0
           and (fp.date_effective_from is null or fp.date_effective_from <= current_date)
           and (fp.date_effective_to is null or fp.date_effective_to >= current_date)
         order by fp.date_effective_from desc nulls last, fp.nutrient_profile_id
         limit 1
       ) p on true
 where i.grams_consumed is not null
   and i.meal_id in (select ml.meal_id from nutrition.meal_logs ml
                              join demo_config c on c.user_id = ml.user_id);

-- 3d-2. saturated fat, water and category
update nutrition.meal_items mi
   set saturated_fat_grams = round(s.saturated_fat_grams * s.factor, 2)
  from snapshot_scale s
 where mi.meal_item_id = s.meal_item_id
   and mi.saturated_fat_grams is null
   and s.saturated_fat_grams is not null
   and s.factor is not null;

update nutrition.meal_items mi
   set water_ml = round(s.water_g * s.factor)::int
  from snapshot_scale s
 where mi.meal_item_id = s.meal_item_id
   and mi.water_ml is null
   and s.counts_toward_water
   and s.water_g is not null
   and s.factor is not null;

update nutrition.meal_items mi
   set category_code = s.category_code
  from snapshot_scale s
 where mi.meal_item_id = s.meal_item_id
   and mi.category_code is null
   and s.category_code is not null;

-- 3d-3. vitamins and minerals
insert into nutrition.meal_item_nutrient_details
    (detail_id, meal_item_id, nutrient_code, display_name, amount, unit, sort_order,
     date_time_created, date_time_updated, created_by, updated_by, version)
select md5(s.meal_item_id::text || ':' || d.nutrient_code)::uuid,
       s.meal_item_id, d.nutrient_code, d.display_name,
       round(d.amount * s.factor, 2), d.unit, d.sort_order,
       now(), now(), 'backfill-snapshots', 'backfill-snapshots', 0
  from snapshot_scale s
  join nutrition.food_nutrient_details d on d.food_id = s.food_id
 where s.factor is not null
   and d.active
   and (d.date_effective_from is null or d.date_effective_from <= current_date)
   and (d.date_effective_to is null or d.date_effective_to >= current_date)
   and not exists (select 1
                     from nutrition.meal_item_nutrient_details x
                    where x.meal_item_id = s.meal_item_id)
on conflict do nothing;

drop table snapshot_scale;

-- 3c. water_logs — four sips per day, ~2.3 L total, on every day with a meal or a fast (today: the first
--     sip only, once 08:00 has passed).
insert into nutrition.water_logs
    (water_id, user_id, amount_ml, logged_at, timezone,
     date_time_created, date_time_updated, created_by, updated_by, version)
with sips(slot, at_t, amount_ml) as (values
    (1, time '08:00', 500),
    (2, time '12:30', 750),
    (3, time '17:00', 600),
    (4, time '21:00', 450)
)
select
    md5(dd.user_id::text || dd.d::text || 'water' || s.slot::text)::uuid,
    dd.user_id, s.amount_ml,
    (dd.d + s.at_t) at time zone dd.tz, dd.tz,
    (dd.d + s.at_t) at time zone dd.tz,
    (dd.d + s.at_t) at time zone dd.tz,
    'demo-seed', 'demo-seed', 0
from demo_days dd cross join sips s
where dd.persona <> 'fresh'
  and (dd.meal_day or dd.fast_day)
  and (dd.d < (select d_today from demo_config) or s.slot = 1)  -- today has the morning sip only
  and (dd.d + s.at_t) <= (select now_local from demo_config);

-- ============================================================================
-- 4. USER profile data — body metrics + goals.
-- ============================================================================

-- 4a. body_metric_entries — Monday/Thursday mornings, skipped entirely for
--     "fresh" (the days are set in the query). steady falls from 74.20 kg to 71.10 kg at a pace that picks up
--     slightly, about 0.4 kg a week over the last four weeks, so that the rate
--     of the last 28 days reaches the 68 kg goal before its date. drifting
--     falls from 74.20 kg to 71.80 kg by day 30 and climbs back to about
--     73.20 kg by its last weigh-in 10 days ago, so its 30-day change is a gain. Small deterministic noise keeps the line from
--     being perfectly straight. Body fat and waist trend down with weight.
insert into user_domain.body_metric_entries
    (metric_id, user_id, weight_kg, body_fat_rate, waist_cm, hip_cm, chest_cm,
     arm_cm, thigh_cm, calf_cm, measured_at, source, note,
     date_time_created, date_time_updated, created_by, updated_by, version)
with calc as (
    select
        dd.user_id, dd.tz, dd.d, dd.idx,
        case dd.persona
            when 'steady'   then round(74.20 - 3.10 * power(dd.idx::numeric / 59.0, 1.15), 2)
            when 'drifting' then case
                when dd.idx <= 30 then round(74.20 - 2.40 * (dd.idx::numeric / 30.0), 2)
                else                   round(71.80 + 1.40 * ((dd.idx - 30)::numeric / 19.0), 2)
            end
        end as base_w,
        round((((dd.idx * 37) % 7) - 3) * 0.03, 2) as noise
    from demo_days dd
    -- Every two or three days counted back from yesterday (1, 3, 5, 8, 10, ... days ago), so steady's
    -- latest weigh-in is always yesterday; drifting stops weighing in after 10 days ago.
    where dd.o % 7 in (1, 3, 5)
      and (dd.persona = 'steady' or (dd.persona = 'drifting' and dd.o >= 9))
)
select
    md5(c.user_id::text || 'body' || c.idx::text)::uuid,
    c.user_id,
    (c.base_w + c.noise)::numeric(6,2),
    round(22.50 - (74.20 - (c.base_w + c.noise)) * 0.90, 2),
    round(82.00 - (74.20 - (c.base_w + c.noise)) * 1.20, 2),
    null, null, null, null, null,
    (c.d + time '07:00') at time zone c.tz,
    'MANUAL', 'Demo check-in.',
    (c.d + time '07:05') at time zone c.tz,
    (c.d + time '07:05') at time zone c.tz,
    'demo-seed', 'demo-seed', 0
from calc c;

-- 4b. user_goals (deterministic ids; feed the analytics targets below).
--     fresh gets only WORKOUT_FREQUENCY (the empty-state persona). steady's
--     WEIGHT target date is 10 weeks after today; drifting's is 6 weeks
--     after today, which its regained weight no longer reaches.
insert into user_domain.user_goals
    (goal_id, user_id, goal_type, target_value, target_unit, target_date, status, notes,
     date_time_created, date_time_updated, created_by, updated_by, version)
select
    md5(c.user_id::text || 'GOAL' || g.goal_type)::uuid,
    c.user_id, g.goal_type,
    -- steady counts every session, the short mobility sessions of its rest days included: four plan days
    -- and two or three rest days make six a week, so its goal is six and Home, the plan ring and Workout's
    -- "4 of 6" agree on any weekday. drifting and fresh keep four, which drifting's slip falls short of.
    case when g.goal_type = 'WORKOUT_FREQUENCY' and c.persona = 'steady' then 6.00
         else g.target_value::numeric end,
    g.target_unit,
    case
        -- Ten weeks ahead, which the steady persona's rate of loss reaches, so that the pace rule and
        -- the goal progress agree that the goal is on track.
        when g.goal_type = 'WEIGHT' and c.persona = 'steady'   then (c.d_today + 70)::date
        -- Six weeks ahead, which drifting's regained weight no longer reaches, so the pace reads behind.
        when g.goal_type = 'WEIGHT' and c.persona = 'drifting' then (c.d_today + 42)::date
        else (c.d_today + 60)::date
    end,
    'ACTIVE',
    case when g.goal_type = 'WORKOUT_FREQUENCY' and c.persona = 'steady'
         then 'Train six days a week: the four plan days and mobility on two rest days.'
         else g.notes end,
    (c.d_start + time '08:00') at time zone c.tz,
    c.now_ts,
    'demo-seed', 'demo-seed', 0
from demo_config c
cross join (values
    ('WEIGHT'::text,            '68.00'::text,   'kg'::text,      'Reach a lean target weight while keeping strength.'::text),
    ('CALORIES',                '2100.00',       'kcal',          'Maintain a slight daily calorie deficit.'),
    ('PROTEIN',                 '140.00',        'g',             'Hit daily protein for recomposition.'),
    ('WORKOUT_FREQUENCY',       '4.00',          'sessions/week', 'Complete four structured sessions every week.')
) as g(goal_type, target_value, target_unit, notes)
where c.persona in ('steady', 'drifting')
   or (c.persona = 'fresh' and g.goal_type = 'WORKOUT_FREQUENCY');

-- Direct SQL bypasses user-service, so explicitly enqueue the same GoalUpdated
-- events that UserServiceImpl.upsertGoal() would publish. Re-running the seed
-- requeues the deterministic events with the latest seeded targets.
insert into user_domain.outbox_events
    (event_id, topic, event_type, aggregate_type, aggregate_id, user_id, event_version,
     payload, status, date_time_produced, date_time_published, error_message, date_time_created)
select
    md5(c.user_id::text || ':demo-goal-updated:' || g.goal_id::text)::uuid,
    'progress-events',
    'GoalUpdated',
    'UserGoal',
    g.goal_id::text,
    g.user_id,
    1,
    jsonb_build_object(
        'eventId', md5(c.user_id::text || ':demo-goal-updated:' || g.goal_id::text)::uuid,
        'eventType', 'GoalUpdated',
        'version', 1,
        'occurredAt', g.date_time_updated,
        'source', 'user-service',
        'userId', g.user_id,
        'payload', jsonb_build_object(
            'goalId', g.goal_id,
            'goalType', g.goal_type,
            'targetValue', g.target_value,
            'targetUnit', g.target_unit,
            'targetDate', g.target_date,
            'status', g.status
        )
    )::text,
    'PENDING',
    g.date_time_updated,
    null,
    null,
    current_timestamp
from demo_config c
join user_domain.user_goals g on g.user_id = c.user_id and g.created_by = 'demo-seed'
where g.status = 'ACTIVE'
on conflict (event_id) do update set
    payload = excluded.payload,
    status = 'PENDING',
    date_time_produced = excluded.date_time_produced,
    date_time_published = null,
    error_message = null;

-- A requeued event also starts its retries over. Without this an event that had already failed its
-- attempts would stay parked behind next_attempt_at, or be skipped as exhausted, and the goals would never
-- reach analytics. The retry columns arrived in user-service V10, so the reset is guarded on them existing
-- and the script still runs against a database migrated before that.
do $$
begin
    if exists (select 1 from information_schema.columns
                where table_schema = 'user_domain' and table_name = 'outbox_events'
                  and column_name = 'attempt_count') then
        update user_domain.outbox_events o
           set attempt_count = 0,
               next_attempt_at = null
          from demo_config c
          join user_domain.user_goals g on g.user_id = c.user_id and g.created_by = 'demo-seed'
         where o.event_id = md5(c.user_id::text || ':demo-goal-updated:' || g.goal_id::text)::uuid;
    end if;
end $$;

-- ============================================================================
-- 5. WORKOUT — exercises, a plan, and one session per demo_days row where
--    is_training_day is true and skip_session is false, for the 60-day
--    window (never on or after d_today). Entirely skipped for "fresh" (no
--    plan, no sessions). Every set of a completed session is stored as
--    performed (logged = true AND status = 'DONE'); the application's
--    personal-record query requires DONE, and the seed used to leave status
--    at its PENDING default, so that query found nothing — this is a bug fix.
-- ============================================================================
do $$
declare
    v_user   uuid;
    v_tz     text;
    v_persona text;
    v_start  date;
    v_today  date;
    v_plan   uuid := gen_random_uuid();
    v_day_mon uuid := gen_random_uuid();
    v_day_thu uuid := gen_random_uuid();
    v_day_fri uuid := gen_random_uuid();
    v_day_sat uuid := gen_random_uuid();
    v_plan_day uuid;
    ex_bench uuid;
    ex_squat uuid;
    ex_row   uuid;
    ex_plank uuid;
    ex_run   uuid := gen_random_uuid();

    r        record;
    started  timestamptz;
    v_session uuid;
    v_log    uuid;
begin
    select user_id, tz, persona, d_start, d_today into v_user, v_tz, v_persona, v_start, v_today from demo_config;

    if v_persona = 'fresh' then
        return;
    end if;

    -- 5a. Plan movements.
    --     Prefer the imported free-exercise-db catalog entry (custom=false) so the
    --     plan + guided session show the catalog's real description, step-by-step
    --     instructions and images (present on the deployed catalog). Pick the row
    --     with the most instructions so guidance is non-empty. Fall back to a
    --     self-seeded demo exercise when the catalog is not imported in this env.
    select e.exercise_id into ex_bench from workout.exercises e
        where e.custom = false and e.status = 'ACTIVE' and e.name = 'Dumbbell Bench Press'
        order by (select count(*) from workout.exercise_instructions x where x.exercise_id = e.exercise_id) desc, e.exercise_id
        limit 1;
    select e.exercise_id into ex_squat from workout.exercises e
        where e.custom = false and e.status = 'ACTIVE' and e.name = 'Goblet Squat'
        order by (select count(*) from workout.exercise_instructions x where x.exercise_id = e.exercise_id) desc, e.exercise_id
        limit 1;
    select e.exercise_id into ex_row from workout.exercises e
        where e.custom = false and e.status = 'ACTIVE' and e.name = 'Bent Over Two-Dumbbell Row'
        order by (select count(*) from workout.exercise_instructions x where x.exercise_id = e.exercise_id) desc, e.exercise_id
        limit 1;
    select e.exercise_id into ex_plank from workout.exercises e
        where e.custom = false and e.status = 'ACTIVE' and e.name = 'Plank'
        order by (select count(*) from workout.exercise_instructions x where x.exercise_id = e.exercise_id) desc, e.exercise_id
        limit 1;

    -- Fallback demo exercises (only when the catalog is absent).
    if ex_bench is null then
        ex_bench := gen_random_uuid();
        insert into workout.exercises
            (exercise_id, owner_user_id, code, name, description, exercise_type, primary_muscle_group, equipment, difficulty, status, custom, date_time_created, date_time_updated, created_by, updated_by, version)
        values
            (ex_bench, v_user, 'DEMO_DB_BENCH', 'Dumbbell Bench Press', 'Flat-bench dumbbell press for chest strength.', 'STRENGTH', 'CHEST', 'DUMBBELL', 'INTERMEDIATE', 'ACTIVE', true,
             (v_start + time '08:00') at time zone v_tz, (v_start + time '08:00') at time zone v_tz, 'demo-seed', 'demo-seed', 0);
    end if;
    if ex_squat is null then
        ex_squat := gen_random_uuid();
        insert into workout.exercises
            (exercise_id, owner_user_id, code, name, description, exercise_type, primary_muscle_group, equipment, difficulty, status, custom, date_time_created, date_time_updated, created_by, updated_by, version)
        values
            (ex_squat, v_user, 'DEMO_GOBLET_SQUAT', 'Goblet Squat', 'Dumbbell goblet squat for legs and glutes.', 'STRENGTH', 'LEGS', 'DUMBBELL', 'BEGINNER', 'ACTIVE', true,
             (v_start + time '08:00') at time zone v_tz, (v_start + time '08:00') at time zone v_tz, 'demo-seed', 'demo-seed', 0);
    end if;
    if ex_row is null then
        ex_row := gen_random_uuid();
        insert into workout.exercises
            (exercise_id, owner_user_id, code, name, description, exercise_type, primary_muscle_group, equipment, difficulty, status, custom, date_time_created, date_time_updated, created_by, updated_by, version)
        values
            (ex_row, v_user, 'DEMO_BENT_ROW', 'Bent-Over Row', 'Dumbbell bent-over row for the back.', 'STRENGTH', 'BACK', 'DUMBBELL', 'INTERMEDIATE', 'ACTIVE', true,
             (v_start + time '08:00') at time zone v_tz, (v_start + time '08:00') at time zone v_tz, 'demo-seed', 'demo-seed', 0);
    end if;
    if ex_plank is null then
        ex_plank := gen_random_uuid();
        insert into workout.exercises
            (exercise_id, owner_user_id, code, name, description, exercise_type, primary_muscle_group, equipment, difficulty, status, custom, date_time_created, date_time_updated, created_by, updated_by, version)
        values
            (ex_plank, v_user, 'DEMO_PLANK', 'Plank Hold', 'Isometric core hold.', 'MOBILITY', 'CORE', 'BODYWEIGHT', 'BEGINNER', 'ACTIVE', true,
             (v_start + time '08:00') at time zone v_tz, (v_start + time '08:00') at time zone v_tz, 'demo-seed', 'demo-seed', 0);
    end if;

    -- Treadmill Run has no free-exercise-db equivalent — always a demo exercise.
    insert into workout.exercises
        (exercise_id, owner_user_id, code, name, description, exercise_type, primary_muscle_group, equipment, difficulty, status, custom, date_time_created, date_time_updated, created_by, updated_by, version)
    values
        (ex_run, v_user, 'DEMO_TREADMILL', 'Treadmill Run', 'Steady-state treadmill running.', 'CARDIO', 'CARDIO', 'CARDIO_MACHINE', 'BEGINNER', 'ACTIVE', true,
         (v_start + time '08:00') at time zone v_tz, (v_start + time '08:00') at time zone v_tz, 'demo-seed', 'demo-seed', 0);

    -- 5b. workout plan with a week: strength on Monday and Friday, a run on Thursday and
    --     badminton on Saturday. The other three days are rest days.
    insert into workout.workout_plans
        (plan_id, user_id, name, description, goal_focus, workout_type, difficulty,
         estimated_duration_minutes, status, date_time_created, date_time_updated,
         created_by, updated_by, version)
    values
        (v_plan, v_user, 'Recomp Strength + Cardio',
         'Strength on Monday and Friday, a run on Thursday and badminton on Saturday.',
         'GENERAL_FITNESS', 'STRENGTH', 'BEGINNER', 50, 'ACTIVE',
         (v_start + time '08:00') at time zone v_tz, (v_start + time '08:00') at time zone v_tz,
         'demo-seed', 'demo-seed', 0);

    insert into workout.workout_plan_days
        (plan_day_id, plan_id, day_of_week, kind, title, sport_activity, estimated_duration_minutes,
         date_time_created, date_time_updated, created_by, updated_by, version)
    select d.plan_day_id, v_plan, d.day_of_week, d.kind, d.title, d.sport_activity, d.minutes,
           (v_start + time '08:00') at time zone v_tz, (v_start + time '08:00') at time zone v_tz,
           'demo-seed', 'demo-seed', 0
    from (values
        (v_day_mon, 1, 'STRENGTH', 'Full Body Strength', null,        50),
        (v_day_thu, 4, 'CARDIO',   'Treadmill Run',      null,        35),
        (v_day_fri, 5, 'STRENGTH', 'Full Body Strength', null,        50),
        (v_day_sat, 6, 'SPORT',    'Badminton',          'BADMINTON', 60)
    ) as d(plan_day_id, day_of_week, kind, title, sport_activity, minutes);

    -- 5c. plan exercises by day, and their targets set by set. A sport day has no exercises.
    insert into workout.workout_plan_entries
        (plan_exercise_id, plan_id, plan_day_id, exercise_id, exercise_name_snapshot, sequence_no, position,
         target_sets, target_reps, target_weight_kg, target_duration_seconds,
         target_distance_km, rest_seconds, notes)
    select gen_random_uuid(), v_plan, d.plan_day_id, x.exercise_id, x.name, x.sequence_no, x.sequence_no,
           x.target_sets, x.target_reps, x.target_weight_kg, x.target_duration_seconds,
           null, x.rest_seconds, x.notes
    from (values (v_day_mon), (v_day_fri)) as d(plan_day_id)
    cross join (values
        (ex_bench, 'Dumbbell Bench Press', 1, 3, 10,            20.00,          null::int, 75, 'Controlled tempo, full range.'),
        (ex_squat, 'Goblet Squat',         2, 3, 12,            16.00,          null,      75, 'Knees tracking over toes.'),
        (ex_row,   'Bent-Over Row',        3, 3, 10,            18.00,          null,      75, 'Squeeze the shoulder blades.'),
        (ex_plank, 'Plank Hold',           4, 3, null,          null,           45,        45, 'Brace and keep hips level.')
    ) as x(exercise_id, name, sequence_no, target_sets, target_reps, target_weight_kg,
           target_duration_seconds, rest_seconds, notes);

    insert into workout.workout_plan_entries
        (plan_exercise_id, plan_id, plan_day_id, exercise_id, exercise_name_snapshot, sequence_no, position,
         target_sets, target_reps, target_weight_kg, target_duration_seconds,
         target_distance_km, rest_seconds, notes)
    values
        (gen_random_uuid(), v_plan, v_day_thu, ex_run,   'Treadmill Run', 1, 1, 1, null, null, 1800, 5.00, 0,  'Conversational pace.'),
        (gen_random_uuid(), v_plan, v_day_thu, ex_plank, 'Plank Hold',    2, 2, 3, null, null, 45,   null, 45, 'Brace and keep hips level.');

    insert into workout.workout_plan_exercise_set_targets
        (set_target_id, plan_exercise_id, set_number, target_reps, target_weight_kg,
         target_duration_seconds, target_distance_km, rest_seconds)
    select gen_random_uuid(), pe.plan_exercise_id, s.set_number,
           pe.target_reps, pe.target_weight_kg, pe.target_duration_seconds, pe.target_distance_km, pe.rest_seconds
    from workout.workout_plan_entries pe
    cross join (values (1),(2),(3)) as s(set_number)
    where pe.plan_id = v_plan
      and s.set_number <= pe.target_sets;

    -- 5d. one completed session per scheduled, non-skipped training day. Every entry carries logged_on,
    --     the session's local date in the user's zone, as WorkoutServiceImpl sets it; the history of one
    --     exercise is read by that date.
    for r in
        select * from demo_days
        where user_id = v_user and is_training_day and not skip_session and d < v_today
        order by d
    loop
        started   := (r.d + time '18:30') at time zone v_tz;
        v_session := gen_random_uuid();
        -- A moved session keeps the plan day it was meant for.
        v_plan_day := case when r.workout_kind = 'MOBILITY' then null else case coalesce(r.plan_dow, r.dow)
            when 1 then v_day_mon when 4 then v_day_thu when 5 then v_day_fri when 6 then v_day_sat end end;

        if r.workout_kind = 'MOBILITY' then
            -- A light day: a short morning mobility session outside the plan, core holds as its one entry.
            started := (r.d + time '06:45') at time zone v_tz;
            insert into workout.workout_sessions
                (session_id, user_id, plan_id, plan_day_id, name, workout_type, kind, status, source,
                 started_at, completed_at, duration_minutes, calories_burned,
                 perceived_exertion, notes, date_time_created, date_time_updated,
                 created_by, updated_by, version)
            values
                (v_session, v_user, null, null,
                 case when r.idx % 2 = 0 then 'Mobility flow' else 'Stretch and core' end,
                 'MOBILITY', 'MOBILITY', 'COMPLETED', 'MANUAL',
                 started, started + (r.duration_minutes * interval '1 minute'), r.duration_minutes, 90, 3,
                 'Easy morning session on a rest day.',
                 started, started + (r.duration_minutes * interval '1 minute'), 'demo-seed', 'demo-seed', 0);

            v_log := gen_random_uuid();
            insert into workout.workout_session_entries
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no, position,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes, logged_on)
            values (v_log, v_session, ex_plank, 'Plank Hold', 1, 1, 2, null, null, null, 90, 105, 115, null, r.d);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged, status)
            select gen_random_uuid(), v_log, s.n, null, null, 45, null, 45, 3, null, true, 'DONE'
            from (values (1),(2)) as s(n);

        elsif r.workout_kind = 'STRENGTH' then
            insert into workout.workout_sessions
                (session_id, user_id, plan_id, plan_day_id, name, workout_type, kind, status, source,
                 started_at, completed_at, duration_minutes, calories_burned,
                 perceived_exertion, notes, date_time_created, date_time_updated,
                 created_by, updated_by, version)
            values
                (v_session, v_user, v_plan, v_plan_day, 'Strength — Full Body', 'STRENGTH', 'STRENGTH', 'COMPLETED', 'PLAN',
                 started, started + (r.duration_minutes * interval '1 minute'), r.duration_minutes, 360, 7,
                 'Hit all working sets; progression felt manageable.',
                 started, started + (r.duration_minutes * interval '1 minute'), 'demo-seed', 'demo-seed', 0);

            -- bench
            v_log := gen_random_uuid();
            insert into workout.workout_session_entries
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no, position,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes, logged_on)
            values (v_log, v_session, ex_bench, 'Dumbbell Bench Press', 1, 1, 3, 30, 3 * 10 * r.bench_w, null, 360, 128, 145, null, r.d);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged, status)
            select gen_random_uuid(), v_log, s.n, 10, r.bench_w, null, null, 75, 6 + s.n - 1, null, true, 'DONE'
            from (values (1),(2),(3)) as s(n);

            -- squat
            v_log := gen_random_uuid();
            insert into workout.workout_session_entries
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no, position,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes, logged_on)
            values (v_log, v_session, ex_squat, 'Goblet Squat', 2, 2, 3, 36, 3 * 12 * r.squat_w, null, 420, 132, 150, null, r.d);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged, status)
            select gen_random_uuid(), v_log, s.n, 12, r.squat_w, null, null, 75, 6 + s.n - 1, null, true, 'DONE'
            from (values (1),(2),(3)) as s(n);

            -- row
            v_log := gen_random_uuid();
            insert into workout.workout_session_entries
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no, position,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes, logged_on)
            values (v_log, v_session, ex_row, 'Bent-Over Row', 3, 3, 3, 30, 3 * 10 * r.row_w, null, 360, 130, 147, null, r.d);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged, status)
            select gen_random_uuid(), v_log, s.n, 10, r.row_w, null, null, 75, 6 + s.n - 1, null, true, 'DONE'
            from (values (1),(2),(3)) as s(n);

            -- plank
            v_log := gen_random_uuid();
            insert into workout.workout_session_entries
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no, position,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes, logged_on)
            values (v_log, v_session, ex_plank, 'Plank Hold', 4, 4, 3, null, null, null, 135, 118, 128, 'Held all prescribed durations.', r.d);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged, status)
            select gen_random_uuid(), v_log, s.n, null, null, 45, null, 45, 6 + s.n - 1, null, true, 'DONE'
            from (values (1),(2),(3)) as s(n);

        elsif r.workout_kind = 'SPORT' then
            -- A sport session is measured by its time and has no exercises.
            insert into workout.workout_sessions
                (session_id, user_id, plan_id, plan_day_id, name, workout_type, kind, sport_activity, status, source,
                 started_at, completed_at, duration_minutes, calories_burned,
                 perceived_exertion, notes, date_time_created, date_time_updated,
                 created_by, updated_by, version)
            values
                (v_session, v_user, v_plan, v_plan_day, 'Badminton', 'SPORTS', 'SPORT', 'BADMINTON', 'COMPLETED', 'PLAN',
                 started, started + (r.duration_minutes * interval '1 minute'), r.duration_minutes, 400, 6,
                 'Doubles with friends.',
                 started, started + (r.duration_minutes * interval '1 minute'), 'demo-seed', 'demo-seed', 0);

        else  -- CARDIO
            insert into workout.workout_sessions
                (session_id, user_id, plan_id, plan_day_id, name, workout_type, kind, distance_km, status, source,
                 started_at, completed_at, duration_minutes, calories_burned,
                 perceived_exertion, notes, date_time_created, date_time_updated,
                 created_by, updated_by, version)
            values
                (v_session, v_user, v_plan, v_plan_day, 'Cardio — Treadmill', 'CARDIO', 'CARDIO', r.run_km, 'COMPLETED', 'PLAN',
                 started, started + (r.duration_minutes * interval '1 minute'), r.duration_minutes, 380, 6,
                 'Steady-state run, conversational pace.',
                 started, started + (r.duration_minutes * interval '1 minute'), 'demo-seed', 'demo-seed', 0);

            -- treadmill run
            v_log := gen_random_uuid();
            insert into workout.workout_session_entries
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no, position,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes, logged_on)
            values (v_log, v_session, ex_run, 'Treadmill Run', 1, 1, 1, null, null, r.run_km, 1800, 150, 168, null, r.d);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged, status)
            values (gen_random_uuid(), v_log, 1, null, null, 1800, r.run_km, 0, 6, null, true, 'DONE');

            -- plank finisher
            v_log := gen_random_uuid();
            insert into workout.workout_session_entries
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no, position,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes, logged_on)
            values (v_log, v_session, ex_plank, 'Plank Hold', 2, 2, 3, null, null, null, 135, 120, 130, null, r.d);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged, status)
            select gen_random_uuid(), v_log, s.n, null, null, 45, null, 45, 6 + s.n - 1, null, true, 'DONE'
            from (values (1),(2),(3)) as s(n);

            -- the run's time segments, planned against what ran: the steady part ran 26 minutes
            -- against 20 planned, which the coach's session read says in words.
            insert into workout.workout_session_entries
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no, position,
                 entry_type, kind, label, planned_seconds, actual_seconds, started_at, logged_on, status)
            values
                (gen_random_uuid(), v_session, null, 'Warm-up',   1001, 1, 'SEGMENT', 'WARMUP',   'Warm-up',   300,  300,
                 started, r.d, 'DONE'),
                (gen_random_uuid(), v_session, null, 'Steady',    1002, 2, 'SEGMENT', 'STEADY',   'Steady',    1200, 1560,
                 started + interval '5 minutes', r.d, 'DONE'),
                (gen_random_uuid(), v_session, null, 'Cool-down', 1003, 3, 'SEGMENT', 'COOLDOWN', 'Cool-down', 300,  300,
                 started + interval '31 minutes', r.d, 'DONE');
        end if;
    end loop;

    -- 5e. steady's goal is six sessions a week, and the full-week badges and the month panel's weeks on
    --     goal count sessions, not days. The older weeks hold only the plan's four days (three in the week
    --     with the dropped Saturday), so each completed week short of six gets a short evening mobility
    --     session at 19:45 after the plan session of one of its plan days (Monday, then Friday, Thursday,
    --     Saturday), until it reaches six. They fall on days that already have a session, so the active
    --     days, the streak and the weekly recap stay as they are. The last full week always has seven
    --     (mobility fills its rest days) and the current week is not touched; only weeks that ended before
    --     today get one, so nothing is written after the run. The first, partial week of the history
    --     cannot reach six and stays short.
    if v_persona = 'steady' then
        for r in
            with sessions as (
                select dd.*, (dd.d - (dd.dow - 1)) as week_start
                  from demo_days dd
                 where dd.user_id = v_user and dd.is_training_day and not dd.skip_session and dd.d < v_today
            ),
            per_week as (
                select week_start, count(*) as n from sessions group by week_start
            ),
            ranked as (
                select s.*, w.n,
                       row_number() over (partition by s.week_start
                                          order by case s.dow when 1 then 1 when 5 then 2 when 4 then 3 when 6 then 4 else 5 end, s.d) as rk
                  from sessions s
                  join per_week w using (week_start)
                 where s.week_start + 6 < v_today
                   and s.workout_kind <> 'MOBILITY'
            )
            select * from ranked where rk <= 6 - n order by d
        loop
            started   := (r.d + time '19:45') at time zone v_tz;
            v_session := gen_random_uuid();
            insert into workout.workout_sessions
                (session_id, user_id, plan_id, plan_day_id, name, workout_type, kind, status, source,
                 started_at, completed_at, duration_minutes, calories_burned,
                 perceived_exertion, notes, date_time_created, date_time_updated,
                 created_by, updated_by, version)
            values
                (v_session, v_user, null, null, 'Evening mobility', 'MOBILITY', 'MOBILITY', 'COMPLETED', 'MANUAL',
                 started, started + interval '15 minutes', 15, 60, 2,
                 'Short stretch after the plan session.',
                 started, started + interval '15 minutes', 'demo-seed', 'demo-seed', 0);

            v_log := gen_random_uuid();
            insert into workout.workout_session_entries
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no, position,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes, logged_on)
            values (v_log, v_session, ex_plank, 'Plank Hold', 1, 1, 2, null, null, null, 90, 100, 110, null, r.d);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged, status)
            select gen_random_uuid(), v_log, s.n, null, null, 45, null, 45, 2, null, true, 'DONE'
            from (values (1),(2)) as s(n);
        end loop;
    end if;
end $$;

-- ============================================================================
-- 6. ANALYTICS read-models — computed by aggregating the rows seeded above so
--    they stay self-consistent (the real service builds these from Kafka
--    events, which direct SQL inserts bypass). Every query here is scoped to
--    the seeded rows for this account, so a "fresh" account (no meals, no
--    sessions, no body metrics) naturally produces zero rows in every table
--    below — that IS its empty state, nothing further to guard.
-- ============================================================================

-- 6a. per-meal contributions
insert into analytics.analytics_meal_contributions
    (meal_id, user_id, activity_date, total_calories, total_protein_grams,
     total_carbs_grams, total_fat_grams, item_count, last_applied_at,
     date_time_created, date_time_updated, created_by, updated_by, version, meal_type)
select
    ml.meal_id, ml.user_id, (ml.consumed_at at time zone c.tz)::date,
    coalesce(sum(mi.calories), 0)::int, coalesce(sum(mi.protein_grams), 0), coalesce(sum(mi.carbs_grams), 0),
    coalesce(sum(mi.fat_grams), 0), count(mi.meal_item_id)::int, now(), now(), now(), 'demo-seed', 'demo-seed', 0, ml.meal_type
from demo_config c
join nutrition.meal_logs ml on ml.user_id = c.user_id and ml.created_by = 'demo-seed'
left join nutrition.meal_items mi on mi.meal_id = ml.meal_id  -- a FAST meal has no items and still counts as a logged meal
group by ml.meal_id, ml.user_id, ml.meal_type, (ml.consumed_at at time zone c.tz)::date;

-- 6b. daily nutrition roll-up (targets + adherence)
insert into analytics.analytics_user_daily_nutrition
    (user_id, activity_date, meal_count, total_calories_consumed, total_protein_grams,
     total_carbs_grams, total_fat_grams, calorie_target, protein_target_grams,
     calorie_adherence_ratio, macro_adherence_score, projection_updated_at,
     date_time_created, date_time_updated, created_by, updated_by, version)
select
    s.user_id, s.activity_date, s.meal_count, s.cals, s.protein, s.carbs, s.fat,
    2100, 140.00,
    round(s.cals / 2100.0, 4),
    round(s.protein / 140.0, 4),
    now(), now(), now(), 'demo-seed', 'demo-seed', 0
from (
    select ml.user_id,
           (ml.consumed_at at time zone c.tz)::date as activity_date,
           count(distinct ml.meal_id)               as meal_count,
           coalesce(sum(mi.calories), 0)            as cals,
           coalesce(sum(mi.protein_grams), 0)       as protein,
           coalesce(sum(mi.carbs_grams), 0)         as carbs,
           coalesce(sum(mi.fat_grams), 0)           as fat
    from demo_config c
    join nutrition.meal_logs ml on ml.user_id = c.user_id and ml.created_by = 'demo-seed'
    left join nutrition.meal_items mi on mi.meal_id = ml.meal_id
    group by ml.user_id, (ml.consumed_at at time zone c.tz)::date
) s;

-- 6c. per-session workout contributions
insert into analytics.analytics_workout_contributions
    (session_id, user_id, activity_date, duration_minutes, calories_burned,
     exercise_count, last_applied_at, date_time_created, date_time_updated,
     created_by, updated_by, version)
select
    ws.session_id, ws.user_id, (ws.started_at at time zone c.tz)::date,
    ws.duration_minutes, ws.calories_burned,
    (select count(*) from workout.workout_session_entries el where el.session_id = ws.session_id)::int,
    now(), now(), now(), 'demo-seed', 'demo-seed', 0
from demo_config c
join workout.workout_sessions ws on ws.user_id = c.user_id and ws.created_by = 'demo-seed';

-- 6d. daily workout roll-up (with rolling 7/28-day session counts)
insert into analytics.analytics_user_daily_workout
    (user_id, activity_date, completed_workout_count, total_workout_minutes,
     total_calories_burned, total_exercise_count, rolling_7_day_workout_count,
     rolling_28_day_workout_count, projection_updated_at, date_time_created,
     date_time_updated, created_by, updated_by, version)
with sess as (
    select ws.user_id,
           (ws.started_at at time zone c.tz)::date as activity_date,
           ws.session_id, ws.duration_minutes, ws.calories_burned,
           (select count(*) from workout.workout_session_entries el where el.session_id = ws.session_id) as exc
    from demo_config c
    join workout.workout_sessions ws
      on ws.user_id = c.user_id and ws.created_by = 'demo-seed' and ws.status = 'COMPLETED'
),
daily as (
    select user_id, activity_date,
           count(*)              as cnt,
           sum(duration_minutes) as mins,
           sum(calories_burned)  as cals,
           sum(exc)              as excs
    from sess
    group by user_id, activity_date
)
select
    d.user_id, d.activity_date, d.cnt::int, d.mins::int, d.cals::int, d.excs::int,
    (select count(*) from sess s where s.activity_date between d.activity_date - 6  and d.activity_date)::int,
    (select count(*) from sess s where s.activity_date between d.activity_date - 27 and d.activity_date)::int,
    now(), now(), now(), 'demo-seed', 'demo-seed', 0
from daily d;

-- 6e. daily body roll-up (BMI + weight deltas)
insert into analytics.analytics_user_daily_body
    (user_id, metric_date, weight_kg, body_fat_rate, waist_cm, bmi,
     weight_delta_from_start, weight_delta_from_previous, projection_updated_at,
     date_time_created, date_time_updated, created_by, updated_by, version)
with b as (
    select bm.user_id,
           (bm.measured_at at time zone c.tz)::date as metric_date,
           bm.weight_kg, bm.body_fat_rate, bm.waist_cm,
           (select height_cm from user_domain.user_profiles p where p.user_id = c.user_id) as height_cm,
           first_value(bm.weight_kg) over (order by bm.measured_at) as start_w,
           lag(bm.weight_kg)         over (order by bm.measured_at) as prev_w
    from demo_config c
    join user_domain.body_metric_entries bm
      on bm.user_id = c.user_id and bm.created_by = 'demo-seed'
)
select
    b.user_id, b.metric_date, b.weight_kg, b.body_fat_rate, b.waist_cm,
    case when b.height_cm is not null and b.height_cm > 0
         then round(b.weight_kg / power(b.height_cm / 100.0, 2), 2) end,
    round(b.weight_kg - b.start_w, 2),
    case when b.prev_w is not null then round(b.weight_kg - b.prev_w, 2) end,
    now(), now(), now(), 'demo-seed', 'demo-seed', 0
from b;

-- 6f. goal progress (one row per active goal)
insert into analytics.analytics_user_goal_progress
    (goal_id, user_id, goal_type, goal_status, target_value, target_unit, target_date,
     baseline_value, current_value, progress_ratio, days_remaining, on_track, last_evaluated_at,
     date_time_created, date_time_updated, created_by, updated_by, version)
select
    g.goal_id, g.user_id, g.goal_type, g.status, g.target_value, g.target_unit, g.target_date,
    cv.baseline_value, cv.current_value, cv.progress_ratio,
    (g.target_date - c.d_today)::int,
    cv.on_track,
    now(), now(), now(), 'demo-seed', 'demo-seed', 0
from demo_config c
join user_domain.user_goals g on g.user_id = c.user_id and g.created_by = 'demo-seed'
join lateral (
    select
        (select bd.weight_kg from analytics.analytics_user_daily_body bd
         where bd.user_id = g.user_id order by bd.metric_date asc limit 1) as baseline_weight,
        (select bd.metric_date from analytics.analytics_user_daily_body bd
         where bd.user_id = g.user_id order by bd.metric_date asc limit 1) as baseline_date,
        (select bd.weight_kg from analytics.analytics_user_daily_body bd
         where bd.user_id = g.user_id order by bd.metric_date desc limit 1) as latest_weight,
        coalesce((select n.total_calories_consumed from analytics.analytics_user_daily_nutrition n
                  where n.user_id = g.user_id and n.activity_date = c.d_today), 0)::numeric as calories_today,
        coalesce((select n.total_protein_grams from analytics.analytics_user_daily_nutrition n
                  where n.user_id = g.user_id and n.activity_date = c.d_today), 0)::numeric as protein_today,
        coalesce((select n.total_carbs_grams from analytics.analytics_user_daily_nutrition n
                  where n.user_id = g.user_id and n.activity_date = c.d_today), 0)::numeric as carbs_today,
        coalesce((select n.total_fat_grams from analytics.analytics_user_daily_nutrition n
                  where n.user_id = g.user_id and n.activity_date = c.d_today), 0)::numeric as fat_today,
        coalesce((select sum(w.completed_workout_count) from analytics.analytics_user_daily_workout w
                  where w.user_id = g.user_id and w.activity_date between c.d_today - 6 and c.d_today), 0)::numeric
            as workouts_7d
) inputs on true
join lateral (
    select
        case g.goal_type
            when 'WEIGHT' then inputs.baseline_weight
        end as baseline_value,
        case g.goal_type
            when 'WEIGHT' then inputs.latest_weight
            when 'CALORIES' then inputs.calories_today
            when 'PROTEIN' then inputs.protein_today
            when 'CARBS' then inputs.carbs_today
            when 'FAT' then inputs.fat_today
            when 'WORKOUT_FREQUENCY' then inputs.workouts_7d
        end as current_value,
        case g.goal_type
            when 'WEIGHT' then
                case
                    when inputs.baseline_weight is null or inputs.latest_weight is null or g.target_value is null then null
                    when g.target_value < inputs.baseline_weight then round(least(greatest(
                        (inputs.baseline_weight - inputs.latest_weight)
                        / nullif(inputs.baseline_weight - g.target_value, 0), 0), 1), 4)
                    when g.target_value > inputs.baseline_weight then round(least(greatest(
                        (inputs.latest_weight - inputs.baseline_weight)
                        / nullif(g.target_value - inputs.baseline_weight, 0), 0), 1), 4)
                    when abs(inputs.latest_weight - inputs.baseline_weight) <= 0.50 then 1.0000
                    else round(greatest(
                        1 - ((abs(inputs.latest_weight - inputs.baseline_weight) - 0.50) / 0.50), 0), 4)
                end
            when 'CALORIES' then round(inputs.calories_today / nullif(g.target_value, 0), 4)
            when 'PROTEIN' then round(inputs.protein_today / nullif(g.target_value, 0), 4)
            when 'CARBS' then round(inputs.carbs_today / nullif(g.target_value, 0), 4)
            when 'FAT' then round(inputs.fat_today / nullif(g.target_value, 0), 4)
            when 'WORKOUT_FREQUENCY' then round(inputs.workouts_7d / nullif(g.target_value, 0), 4)
        end as progress_ratio,
        case g.goal_type
            when 'WEIGHT' then
                case
                    when inputs.baseline_weight is null or inputs.latest_weight is null or g.target_value is null then false
                    when g.target_value = inputs.baseline_weight then
                        abs(inputs.latest_weight - inputs.baseline_weight) <= 0.50
                    when g.target_date is not null
                            and inputs.baseline_date is not null
                            and g.target_date > inputs.baseline_date
                            and g.target_value < inputs.baseline_weight then
                        inputs.latest_weight <= inputs.baseline_weight
                            + (g.target_value - inputs.baseline_weight)
                            * (least(greatest((c.d_today - inputs.baseline_date)::numeric, 0),
                                      (g.target_date - inputs.baseline_date)::numeric)
                               / nullif((g.target_date - inputs.baseline_date)::numeric, 0))
                    when g.target_date is not null
                            and inputs.baseline_date is not null
                            and g.target_date > inputs.baseline_date then
                        inputs.latest_weight >= inputs.baseline_weight
                            + (g.target_value - inputs.baseline_weight)
                            * (least(greatest((c.d_today - inputs.baseline_date)::numeric, 0),
                                      (g.target_date - inputs.baseline_date)::numeric)
                               / nullif((g.target_date - inputs.baseline_date)::numeric, 0))
                    when g.target_value < inputs.baseline_weight then inputs.latest_weight <= inputs.baseline_weight
                    else inputs.latest_weight >= inputs.baseline_weight
                end
            when 'CALORIES' then g.target_value is not null and g.target_value > 0 and inputs.calories_today <= g.target_value
            when 'PROTEIN' then g.target_value is not null and g.target_value > 0 and inputs.protein_today >= g.target_value
            when 'CARBS' then g.target_value is not null and g.target_value > 0 and inputs.carbs_today <= g.target_value
            when 'FAT' then g.target_value is not null and g.target_value > 0 and inputs.fat_today <= g.target_value
            when 'WORKOUT_FREQUENCY' then g.target_value is not null and g.target_value > 0
                and inputs.workouts_7d >= floor(g.target_value * extract(isodow from c.d_today) / 7)
            else false
        end as on_track
) cv on true;

-- 6g. dashboard snapshot (single row the home screen reads)
insert into analytics.analytics_user_dashboard_snapshot
    (user_id, timezone, latest_weight_kg, latest_body_fat_rate, bmi, weight_change_30d,
     latest_waist_cm, waist_measured_on,
     today_workout_count, week_workout_count, week_workout_minutes, active_streak_days,
     today_calories_consumed, today_calories_target, today_protein_grams,
     today_protein_target_grams, generated_at, date_time_created, date_time_updated,
     created_by, updated_by, version)
select
    c.user_id, c.tz,
    bd.weight_kg, bd.body_fat_rate, bd.bmi,
    -- BodyWeightChange.over30Days: the latest weight minus the weight on the newest body row dated 30 or
    -- more days before it. Null when there is no such row or it has no weight, so a shorter span is never
    -- shown as a 30-day change.
    (select bd.weight_kg - old.weight_kg
       from analytics.analytics_user_daily_body old
      where old.user_id = c.user_id
        and old.metric_date <= bd.metric_date - 30
      order by old.metric_date desc
      limit 1),
    (select w.waist_cm from analytics.analytics_user_daily_body w
      where w.user_id = c.user_id and w.waist_cm is not null
      order by w.metric_date desc limit 1),
    (select w.metric_date from analytics.analytics_user_daily_body w
      where w.user_id = c.user_id and w.waist_cm is not null
      order by w.metric_date desc limit 1),
    coalesce((select completed_workout_count from analytics.analytics_user_daily_workout w
              where w.user_id = c.user_id and w.activity_date = c.d_today), 0),
    coalesce((select sum(completed_workout_count) from analytics.analytics_user_daily_workout w
              where w.user_id = c.user_id and w.activity_date between c.d_today - 6 and c.d_today), 0)::int,
    coalesce((select sum(total_workout_minutes) from analytics.analytics_user_daily_workout w
              where w.user_id = c.user_id and w.activity_date between c.d_today - 6 and c.d_today), 0)::int,
    streak.days,
    (select total_calories_consumed from analytics.analytics_user_daily_nutrition n
     where n.user_id = c.user_id and n.activity_date = c.d_today),
    2100,
    (select total_protein_grams from analytics.analytics_user_daily_nutrition n
     where n.user_id = c.user_id and n.activity_date = c.d_today),
    140.00,
    now(), now(), now(), 'demo-seed', 'demo-seed', 0
from demo_config c
join lateral (
    select metric_date, weight_kg, body_fat_rate, bmi
    from analytics.analytics_user_daily_body bd
    where bd.user_id = c.user_id
    order by bd.metric_date desc
    limit 1
) bd on true
cross join lateral (
    -- ActiveStreak.days: consecutive days with a completed workout, walking back from today, or from
    -- yesterday when today has no row yet; 0 once the latest active day is two or more days back. The
    -- dashboard, getWorkoutSummary and the coach context all read this one number.
    select count(*)::int as days
      from (
            select w.activity_date,
                   w.activity_date + (row_number() over (order by w.activity_date desc))::int as anchor
              from analytics.analytics_user_daily_workout w
             where w.user_id = c.user_id
               and w.completed_workout_count > 0
               and w.activity_date between c.d_today - 400 and c.d_today
           ) runs
     where runs.anchor = (
            -- Where the streak starts: today when today is active, else yesterday when yesterday is.
            -- With neither, no date matches and the count is 0.
            select case
                       when exists (select 1 from analytics.analytics_user_daily_workout t
                                     where t.user_id = c.user_id and t.activity_date = c.d_today
                                       and t.completed_workout_count > 0)
                           then c.d_today + 1
                       when exists (select 1 from analytics.analytics_user_daily_workout y
                                     where y.user_id = c.user_id and y.activity_date = c.d_today - 1
                                       and y.completed_workout_count > 0)
                           then c.d_today
                   end)
) streak;

-- 6h. personal-record tally — the table the achievements endpoint reads
--     (analytics.analytics_user_workout_records: one row per user, a running
--     personal_record_count + first/last timestamps; see
--     WorkoutRecordsProjectionService / AnalyticsAchievementsResponse). A
--     record here is a session where a lift's weight beat every one of that
--     lift's earlier sessions — exactly what workout-service's
--     PersonalRecordAchieved event marks live. Guarded on the table existing,
--     and naturally empty for "fresh" (no sessions to rank).
do $$
declare
    v_user uuid;
    v_persona text;
begin
    select user_id, persona into v_user, v_persona from demo_config;
    if v_persona = 'fresh' then
        return;
    end if;
    if not exists (select 1 from information_schema.tables where table_schema = 'analytics' and table_name = 'analytics_user_workout_records') then
        return;
    end if;

    insert into analytics.analytics_user_workout_records
        (user_id, personal_record_count, first_record_at, last_record_at,
         date_time_created, date_time_updated, created_by, updated_by, version)
    select v_user, pr.cnt, pr.first_at, pr.last_at, now(), now(), 'demo-seed', 'demo-seed', 0
    from (
        select count(*) as cnt, min(achieved_at) as first_at, max(achieved_at) as last_at
        from (
            select achieved_at, exercise_name, weight_kg,
                   max(weight_kg) over (
                       partition by exercise_name order by achieved_at
                       rows between unbounded preceding and 1 preceding
                   ) as prior_max
            from (
                select s.completed_at as achieved_at, e.exercise_name_snapshot as exercise_name,
                       max(sl.weight_kg) as weight_kg
                from workout.workout_sessions s
                join workout.workout_session_entries e on e.session_id = s.session_id
                join workout.workout_exercise_set_logs sl on sl.exercise_log_id = e.exercise_log_id
                where s.user_id = v_user and s.created_by = 'demo-seed' and sl.weight_kg is not null
                group by s.completed_at, e.exercise_name_snapshot
            ) sess
        ) ranked
        where prior_max is null or weight_kg > prior_max
    ) pr
    where pr.cnt > 0
    on conflict (user_id) do update set
        personal_record_count = excluded.personal_record_count,
        first_record_at = excluded.first_record_at,
        last_record_at = excluded.last_record_at,
        date_time_updated = now(),
        updated_by = 'demo-seed';
end $$;

-- ----------------------------------------------------------------------------
-- 6i. Recipe cooks, the cook counter, the streak rollup and the energy profile.
--
--     Cooks: three for steady (3, 6 and 9 days before today, the two newest with servings left over so
--     the Leftovers list has something to offer), one older cook for drifting, none for fresh. They
--     point at published system recipes, picked by name, so no recipe needs inserting. The analytics
--     cook counter is rebuilt from the cook rows, the same total the RecipeCooked events would have
--     produced, and it feeds the kitchen badges.
--
--     Streak rollup: the nightly job's output (best run of consecutive training days), computed here
--     from the daily workout rows so the Progress page does not wait for the job.
--
--     Energy profile: the BMR the Progress energy view reads, one row dated at the start of the window,
--     from the Mifflin-St Jeor equation when the profile has what it needs and flagged as estimated
--     when it does not.
-- ----------------------------------------------------------------------------
create temporary table demo_cook_plan on commit drop as
select c.user_id, c.tz, v.n, v.recipe_name, v.servings, v.servings_left,
       ((c.d_today - v.days_ago) + time '19:30') at time zone c.tz as finished_at
from demo_config c
join (values
    -- servings_left: the whole servings still uneaten. The Leftovers list shows a cook finished in the
    -- last 7 days with servings_left > 0 (RecipeCookService.leftovers), so steady's two cooks of 3 and 6
    -- days ago keep some back; the older cooks were eaten up. Never above the servings cooked.
    (1, 'Salmon Avocado Salad'::text, 2.00::numeric,  1,  3, 'steady'::text),
    (2, 'Spanish Chicken',            4.00,           2,  6, 'steady'),
    (3, 'Sweet potato salad',         4.00,           0,  9, 'steady'),
    (1, 'Spanish Chicken',            4.00,           0, 24, 'drifting')
) as v(n, recipe_name, servings, servings_left, days_ago, persona) on v.persona = c.persona;

do $$
begin
    if (select count(*) from demo_cook_plan p
         where not exists (select 1 from nutrition.recipes r
                            where r.name = p.recipe_name and r.scope = 'SYSTEM'
                              and r.published and r.deleted_at is null)) > 0 then
        raise exception 'A recipe the demo cooks is missing from the system library. Import the recipe library first.';
    end if;
end $$;

insert into nutrition.recipe_cooks
    (cook_id, user_id, recipe_id, client_cook_id, servings, servings_left, started_at, finished_at, created_at)
select md5(p.user_id::text || 'cook' || p.n::text)::uuid,
       p.user_id,
       (select r.recipe_id from nutrition.recipes r
         where r.name = p.recipe_name and r.scope = 'SYSTEM' and r.published and r.deleted_at is null
         order by r.recipe_id limit 1),
       md5(p.user_id::text || 'client-cook' || p.n::text)::uuid,
       p.servings,
       p.servings_left,
       p.finished_at - interval '50 minutes',
       p.finished_at,
       p.finished_at
from demo_cook_plan p;

do $$
begin
    if exists (select 1 from information_schema.tables where table_schema = 'analytics' and table_name = 'analytics_user_cooks') then
        insert into analytics.analytics_user_cooks (user_id, cooks, first_cook_at, last_cook_at)
        select rc.user_id, count(*)::int, min(rc.finished_at), max(rc.finished_at)
          from nutrition.recipe_cooks rc
          join demo_config c on c.user_id = rc.user_id
         where rc.deleted_at is null
         group by rc.user_id
        on conflict (user_id) do update set
            cooks = excluded.cooks,
            first_cook_at = excluded.first_cook_at,
            last_cook_at = excluded.last_cook_at;
    end if;
end $$;

insert into analytics.analytics_user_streak_rollup
    (user_id, best_streak_days, best_streak_from, best_streak_to, last_active_date, computed_at,
     date_time_created, date_time_updated, created_by, updated_by, version)
select c.user_id, best.len, best.from_d, best.to_d,
       (select max(w.activity_date) from analytics.analytics_user_daily_workout w
         where w.user_id = c.user_id and w.completed_workout_count > 0),
       now(), now(), now(), 'demo-seed', 'demo-seed', 0
from demo_config c
join lateral (
    -- Runs of consecutive active dates; the longest wins, the most recent on a tie, as the job does.
    select count(*)::int as len, min(r.d) as from_d, max(r.d) as to_d
      from (select w.activity_date as d,
                   w.activity_date - (row_number() over (order by w.activity_date))::int as grp
              from analytics.analytics_user_daily_workout w
             where w.user_id = c.user_id and w.completed_workout_count > 0) r
     group by r.grp
     order by count(*) desc, max(r.d) desc
     limit 1
) best on true;

insert into analytics.analytics_user_energy_profiles
    (event_id, user_id, bmr_kcal, estimated, computed_at,
     date_time_created, date_time_updated, created_by, updated_by, version)
select md5(c.user_id::text || 'energy-profile')::uuid,
       c.user_id,
       round(10 * w.weight_kg + 6.25 * p.height_cm
             - 5 * extract(year from age(c.d_today, p.birth_date))
             + case p.gender when 'MALE' then 5 when 'FEMALE' then -161 else -78 end)::int,
       p.gender not in ('MALE', 'FEMALE'),
       (c.d_start + time '08:00') at time zone c.tz,
       now(), now(), 'demo-seed', 'demo-seed', 0
from demo_config c
join user_domain.user_profiles p on p.user_id = c.user_id
join lateral (
    select bd.weight_kg from analytics.analytics_user_daily_body bd
     where bd.user_id = c.user_id and bd.weight_kg is not null
     order by bd.metric_date asc limit 1
) w on true
where p.height_cm is not null and p.birth_date is not null;

-- ----------------------------------------------------------------------------
-- 8b. Fields the Phase 2 work added after this seed was written.
--
--     The script predates three things and so left them empty, which meant the features built on
--     them had nothing to show: the rules engine could not judge weekly adherence without a target,
--     and the food-group breakdown, the payoff of the whole open-data thread, was blank.
--
--     Applied for every persona, "fresh" included — the product owner wants a fresh account's
--     profile and preferences filled in exactly like the others, just with no activity yet.
-- ----------------------------------------------------------------------------

-- Training preferences. R2 compares active days against weekly_workout_days when there is no
-- WORKOUT_FREQUENCY goal, so without this the weekly-adherence rule could not fire for such an account.
-- steady's matches its goal of six days, so About you and the coach say one number; drifting and fresh
-- keep five.
insert into user_domain.user_training_preferences
    (user_id, fitness_level_code, training_location_code, weekly_workout_days,
     preferred_duration_minutes, preferred_workout_time, intensity_preference_code,
     date_time_created, date_time_updated, created_by, updated_by, version)
-- Codes are looked up by display name rather than pasted in, so the seed survives a renumbering
-- of the code tables and reads as what it means.
select c.user_id,
       (select cv.code from user_domain.code_values cv
         where cv.code_set_key = 'fitness_level' and cv.display_name = 'Intermediate'),
       (select cv.code from user_domain.code_values cv
         where cv.code_set_key = 'training_location' and cv.display_name = 'Gym'),
       case when c.persona = 'steady' then 6 else 5 end, 45, time '19:00',
       (select cv.code from user_domain.code_values cv
         where cv.code_set_key = 'intensity_preference' and cv.display_name = 'Moderate'),
       now(), now(), 'demo-seed', 'demo-seed', 0
from demo_config c
on conflict (user_id) do update
   set weekly_workout_days = excluded.weekly_workout_days,
       preferred_duration_minutes = excluded.preferred_duration_minutes,
       date_time_updated = now();

insert into user_domain.user_training_preference_focus_areas (user_id, sort_order, focus_area_code)
select c.user_id, f.sort_order, f.code
from demo_config c
cross join lateral (
    select row_number() over (order by cv.sort_order) - 1 as sort_order, cv.code
      from user_domain.code_values cv
     where cv.code_set_key = 'training_focus_area'
       and cv.display_name in ('Upper body', 'Lower body', 'Core')
) f
on conflict do nothing;

insert into user_domain.user_training_preference_equipment (user_id, sort_order, equipment_code)
select c.user_id, e.sort_order, e.code
from demo_config c
cross join lateral (
    select row_number() over (order by cv.sort_order) - 1 as sort_order, cv.code
      from user_domain.code_values cv
     where cv.code_set_key = 'training_equipment'
       and cv.display_name in ('Barbell', 'Dumbbells', 'Full gym')
) e
on conflict do nothing;

-- Food-group classification: the seeded items are linked to the USDA catalogue (section 3b), which
-- gives them their category_code, so the food-group breakdown below needs no mapping by name.

-- Per-day food-group energy, the aggregate AN-1 maintains from MealLogged.foodGroupCalories.
-- Encoded as CODE=kcal;CODE=kcal, the compact internal format the analytics column already uses.
update analytics.analytics_user_daily_nutrition n
   set food_group_calories = agg.encoded
  from (
        select ml.user_id,
               (ml.consumed_at at time zone c.tz)::date as activity_date,
               string_agg(x.code || '=' || round(x.kcal)::text, ';' order by x.kcal desc) as encoded
          from demo_config c
          join nutrition.meal_logs ml on ml.user_id = c.user_id
          join lateral (
                select mi.category_code as code, sum(coalesce(mi.calories, 0)) as kcal
                  from nutrition.meal_items mi
                 where mi.meal_id = ml.meal_id
                   and mi.category_code is not null
                 group by mi.category_code
          ) x on true
         group by 1, 2
       ) agg
 where n.user_id = agg.user_id
   and n.activity_date = agg.activity_date;

-- ----------------------------------------------------------------------------
-- 9. recommendation-service context projection.
--
--    Every value below is SELECTed from rows this script has just inserted, in the same
--    transaction. Nothing is hardcoded, for two reasons.
--
--    The coach must not contradict the screen it sits on. Its numbers come from the same
--    analytics rows the dashboard renders, so a demo cannot show "5 workouts this week" beside a
--    month view reading zero.
--
--    The context is date-relative, and a literal date would not merely age, it would give the
--    wrong answer. R1 only counts intake when today_date equals the actual current date, and R8
--    measures a logging lapse from it. Joining demo_config inherits this script's rolling anchor,
--    so the context is correct on whatever day the demo is recorded.
--
--    Normally these columns are filled by thirteen Kafka consumers. A fresh environment has no
--    event history to replay, which is the gap described in
--    Documents/architecture/projection-bootstrap-design.md; this is its Layer 1. Once the rebuild
--    endpoint in Layer 2 exists, most of this section can be replaced by a call to it.
--
--    Guarded on the schema existing, because recommendation-service is branch-only. For a
--    "fresh" account the goal/body/workout lookups all come back null via the LEFT/lateral joins
--    below, so the row still gets created with just the WORKOUT_FREQUENCY goal and preferences —
--    "the recommendation context row exists with the goal and preferences and nothing else."
-- ----------------------------------------------------------------------------
do $do$
begin
    if not exists (select 1 from pg_namespace where nspname = 'recommendation') then
        raise notice 'recommendation schema absent - skipping the coach context seed';
        return;
    end if;

    insert into recommendation.recommendation_context (
        user_id, timezone, today_date,
        current_goal_type, current_goal_target_value, current_goal_target_unit, current_goal_target_date,
        goal_baseline_weight_kg, goal_baseline_on,
        energy_target_calories, protein_target_grams,
        latest_weight_kg, latest_body_fat_rate, body_metrics_recorded_at,
        height_cm, activity_level, workout_days_per_week, dietary_preference,
        focus_areas, equipment_inventory,
        recent_week_start, recent_workout_count, recent_workout_minutes, recent_active_days,
        today_calories_consumed, today_protein_grams, current_streak_days,
        nutrition_week_start, week_food_group_calories,
        last_pr_exercise, last_pr_record_type, last_pr_value, last_pr_at,
        stale, context_hash, last_assembled_at,
        date_time_created, date_time_updated, created_by, updated_by, version
    )
    select
        c.user_id,
        c.tz,
        -- today_date is the day of the last logged meal, as MealLoggedEventHandler keeps it: today once
        -- today's breakfast is in, yesterday on a run before breakfast, and days back for drifting, whose
        -- quiet days the logging reminder (R8) counts from it. Fresh has no meals and keeps today.
        case when c.persona = 'fresh' then c.d_today
             else (select max(n.activity_date) from analytics.analytics_user_daily_nutrition n
                    where n.user_id = c.user_id and n.meal_count > 0) end,
        wg.goal_type, wg.target_value, wg.target_unit, wg.target_date,
        -- Pace baseline: earliest weight inside the demo window, so R3 has both ends of the line.
        (select bd.weight_kg from analytics.analytics_user_daily_body bd
          where bd.user_id = c.user_id and bd.weight_kg is not null
          order by bd.metric_date asc limit 1),
        (select bd.metric_date from analytics.analytics_user_daily_body bd
          where bd.user_id = c.user_id and bd.weight_kg is not null
          order by bd.metric_date asc limit 1),
        -- The targets from the newest nutrition day, which is yesterday on a run before breakfast.
        (select n.calorie_target::int from analytics.analytics_user_daily_nutrition n
          where n.user_id = c.user_id order by n.activity_date desc limit 1),
        (select n.protein_target_grams from analytics.analytics_user_daily_nutrition n
          where n.user_id = c.user_id order by n.activity_date desc limit 1),
        bd_latest.weight_kg, bd_latest.body_fat_rate,
        (bd_latest.metric_date + time '08:00') at time zone c.tz,
        p.height_cm, p.activity_level,
        tp.weekly_workout_days, np.dietary_pattern_code,
        (select string_agg(fa.focus_area_code, ',' order by fa.sort_order)
           from user_domain.user_training_preference_focus_areas fa
          where fa.user_id = c.user_id),
        (select string_agg(eq.equipment_code, ',' order by eq.sort_order)
           from user_domain.user_training_preference_equipment eq
          where eq.user_id = c.user_id),
        recap.week_start, recap.workout_count, recap.workout_minutes, recap.active_days,
        (select n.total_calories_consumed from analytics.analytics_user_daily_nutrition n
          where n.user_id = c.user_id and n.activity_date = c.d_today),
        (select n.total_protein_grams from analytics.analytics_user_daily_nutrition n
          where n.user_id = c.user_id and n.activity_date = c.d_today),
        snap.active_streak_days,
        wk.week_start,
        -- Weekly food-group totals, re-encoded into the CODE=kcal;CODE=kcal form the rules parse.
        (select string_agg(agg.code || '=' || round(agg.kcal)::text, ';' order by agg.kcal desc)
           from (
                 select split_part(pair, '=', 1) as code,
                        sum(split_part(pair, '=', 2)::numeric) as kcal
                   from analytics.analytics_user_daily_nutrition n
                   cross join lateral unnest(string_to_array(coalesce(n.food_group_calories, ''), ';')) as pair
                  where n.user_id = c.user_id
                    and n.activity_date >= wk.week_start
                    and pair <> ''
                  group by 1
                ) agg),
        pr.exercise_name, pr.record_type, pr.record_value, pr.achieved_at,
        -- Left stale with no hash, so the first read recomputes the rules against this context.
        true, null, null,
        now(), now(), 'demo-seed', 'demo-seed', 0
    from demo_config c
    join user_domain.user_profiles p on p.user_id = c.user_id
    left join user_domain.user_training_preferences tp on tp.user_id = c.user_id
    left join user_domain.user_nutrition_preferences np on np.user_id = c.user_id
    left join analytics.analytics_user_dashboard_snapshot snap on snap.user_id = c.user_id
    left join lateral (
        select bd.weight_kg, bd.body_fat_rate, bd.metric_date
          from analytics.analytics_user_daily_body bd
         where bd.user_id = c.user_id
         order by bd.metric_date desc
         limit 1
    ) bd_latest on true
    left join lateral (
        select g.goal_type, g.target_value, g.target_unit, g.target_date
          from user_domain.user_goals g
         where g.user_id = c.user_id and g.goal_type = 'WEIGHT'
         limit 1
    ) wg on true
    left join lateral (
        -- Monday of the current week, which is where the food-group tally starts.
        select (c.d_today - ((extract(isodow from c.d_today)::int) - 1))::date as week_start
    ) wk on true
    left join lateral (
        -- The last full week, Monday to Sunday. The live value comes from the weekly recap, which
        -- analytics-service writes for the week before the current one, so the seed describes the
        -- same week. The current week would read as missed on any day before its last session.
        -- A fresh account has no last week, so its fields stay empty and the rule says nothing.
        -- Active days are counted as AnalyticsWeeklyRecapService.emitWeeklyRecap counts them: every date
        -- of the week with a daily workout row or a daily nutrition row, whatever the row holds.
        select (wk.week_start - 7)                                             as week_start,
               coalesce((select sum(w.completed_workout_count)
                           from analytics.analytics_user_daily_workout w
                          where w.user_id = c.user_id
                            and w.activity_date >= wk.week_start - 7
                            and w.activity_date < wk.week_start), 0)::int      as workout_count,
               coalesce((select sum(w.total_workout_minutes)
                           from analytics.analytics_user_daily_workout w
                          where w.user_id = c.user_id
                            and w.activity_date >= wk.week_start - 7
                            and w.activity_date < wk.week_start), 0)::int      as workout_minutes,
               (select count(*)::int
                  from (select w.activity_date
                          from analytics.analytics_user_daily_workout w
                         where w.user_id = c.user_id
                           and w.activity_date >= wk.week_start - 7
                           and w.activity_date < wk.week_start
                        union
                        select n.activity_date
                          from analytics.analytics_user_daily_nutrition n
                         where n.user_id = c.user_id
                           and n.activity_date >= wk.week_start - 7
                           and n.activity_date < wk.week_start) active)         as active_days
         where c.persona <> 'fresh'
    ) recap on true
    left join lateral (
        -- Heaviest single set in the window, dated to the first session that lifted it, which is
        -- the day the record was set. Later sessions at the same weight repeat it.
        select e.exercise_name_snapshot as exercise_name,
               'MAX_WEIGHT'             as record_type,
               sl.weight_kg             as record_value,
               min(s.completed_at)      as achieved_at
          from workout.workout_sessions s
          join workout.workout_session_entries e on e.session_id = s.session_id
          join workout.workout_exercise_set_logs sl on sl.exercise_log_id = e.exercise_log_id
         where s.user_id = c.user_id
           and s.completed_at is not null
           and sl.weight_kg is not null
         group by e.exercise_name_snapshot, sl.weight_kg
         order by sl.weight_kg desc, min(s.completed_at)
         limit 1
    ) pr on true;

    raise notice 'coach context seeded';
end
$do$;

-- 9b. Three columns Phase-2 added to recommendation_context after this seed was written:
--     weekly_workout_goal_days (from the WORKOUT_FREQUENCY goal), protein_target_grams (from
--     the PROTEIN goal, overriding the nutrition-target-derived value above with the goal's own
--     number) and calorie_goal_kcal (from the CALORIES goal). Backfilled as separate guarded
--     UPDATEs, rather than folded into the INSERT above, so the script still runs unmodified
--     against a database built before those migrations (V9 and V12).
do $$
begin
    if exists (select 1 from pg_namespace where nspname = 'recommendation')
       and exists (
           select 1 from information_schema.columns
            where table_schema = 'recommendation' and table_name = 'recommendation_context'
              and column_name = 'weekly_workout_goal_days'
       ) then
        update recommendation.recommendation_context rc
           set weekly_workout_goal_days = g.target_value::int
          from demo_config c
          join user_domain.user_goals g on g.user_id = c.user_id and g.goal_type = 'WORKOUT_FREQUENCY'
         where rc.user_id = c.user_id;
    end if;

    if exists (select 1 from pg_namespace where nspname = 'recommendation')
       and exists (
           select 1 from information_schema.columns
            where table_schema = 'recommendation' and table_name = 'recommendation_context'
              and column_name = 'protein_target_grams'
       ) then
        update recommendation.recommendation_context rc
           set protein_target_grams = g.target_value
          from demo_config c
          join user_domain.user_goals g on g.user_id = c.user_id and g.goal_type = 'PROTEIN'
         where rc.user_id = c.user_id;
    end if;

    if exists (select 1 from pg_namespace where nspname = 'recommendation')
       and exists (
           select 1 from information_schema.columns
            where table_schema = 'recommendation' and table_name = 'recommendation_context'
              and column_name = 'calorie_goal_kcal'
       ) then
        update recommendation.recommendation_context rc
           set calorie_goal_kcal = g.target_value::int
          from demo_config c
          join user_domain.user_goals g on g.user_id = c.user_id and g.goal_type = 'CALORIES'
         where rc.user_id = c.user_id;
    end if;

    -- current_goal_id (V15): the WEIGHT goal the current-goal slot holds, so that the GoalUpdated the seed
    -- requeues above is read as the same goal and keeps the pace baseline instead of taking a new one.
    -- Section 4b gives that goal the id md5(user_id || 'GOAL' || goal_type).
    if exists (select 1 from pg_namespace where nspname = 'recommendation')
       and exists (
           select 1 from information_schema.columns
            where table_schema = 'recommendation' and table_name = 'recommendation_context'
              and column_name = 'current_goal_id'
       ) then
        update recommendation.recommendation_context rc
           set current_goal_id = g.goal_id
          from demo_config c
          join user_domain.user_goals g on g.user_id = c.user_id and g.goal_type = 'WEIGHT'
         where rc.user_id = c.user_id
           and g.goal_id = md5(c.user_id::text || 'GOAL' || 'WEIGHT')::uuid;
    end if;
end $$;

commit;

-- ============================================================================
-- Quick verification (run after committing):
--   select activity_date, total_calories_consumed, calorie_adherence_ratio
--     from analytics.analytics_user_daily_nutrition
--    where created_by='demo-seed' order by activity_date;
--   select activity_date, completed_workout_count, total_workout_minutes
--     from analytics.analytics_user_daily_workout
--    where created_by='demo-seed' order by activity_date;
-- ============================================================================
