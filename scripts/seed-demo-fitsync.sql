-- ============================================================================
-- FitSync demo seed — 14 days of meal + workout + body + analytics data
-- ----------------------------------------------------------------------------
-- This is NOT a Flyway migration. It is a one-button RESET + seed you run
-- manually against the shared `fitsync` database (local docker-compose OR RDS).
--
-- !!! DESTRUCTIVE !!! For the account matched by the email below, it FIRST
-- DELETES ALL of that account's data (meals, workouts, body metrics, goals,
-- custom foods, plans, preferences, health readiness, analytics, ...), then
-- re-imports the 14-day demo. It KEEPS only the account itself
-- (auth.app_users) and the user_profiles main record. This is irreversible —
-- back up first if running against a database you care about.
--
-- Why not a Flyway db/migration file:
--   * Each service's Flyway only owns its own schema; this script writes across
--     auth / user_domain / nutrition / workout / analytics in one transaction.
--   * It is tied to a real account that only exists in your deployed DB, and
--     Flyway runs in CI / H2 / every environment.
--
-- HOW TO RUN  (must connect as a role that can see ALL schemas — the RDS master
-- user, or the local Postgres superuser; a per-service user like nutrition_svc
-- can only see its own schema and will fail):
--
--   Local docker-compose stack:
--     docker exec -i fitsync-postgres \
--       psql -U <master_user> -d fitsync < backend/scripts/seed-demo-fitsync.sql
--   (If Postgres is RDS-managed even locally, use the psql form below.)
--
--   RDS (or any reachable host):
--     psql "host=<rds-endpoint> port=5432 dbname=fitsync user=<master_user>" \
--       -f backend/scripts/seed-demo-fitsync.sql
--
-- The script resolves the user automatically from the email below, so it works
-- against whichever database actually holds the account. Re-running is safe:
-- it resets the account back to its initial (just-registered) state every time,
-- then re-imports the demo — so you always get the same clean dataset.
--
-- Recommended: run while the backend services are idle (or stopped) so no
-- in-flight Kafka/outbox event re-projects analytics rows after the reset.
--
-- To target a different account, change the single email literal in the
-- `demo_config` CTE below.
--
-- Demo window: a ROLLING 14-day window that always ends on "today" (the current
--              date in Asia/Singapore) so the Dashboard "Today"/streak panels are
--              never stale. d_start = today - 13, d_today = today. Re-run any time.
-- Timezone:    Asia/Singapore (all wall-clock times below are local to it).
-- ============================================================================

begin;

-- ----------------------------------------------------------------------------
-- 0. Resolve the target account ONCE. Everything downstream joins demo_config.
--    >>> EDIT THE EMAIL HERE to target a different account. <<<
-- ----------------------------------------------------------------------------
create temporary table demo_config on commit drop as
select
    u.id                       as user_id,
    'Asia/Singapore'::text                                        as tz,
    ((now() at time zone 'Asia/Singapore')::date - 13)            as d_start,
    (now() at time zone 'Asia/Singapore')::date                   as d_today
from auth.app_users u
where u.email = '2579706652@qq.com';

do $$
begin
    if not exists (select 1 from demo_config) then
        raise exception
            'No FitSync account found for the configured email. Edit the email in demo_config (top of seed-demo-fitsync.sql).';
    end if;
end $$;

-- ----------------------------------------------------------------------------
-- 1. RESET — delete ALL existing data for this account across every schema,
--    so the seed below lands on a clean slate. KEEPS only auth.app_users and
--    the user_profiles main record. Delete order respects FKs; rows with an
--    ON DELETE CASCADE parent (meal_items, set logs, plan exercises, training
--    preference equipment/focus, health-readiness injury areas, food servings/
--    barcodes/nutrient rows, ...) are removed automatically with their parent.
--
--    !!! This removes the user's REAL data, not just demo rows. Irreversible.
-- ----------------------------------------------------------------------------
do $$
declare
    v_user uuid;
begin
    select user_id into v_user from demo_config;
    raise notice 'RESET: deleting all existing data for user_id=% (keeping account + profile)', v_user;
end $$;

-- 1a. Nutrition
delete from nutrition.meal_logs                    where user_id      in (select user_id from demo_config);  -- -> meal_items -> nutrient details
delete from nutrition.water_logs                   where user_id      in (select user_id from demo_config);
delete from nutrition.meal_templates               where user_id      in (select user_id from demo_config);  -- -> meal_template_items
delete from nutrition.recipes                      where user_id      in (select user_id from demo_config);  -- -> recipe_ingredients
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

-- 1d. Analytics read-models + processed-event log for this user
delete from analytics.analytics_meal_contributions      where user_id in (select user_id from demo_config);
delete from analytics.analytics_workout_contributions   where user_id in (select user_id from demo_config);
delete from analytics.analytics_user_daily_nutrition    where user_id in (select user_id from demo_config);
delete from analytics.analytics_user_daily_workout      where user_id in (select user_id from demo_config);
delete from analytics.analytics_user_daily_body         where user_id in (select user_id from demo_config);
delete from analytics.analytics_user_goal_progress      where user_id in (select user_id from demo_config);
delete from analytics.analytics_user_dashboard_snapshot where user_id in (select user_id from demo_config);
delete from analytics.analytics_processed_events        where user_id in (select user_id from demo_config);

-- Note: per-service outbox_events (messaging infra) are intentionally NOT
-- touched. Run while services are idle so no stale event re-projects.

-- ----------------------------------------------------------------------------
-- 2. Make sure the profile has a height + timezone so BMI / dashboard render.
--    Only fills when currently NULL; never overwrites real data.
-- ----------------------------------------------------------------------------
update user_domain.user_profiles p
set height_cm = 170.00
from demo_config c
where p.user_id = c.user_id and p.height_cm is null;

update user_domain.user_profiles p
set timezone = c.tz
from demo_config c
where p.user_id = c.user_id and (p.timezone is null or p.timezone = '');

-- ============================================================================
-- 3. NUTRITION — meals + items, every day; an extra post-workout shake on
--    training days. meal_id / meal_item_id are deterministic (md5) so re-runs
--    and the contribution roll-ups line up.
-- ============================================================================

-- 3a. meal_logs
insert into nutrition.meal_logs
    (meal_id, user_id, meal_type, name, source, consumed_at, timezone, note,
     date_time_created, date_time_updated, created_by, updated_by, version)
with days as (
    select g::date as d, (g::date - c.d_start) as idx, c.user_id, c.tz
    from demo_config c, generate_series(c.d_start, c.d_today, interval '1 day') as g
),
workout_days(idx) as (values (0),(1),(3),(5),(8),(11),(12),(13)),
meals(code, meal_type, at_t, name, note, workout_only) as (values
    ('BFAST'::text,  'BREAKFAST'::text, time '07:30', 'Breakfast'::text,          'Morning fuel before the day.'::text,        false),
    ('LUNCH',        'LUNCH',           time '12:30', 'Lunch',                     'Balanced midday meal.',                     false),
    ('SNACK',        'SNACK',           time '16:00', 'Afternoon snack',           'Light pick-me-up.',                         false),
    ('DINNER',       'DINNER',          time '19:00', 'Dinner',                    'Main evening meal.',                        false),
    ('POSTWO',       'SNACK',           time '20:30', 'Post-workout shake',        'Recovery shake after training.',            true)
)
select
    md5(dy.user_id::text || dy.d::text || m.code)::uuid,
    dy.user_id,
    m.meal_type,
    m.name,
    'MANUAL',
    (dy.d + m.at_t) at time zone dy.tz,
    dy.tz,
    m.note,
    (dy.d + m.at_t) at time zone dy.tz,
    (dy.d + m.at_t) at time zone dy.tz,
    'demo-seed', 'demo-seed', 0
from days dy
cross join meals m
where m.workout_only = false
   or dy.idx in (select idx from workout_days);

-- 3b. meal_items
insert into nutrition.meal_items
    (meal_item_id, meal_id, custom_food_id, external_food_ref, food_name, brand_name,
     quantity, unit, serving_size, serving_unit, calories, protein_grams, carbs_grams,
     fat_grams, fiber_grams, sugar_grams, sodium_mg, date_time_created, date_time_updated,
     created_by, updated_by, version, food_catalog_item_id, food_serving_id, barcode,
     snapshot_serving_label)
with days as (
    select g::date as d, (g::date - c.d_start) as idx, c.user_id, c.tz
    from demo_config c, generate_series(c.d_start, c.d_today, interval '1 day') as g
),
workout_days(idx) as (values (0),(1),(3),(5),(8),(11),(12),(13)),
meals(code, at_t, workout_only) as (values
    ('BFAST'::text, time '07:30', false),
    ('LUNCH',       time '12:30', false),
    ('SNACK',       time '16:00', false),
    ('DINNER',      time '19:00', false),
    ('POSTWO',      time '20:30', true)
),
items(code, slot, food_name, brand_name, quantity, unit, serving_size, serving_unit,
      calories, protein, carbs, fat, fiber, sugar, sodium, snap_label) as (values
    -- breakfast
    ('BFAST'::text, 1, 'Rolled Oats'::text,           'Quaker'::text, 60.00, 'g'::text,     60.00, 'g'::text,     228, 8.00,  40.00, 4.00,  6.00, 1.00,  3.00,  '60 g'::text),
    ('BFAST', 2, 'Banana',                    null,           1.00,  'piece', 1.00,  'piece', 105, 1.30,  27.00, 0.40,  3.10, 14.40, 1.00,  '1 medium'),
    ('BFAST', 3, 'Greek Yogurt',              'FAGE',         170.00,'g',     170.00,'g',     120, 18.00, 6.00,  0.00,  0.00, 5.00,  65.00, '1 cup'),
    -- lunch
    ('LUNCH', 1, 'Grilled Chicken Breast',    null,           150.00,'g',     150.00,'g',     248, 46.00, 0.00,  5.40,  0.00, 0.00,  92.00, '150 g'),
    ('LUNCH', 2, 'Steamed White Rice',        null,           200.00,'g',     200.00,'g',     260, 5.00,  56.00, 0.60,  0.80, 0.10,  2.00,  '1 bowl'),
    ('LUNCH', 3, 'Stir-fried Vegetables',     null,           150.00,'g',     150.00,'g',     90,  3.00,  12.00, 3.50,  5.00, 6.00,  35.00, '150 g'),
    -- afternoon snack
    ('SNACK', 1, 'Almonds',                   null,           30.00, 'g',     30.00, 'g',     174, 6.00,  6.00,  15.00, 3.50, 1.00,  0.00,  '30 g'),
    ('SNACK', 2, 'Apple',                     null,           1.00,  'piece', 1.00,  'piece', 95,  0.50,  25.00, 0.30,  4.00, 19.00, 2.00,  '1 medium'),
    -- dinner
    ('DINNER',1, 'Baked Salmon',              null,           150.00,'g',     150.00,'g',     280, 39.00, 0.00,  13.00, 0.00, 0.00,  90.00, '150 g'),
    ('DINNER',2, 'Sweet Potato',              null,           200.00,'g',     200.00,'g',     180, 4.00,  41.00, 0.30,  6.00, 13.00, 72.00, '200 g'),
    ('DINNER',3, 'Garden Salad',              null,           120.00,'g',     120.00,'g',     70,  2.00,  8.00,  4.00,  3.00, 4.00,  25.00, '120 g'),
    -- post-workout shake (training days only)
    ('POSTWO',1, 'Whey Protein Shake',        null,           35.00, 'g',     35.00, 'g',     130, 25.00, 4.00,  1.50,  1.00, 2.00,  80.00, '1 scoop'),
    ('POSTWO',2, 'Mixed Berries',             null,           100.00,'g',     100.00,'g',     57,  0.70,  14.00, 0.30,  2.40, 10.00, 1.00,  '100 g')
)
select
    md5(dy.user_id::text || dy.d::text || it.code || it.slot::text)::uuid,
    md5(dy.user_id::text || dy.d::text || it.code)::uuid,
    null, null, it.food_name, it.brand_name,
    it.quantity, it.unit, it.serving_size, it.serving_unit, it.calories,
    it.protein, it.carbs, it.fat, it.fiber, it.sugar, it.sodium,
    (dy.d + m.at_t) at time zone dy.tz,
    (dy.d + m.at_t) at time zone dy.tz,
    'demo-seed', 'demo-seed', 0, null, null, null, it.snap_label
from days dy
join items it on true
join meals m on m.code = it.code
where m.workout_only = false
   or dy.idx in (select idx from workout_days);

-- 3c. water_logs — four sips per day, ~2.3 L total.
insert into nutrition.water_logs
    (water_id, user_id, amount_ml, logged_at, timezone,
     date_time_created, date_time_updated, created_by, updated_by, version)
with days as (
    select g::date as d, c.user_id, c.tz
    from demo_config c, generate_series(c.d_start, c.d_today, interval '1 day') as g
),
sips(slot, at_t, amount_ml) as (values
    (1, time '08:00', 500),
    (2, time '12:30', 750),
    (3, time '17:00', 600),
    (4, time '21:00', 450)
)
select
    md5(dy.user_id::text || dy.d::text || 'water' || s.slot::text)::uuid,
    dy.user_id, s.amount_ml,
    (dy.d + s.at_t) at time zone dy.tz, dy.tz,
    (dy.d + s.at_t) at time zone dy.tz,
    (dy.d + s.at_t) at time zone dy.tz,
    'demo-seed', 'demo-seed', 0
from days dy cross join sips s;

-- ============================================================================
-- 4. USER profile data — body metrics (downward trend) + goals.
-- ============================================================================

-- 4a. body_metric_entries (weigh-ins ~07:00 local).
insert into user_domain.body_metric_entries
    (metric_id, user_id, weight_kg, body_fat_rate, waist_cm, hip_cm, chest_cm,
     arm_cm, thigh_cm, calf_cm, measured_at, source, note,
     date_time_created, date_time_updated, created_by, updated_by, version)
with m(idx, weight, bf, waist, note) as (values
    (0,  72.50, 22.50, 82.0, 'Baseline weigh-in at start of the block.'),
    (3,  72.20, 22.30, 81.6, 'Steady start, hydration improving.'),
    (6,  71.90, 22.00, 81.2, 'End of week one — on track.'),
    (10, 71.50, 21.70, 80.7, 'Mid week two, energy good.'),
    (13, 71.10, 21.40, 80.3, 'Latest check-in, trend holding.')
)
select
    md5(c.user_id::text || 'body' || m.idx::text)::uuid,
    c.user_id, m.weight, m.bf, m.waist, null, null, null, null, null,
    ((c.d_start + m.idx) + time '07:00') at time zone c.tz,
    'MANUAL', m.note,
    ((c.d_start + m.idx) + time '07:05') at time zone c.tz,
    ((c.d_start + m.idx) + time '07:05') at time zone c.tz,
    'demo-seed', 'demo-seed', 0
from demo_config c cross join m;

-- 4b. user_goals (deterministic ids; feed the analytics targets below).
insert into user_domain.user_goals
    (goal_id, user_id, goal_type, target_value, target_unit, target_date, status, notes,
     date_time_created, date_time_updated, created_by, updated_by, version)
with g(goal_type, target_value, target_unit, notes) as (values
    ('WEIGHT'::text,            68.00,   'kg'::text,            'Reach a lean target weight while keeping strength.'::text),
    ('CALORIES',                2100.00, 'kcal',                'Maintain a slight daily calorie deficit.'),
    ('PROTEIN',                 140.00,  'g',                   'Hit daily protein for recomposition.'),
    ('WORKOUT_FREQUENCY',       4.00,    'sessions/week',       'Complete four structured sessions every week.')
)
select
    md5(c.user_id::text || 'GOAL' || g.goal_type)::uuid,
    c.user_id, g.goal_type, g.target_value, g.target_unit, date '2026-08-31', 'ACTIVE', g.notes,
    (c.d_start + time '08:00') at time zone c.tz,
    (c.d_today + time '08:00') at time zone c.tz,
    'demo-seed', 'demo-seed', 0
from demo_config c cross join g;

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

-- ============================================================================
-- 5. WORKOUT — exercises, a plan, and 8 completed sessions (≈4/week) with
--    alternating strength / cardio and light week-over-week progression.
-- ============================================================================
do $$
declare
    v_user   uuid;
    v_tz     text;
    v_start  date;
    v_plan   uuid := gen_random_uuid();
    ex_bench uuid;
    ex_squat uuid;
    ex_row   uuid;
    ex_plank uuid;
    ex_run   uuid := gen_random_uuid();

    wdays  int[]  := array[0,1,3,5,8,11,12,13];
    kinds  text[] := array['STRENGTH','CARDIO','STRENGTH','CARDIO','STRENGTH','CARDIO','STRENGTH','CARDIO'];

    i        int;
    idx      int;
    kind     text;
    wk       int;
    d        date;
    started  timestamptz;
    v_session uuid;
    v_log    uuid;

    bench_w numeric; squat_w numeric; row_w numeric;
    run_km  numeric;
begin
    select user_id, tz, d_start into v_user, v_tz, v_start from demo_config;

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

    -- 5b. workout plan (the strength template the strength sessions follow)
    insert into workout.workout_plans
        (plan_id, user_id, name, description, goal_focus, workout_type, difficulty,
         estimated_duration_minutes, status, date_time_created, date_time_updated,
         created_by, updated_by, version)
    values
        (v_plan, v_user, 'Recomp Strength + Cardio',
         'Four sessions a week alternating full-body strength and steady cardio.',
         'GENERAL_FITNESS', 'STRENGTH', 'BEGINNER', 50, 'ACTIVE',
         (v_start + time '08:00') at time zone v_tz, (v_start + time '08:00') at time zone v_tz,
         'demo-seed', 'demo-seed', 0);

    -- 5c. plan exercises + per-set targets (3 working sets each)
    insert into workout.workout_plan_exercises
        (plan_exercise_id, plan_id, exercise_id, exercise_name_snapshot, sequence_no,
         target_sets, target_reps, target_weight_kg, target_duration_seconds,
         target_distance_km, rest_seconds, notes)
    values
        (gen_random_uuid(), v_plan, ex_bench, 'Dumbbell Bench Press', 1, 3, 10, 20.00, null, null, 75, 'Controlled tempo, full range.'),
        (gen_random_uuid(), v_plan, ex_squat, 'Goblet Squat',         2, 3, 12, 16.00, null, null, 75, 'Knees tracking over toes.'),
        (gen_random_uuid(), v_plan, ex_row,   'Bent-Over Row',        3, 3, 10, 18.00, null, null, 75, 'Squeeze the shoulder blades.'),
        (gen_random_uuid(), v_plan, ex_plank, 'Plank Hold',           4, 3, null, null, 45, null, 45, 'Brace and keep hips level.');

    insert into workout.workout_plan_exercise_set_targets
        (set_target_id, plan_exercise_id, set_number, target_reps, target_weight_kg,
         target_duration_seconds, target_distance_km, rest_seconds)
    select gen_random_uuid(), pe.plan_exercise_id, s.set_number,
           pe.target_reps, pe.target_weight_kg, pe.target_duration_seconds, pe.target_distance_km, pe.rest_seconds
    from workout.workout_plan_exercises pe
    cross join (values (1),(2),(3)) as s(set_number)
    where pe.plan_id = v_plan;

    -- 5d. one completed session per training day
    for i in 1 .. array_length(wdays, 1) loop
        idx       := wdays[i];
        kind      := kinds[i];
        d         := v_start + idx;
        wk        := case when idx < 7 then 0 else 1 end;   -- week 1 vs week 2
        started   := (d + time '18:30') at time zone v_tz;
        v_session := gen_random_uuid();

        if kind = 'STRENGTH' then
            bench_w := 20.00 + wk * 2.50;
            squat_w := 16.00 + wk * 2.00;
            row_w   := 18.00 + wk * 2.00;

            insert into workout.workout_sessions
                (session_id, user_id, plan_id, name, workout_type, status, source,
                 started_at, completed_at, duration_minutes, calories_burned,
                 perceived_exertion, notes, date_time_created, date_time_updated,
                 created_by, updated_by, version)
            values
                (v_session, v_user, v_plan, 'Strength — Full Body', 'STRENGTH', 'COMPLETED', 'PLAN',
                 started, started + interval '50 min', 50, 360, 7,
                 'Hit all working sets; progression felt manageable.',
                 started, started + interval '50 min', 'demo-seed', 'demo-seed', 0);

            -- bench
            v_log := gen_random_uuid();
            insert into workout.workout_exercise_logs
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes)
            values (v_log, v_session, ex_bench, 'Dumbbell Bench Press', 1, 3, 30, 3 * 10 * bench_w, null, 360, 128, 145, null);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged)
            select gen_random_uuid(), v_log, s.n, 10, bench_w, null, null, 75, 6 + s.n - 1, null, true
            from (values (1),(2),(3)) as s(n);

            -- squat
            v_log := gen_random_uuid();
            insert into workout.workout_exercise_logs
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes)
            values (v_log, v_session, ex_squat, 'Goblet Squat', 2, 3, 36, 3 * 12 * squat_w, null, 420, 132, 150, null);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged)
            select gen_random_uuid(), v_log, s.n, 12, squat_w, null, null, 75, 6 + s.n - 1, null, true
            from (values (1),(2),(3)) as s(n);

            -- row
            v_log := gen_random_uuid();
            insert into workout.workout_exercise_logs
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes)
            values (v_log, v_session, ex_row, 'Bent-Over Row', 3, 3, 30, 3 * 10 * row_w, null, 360, 130, 147, null);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged)
            select gen_random_uuid(), v_log, s.n, 10, row_w, null, null, 75, 6 + s.n - 1, null, true
            from (values (1),(2),(3)) as s(n);

            -- plank
            v_log := gen_random_uuid();
            insert into workout.workout_exercise_logs
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes)
            values (v_log, v_session, ex_plank, 'Plank Hold', 4, 3, null, null, null, 135, 118, 128, 'Held all prescribed durations.');
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged)
            select gen_random_uuid(), v_log, s.n, null, null, 45, null, 45, 6 + s.n - 1, null, true
            from (values (1),(2),(3)) as s(n);

        else  -- CARDIO
            run_km := 5.00 + wk * 0.50;

            insert into workout.workout_sessions
                (session_id, user_id, plan_id, name, workout_type, status, source,
                 started_at, completed_at, duration_minutes, calories_burned,
                 perceived_exertion, notes, date_time_created, date_time_updated,
                 created_by, updated_by, version)
            values
                (v_session, v_user, null, 'Cardio — Treadmill', 'CARDIO', 'COMPLETED', 'MANUAL',
                 started, started + interval '35 min', 35, 380, 6,
                 'Steady-state run, conversational pace.',
                 started, started + interval '35 min', 'demo-seed', 'demo-seed', 0);

            -- treadmill run
            v_log := gen_random_uuid();
            insert into workout.workout_exercise_logs
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes)
            values (v_log, v_session, ex_run, 'Treadmill Run', 1, 1, null, null, run_km, 1800, 150, 168, null);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged)
            values (gen_random_uuid(), v_log, 1, null, null, 1800, run_km, 0, 6, null, true);

            -- plank finisher
            v_log := gen_random_uuid();
            insert into workout.workout_exercise_logs
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes)
            values (v_log, v_session, ex_plank, 'Plank Hold', 2, 3, null, null, null, 135, 120, 130, null);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged)
            select gen_random_uuid(), v_log, s.n, null, null, 45, null, 45, 6 + s.n - 1, null, true
            from (values (1),(2),(3)) as s(n);
        end if;
    end loop;
end $$;

-- ============================================================================
-- 6. ANALYTICS read-models — computed by aggregating the rows seeded above so
--    they stay self-consistent (the real service builds these from Kafka
--    events, which direct SQL inserts bypass).
-- ============================================================================

-- 6a. per-meal contributions
insert into analytics.analytics_meal_contributions
    (meal_id, user_id, activity_date, total_calories, total_protein_grams,
     total_carbs_grams, total_fat_grams, item_count, last_applied_at,
     date_time_created, date_time_updated, created_by, updated_by, version)
select
    ml.meal_id, ml.user_id, (ml.consumed_at at time zone c.tz)::date,
    sum(mi.calories)::int, sum(mi.protein_grams), sum(mi.carbs_grams), sum(mi.fat_grams),
    count(mi.meal_item_id)::int, now(), now(), now(), 'demo-seed', 'demo-seed', 0
from demo_config c
join nutrition.meal_logs ml on ml.user_id = c.user_id and ml.created_by = 'demo-seed'
join nutrition.meal_items mi on mi.meal_id = ml.meal_id
group by ml.meal_id, ml.user_id, (ml.consumed_at at time zone c.tz)::date;

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
           sum(mi.calories)                         as cals,
           sum(mi.protein_grams)                    as protein,
           sum(mi.carbs_grams)                      as carbs,
           sum(mi.fat_grams)                        as fat
    from demo_config c
    join nutrition.meal_logs ml on ml.user_id = c.user_id and ml.created_by = 'demo-seed'
    join nutrition.meal_items mi on mi.meal_id = ml.meal_id
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
    (select count(*) from workout.workout_exercise_logs el where el.session_id = ws.session_id)::int,
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
           (select count(*) from workout.workout_exercise_logs el where el.session_id = ws.session_id) as exc
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
     today_workout_count, week_workout_count, week_workout_minutes, active_streak_days,
     today_calories_consumed, today_calories_target, today_protein_grams,
     today_protein_target_grams, generated_at, date_time_created, date_time_updated,
     created_by, updated_by, version)
select
    c.user_id, c.tz,
    bd.weight_kg, bd.body_fat_rate, bd.bmi, bd.weight_delta_from_start,
    coalesce((select completed_workout_count from analytics.analytics_user_daily_workout w
              where w.user_id = c.user_id and w.activity_date = c.d_today), 0),
    coalesce((select sum(completed_workout_count) from analytics.analytics_user_daily_workout w
              where w.user_id = c.user_id and w.activity_date between c.d_today - 6 and c.d_today), 0)::int,
    coalesce((select sum(total_workout_minutes) from analytics.analytics_user_daily_workout w
              where w.user_id = c.user_id and w.activity_date between c.d_today - 6 and c.d_today), 0)::int,
    3,
    (select total_calories_consumed from analytics.analytics_user_daily_nutrition n
     where n.user_id = c.user_id and n.activity_date = c.d_today),
    2100,
    (select total_protein_grams from analytics.analytics_user_daily_nutrition n
     where n.user_id = c.user_id and n.activity_date = c.d_today),
    140.00,
    now(), now(), now(), 'demo-seed', 'demo-seed', 0
from demo_config c
join lateral (
    select weight_kg, body_fat_rate, bmi, weight_delta_from_start
    from analytics.analytics_user_daily_body bd
    where bd.user_id = c.user_id
    order by bd.metric_date desc
    limit 1
) bd on true;

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
