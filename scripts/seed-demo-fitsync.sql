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
--   steady    Someone for whom things are going well: four sessions a week,
--             progressive overload, a calorie/protein window inside target,
--             and a steady downward weight trend.
--   drifting  Someone who started well and is slipping: sessions and weights
--             fall away over the most recent weeks, logging gets patchy, and
--             the WEIGHT goal's target date has already passed.
--   fresh     Someone who has just finished onboarding: a profile and
--             preferences, one goal, and otherwise empty history, so the
--             empty states can be checked.
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
    ((now() at time zone 'Asia/Singapore')::date - 59)             as d_start,
    (now() at time zone 'Asia/Singapore')::date                    as d_today,
    lower(:'persona')                                              as persona
from auth.app_users u
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
do $$
begin
    if exists (select 1 from information_schema.tables where table_schema = 'analytics' and table_name = 'analytics_user_workout_records') then
        delete from analytics.analytics_user_workout_records where user_id in (select user_id from demo_config);
    end if;
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

-- STEADY: Monday/Friday strength, a Thursday run and Saturday badminton, every
-- week, which is the week the plan lays out; one Thursday (week 0) and one
-- Saturday (week 1) dropped inside the first three weeks so the record is not
-- a wall of perfect weeks. Weight rises every two
-- weeks going forward in time (equivalently: falls every two weeks the
-- further back from "now" you look), floored at 10 kg.
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

update demo_days
set bench_w = greatest(10, round(22.50 - 2.50 * tier, 2)),
    squat_w = greatest(10, round(18.00 - 2.00 * tier, 2)),
    row_w   = greatest(10, round(20.00 - 2.00 * tier, 2)),
    run_km  = greatest(4.00, round(5.75 - 0.25 * tier, 2))
where persona = 'steady';

update demo_days
set meal_day  = true,
    meal_mult = case idx % 3 when 0 then 1.03 when 1 then 1.10 else 1.17 end,
    shake_day = is_training_day and not skip_session
where persona = 'steady';

-- DRIFTING: same shape as steady for the oldest weeks (m >= 4, "the first
-- five weeks"), drops to 3 sessions/week (no Saturday) for m in (2,3), then
-- to 2 sessions/week (Monday + Thursday only, Monday shortened to 40 min) for
-- m <= 1 — the most recent three calendar weeks (the still-forming current
-- week and the two completed weeks before it). Lift weight rises through the
-- oldest weeks, peaks four weeks before the last completed one, and then
-- falls, so that each of the recent weeks moves no more weight than the week
-- before it and the last completed week moves less.
update demo_days
set is_training_day = case
        when m >= 4      then dow in (1, 4, 5, 6)
        when m in (2, 3) then dow in (1, 4, 5)
        else                  dow in (1, 4)
    end,
    workout_kind = case dow when 1 then 'STRENGTH' when 5 then 'STRENGTH'
                             when 4 then 'CARDIO'   when 6 then 'SPORT' end,
    duration_minutes = case
        when dow = 1 and m <= 1 then 40
        when dow in (1, 5)      then 50
        when dow = 4            then 35
        when dow = 6            then 60
    end,
    skip_session = false
where persona = 'drifting';

alter table demo_days add column fall int;
update demo_days
set fall = case
        when m >= 4      then ceil((m - 4) / 2.0)::int
        when m = 3       then 0
        when m in (1, 2) then 1
        else                  2
    end
where persona = 'drifting';

update demo_days
set bench_w = greatest(10, round(20.00 - 2.50 * fall, 2)),
    squat_w = greatest(10, round(16.00 - 2.00 * fall, 2)),
    row_w   = greatest(10, round(18.00 - 2.00 * fall, 2)),
    run_km  = greatest(4.00, round(5.25 - 0.25 * fall, 2))
where persona = 'drifting';

-- Logged every day for the first five weeks (absolute week 0-4 from d_start),
-- about four days a week after that (Mon/Tue/Thu/Sat). Calories climb to the
-- 2,300-2,500 range on logged days in the last three weeks (absolute week
-- 6-8) as the deficit slips.
update demo_days
set meal_day  = case when w <= 4 then true else dow in (1, 2, 4, 6) end,
    meal_mult = case
        when w >= 6 then 1.25 + (idx % 3) * 0.03
        else case idx % 3 when 0 then 1.03 when 1 then 1.10 else 1.17 end
    end,
    shake_day = is_training_day and not skip_session
where persona = 'drifting';

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
  and dd.d < (select d_today from demo_config)
  and dd.meal_day
  and (m.workout_only = false or dd.shake_day);

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
       and dd.d < (select d_today from demo_config)
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

-- 3c. water_logs — four sips per day, ~2.3 L total, for the whole window.
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
where dd.persona <> 'fresh';

-- ============================================================================
-- 4. USER profile data — body metrics + goals.
-- ============================================================================

-- 4a. body_metric_entries — Monday/Thursday mornings, skipped entirely for
--     "fresh". steady falls from 74.20 kg to 71.10 kg at a pace that picks up
--     slightly, about 0.4 kg a week over the last four weeks, so that the rate
--     of the last 28 days reaches the 68 kg goal before its date. drifting
--     falls from 74.20 kg to ~72.00 kg by day 35 (week 5) and climbs back to
--     ~72.90 kg by d_today. Small deterministic noise keeps the line from
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
                when dd.idx <= 35 then round(74.20 - 2.20 * (dd.idx::numeric / 35.0), 2)
                else                   round(72.00 + 0.90 * ((dd.idx - 35)::numeric / 24.0), 2)
            end
        end as base_w,
        round((((dd.idx * 37) % 7) - 3) * 0.03, 2) as noise
    from demo_days dd
    where dd.dow in (1, 4) and dd.persona in ('steady', 'drifting')
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
--     WEIGHT target date is 8 weeks after today; drifting's is 10 days
--     before today, so it reads as overdue.
insert into user_domain.user_goals
    (goal_id, user_id, goal_type, target_value, target_unit, target_date, status, notes,
     date_time_created, date_time_updated, created_by, updated_by, version)
select
    md5(c.user_id::text || 'GOAL' || g.goal_type)::uuid,
    c.user_id, g.goal_type, g.target_value::numeric, g.target_unit,
    case
        -- Ten weeks ahead, which the steady persona's rate of loss reaches, so that the pace rule and
        -- the goal progress agree that the goal is on track.
        when g.goal_type = 'WEIGHT' and c.persona = 'steady'   then (c.d_today + 70)::date
        when g.goal_type = 'WEIGHT' and c.persona = 'drifting' then (c.d_today - 10)::date
        else (c.d_today + 60)::date
    end,
    'ACTIVE', g.notes,
    (c.d_start + time '08:00') at time zone c.tz,
    (c.d_today + time '08:00') at time zone c.tz,
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

    -- 5d. one completed session per scheduled, non-skipped training day.
    for r in
        select * from demo_days
        where user_id = v_user and is_training_day and not skip_session and d < v_today
        order by d
    loop
        started   := (r.d + time '18:30') at time zone v_tz;
        v_session := gen_random_uuid();
        -- A moved session keeps the plan day it was meant for.
        v_plan_day := case coalesce(r.plan_dow, r.dow)
            when 1 then v_day_mon when 4 then v_day_thu when 5 then v_day_fri when 6 then v_day_sat end;

        if r.workout_kind = 'STRENGTH' then
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
                 average_heart_rate, max_heart_rate, notes)
            values (v_log, v_session, ex_bench, 'Dumbbell Bench Press', 1, 1, 3, 30, 3 * 10 * r.bench_w, null, 360, 128, 145, null);
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
                 average_heart_rate, max_heart_rate, notes)
            values (v_log, v_session, ex_squat, 'Goblet Squat', 2, 2, 3, 36, 3 * 12 * r.squat_w, null, 420, 132, 150, null);
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
                 average_heart_rate, max_heart_rate, notes)
            values (v_log, v_session, ex_row, 'Bent-Over Row', 3, 3, 3, 30, 3 * 10 * r.row_w, null, 360, 130, 147, null);
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
                 average_heart_rate, max_heart_rate, notes)
            values (v_log, v_session, ex_plank, 'Plank Hold', 4, 4, 3, null, null, null, 135, 118, 128, 'Held all prescribed durations.');
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
                 average_heart_rate, max_heart_rate, notes)
            values (v_log, v_session, ex_run, 'Treadmill Run', 1, 1, 1, null, null, r.run_km, 1800, 150, 168, null);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged, status)
            values (gen_random_uuid(), v_log, 1, null, null, 1800, r.run_km, 0, 6, null, true, 'DONE');

            -- plank finisher
            v_log := gen_random_uuid();
            insert into workout.workout_session_entries
                (exercise_log_id, session_id, exercise_id, exercise_name_snapshot, sequence_no, position,
                 performed_sets, performed_reps, total_volume_kg, distance_km, duration_seconds,
                 average_heart_rate, max_heart_rate, notes)
            values (v_log, v_session, ex_plank, 'Plank Hold', 2, 2, 3, null, null, null, 135, 120, 130, null);
            insert into workout.workout_exercise_set_logs
                (set_log_id, exercise_log_id, set_number, reps, weight_kg, duration_seconds,
                 distance_km, rest_seconds, perceived_exertion, notes, logged, status)
            select gen_random_uuid(), v_log, s.n, null, null, 45, null, 45, 6 + s.n - 1, null, true, 'DONE'
            from (values (1),(2),(3)) as s(n);
        end if;
    end loop;
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
    sum(mi.calories)::int, sum(mi.protein_grams), sum(mi.carbs_grams), sum(mi.fat_grams),
    count(mi.meal_item_id)::int, now(), now(), now(), 'demo-seed', 'demo-seed', 0, ml.meal_type
from demo_config c
join nutrition.meal_logs ml on ml.user_id = c.user_id and ml.created_by = 'demo-seed'
join nutrition.meal_items mi on mi.meal_id = ml.meal_id
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
    bd.weight_kg, bd.body_fat_rate, bd.bmi, bd.weight_delta_from_start,
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
-- 8b. Fields the Phase 2 work added after this seed was written.
--
--     The script predates three things and so left them empty, which meant the features built on
--     them had nothing to show: the rules engine could not judge weekly adherence without a target,
--     and the food-group breakdown, the payoff of the whole open-data thread, was blank.
--
--     Applied for every persona, "fresh" included — the product owner wants a fresh account's
--     profile and preferences filled in exactly like the others, just with no activity yet.
-- ----------------------------------------------------------------------------

-- Training preferences. R2 compares active days against weekly_workout_days, so without this the
-- weekly-adherence rule cannot fire at all. Five days against the four the window actually
-- contains, so the rule has something true to say.
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
       5, 45, time '19:00',
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
        c.d_today,
        wg.goal_type, wg.target_value, wg.target_unit, wg.target_date,
        -- Pace baseline: earliest weight inside the demo window, so R3 has both ends of the line.
        (select bd.weight_kg from analytics.analytics_user_daily_body bd
          where bd.user_id = c.user_id and bd.weight_kg is not null
          order by bd.metric_date asc limit 1),
        (select bd.metric_date from analytics.analytics_user_daily_body bd
          where bd.user_id = c.user_id and bd.weight_kg is not null
          order by bd.metric_date asc limit 1),
        (select n.calorie_target::int from analytics.analytics_user_daily_nutrition n
          where n.user_id = c.user_id and n.activity_date = c.d_today),
        (select n.protein_target_grams from analytics.analytics_user_daily_nutrition n
          where n.user_id = c.user_id and n.activity_date = c.d_today),
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
        select (wk.week_start - 7)                                             as week_start,
               coalesce(sum(w.completed_workout_count), 0)::int                as workout_count,
               coalesce(sum(w.total_workout_minutes), 0)::int                  as workout_minutes,
               count(*) filter (where w.completed_workout_count > 0)::int      as active_days
          from analytics.analytics_user_daily_workout w
         where w.user_id = c.user_id
           and w.activity_date >= wk.week_start - 7
           and w.activity_date < wk.week_start
        having c.persona <> 'fresh'
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
