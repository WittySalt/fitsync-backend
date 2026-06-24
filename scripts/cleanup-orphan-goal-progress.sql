-- ============================================================================
-- FitSync — remove orphan goal-progress rows from the analytics read model
-- ----------------------------------------------------------------------------
-- The /analytics/goals/progress read model (analytics.analytics_user_goal_progress)
-- is keyed by goal_id and is append-only: nothing ever deleted from it until the
-- soft-delete propagation (GoalUpdated status=ARCHIVED -> consumer deleteById).
-- In prod the event pipeline runs OFF, and the table was populated directly by
-- seed-analytics-mock.sql (id scheme md5(user||':weight')) and by earlier
-- onboarding/seed runs whose goal ids were later superseded. The result is
-- several stale rows per goal type (e.g. 3x WEIGHT) that surface as duplicates
-- on the /progress page.
--
-- The source of truth is user_domain.user_goals, which has a unique
-- (user_id, goal_type) constraint and therefore exactly one row per type.
-- This script deletes every read-model row whose goal_id is NOT a currently
-- ACTIVE user goal, leaving only the canonical goals.
--
-- Safe to re-run (idempotent). Scoped to the demo account by email; widen the
-- WHERE on demo_config (or drop the user_id filter) to clean all users.
--
-- HOW TO RUN (connect as the RDS master / a role that can see auth, user_domain
-- and analytics schemas):
--   psql "host=<rds-endpoint> port=5432 dbname=fitsync user=<master_user>" \
--     -f backend/scripts/cleanup-orphan-goal-progress.sql
-- ============================================================================

begin;

create temporary table demo_config on commit drop as
select u.id as user_id
from auth.app_users u
where u.email = 'wang2579706652@gmail.com';

do $$
begin
    if not exists (select 1 from demo_config) then
        raise exception 'No FitSync account for the configured email. Edit demo_config at the top of cleanup-orphan-goal-progress.sql.';
    end if;
end $$;

-- Show what will be removed (orphans = read-model rows with no matching ACTIVE goal).
select p.goal_id, p.goal_type, p.target_value, p.target_unit, p.target_date, p.goal_status
from analytics.analytics_user_goal_progress p
join demo_config c on c.user_id = p.user_id
where not exists (
    select 1
    from user_domain.user_goals g
    where g.goal_id = p.goal_id
      and g.status = 'ACTIVE'
)
order by p.goal_type, p.target_date;

-- Delete the orphans.
delete from analytics.analytics_user_goal_progress p
using demo_config c
where p.user_id = c.user_id
  and not exists (
      select 1
      from user_domain.user_goals g
      where g.goal_id = p.goal_id
        and g.status = 'ACTIVE'
  );

-- Verify: should now be one row per goal type for the demo account.
select p.goal_type, count(*) as rows
from analytics.analytics_user_goal_progress p
join demo_config c on c.user_id = p.user_id
group by p.goal_type
order by p.goal_type;

commit;
