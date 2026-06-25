-- ============================================================================
-- FitSync — remove orphan goal-progress rows from the analytics read model
-- ----------------------------------------------------------------------------
-- The /analytics/goals/progress read model (analytics.analytics_user_goal_progress)
-- is keyed by goal_id and is append-only: nothing ever deleted from it until the
-- soft-delete propagation (GoalUpdated status=ARCHIVED -> consumer deleteById).
-- The table can also be populated directly by seed-analytics-mock.sql (id scheme
-- md5(user||':weight')) and by earlier onboarding/seed runs whose goal ids were
-- later superseded. The result is several stale rows per goal type (e.g. 3x
-- WEIGHT) that surface as duplicates on the /progress page.
--
-- The source of truth is user_domain.user_goals, which has a unique
-- (user_id, goal_type) constraint and therefore exactly one row per type. This
-- script deletes every read-model row whose goal_id is NOT a currently ACTIVE
-- user goal, leaving only the canonical goals.
--
-- Safe to re-run (idempotent).
--
-- TARGETING THE ACCOUNT (pick one):
--   * Preferred — pass the user_id directly (works everywhere, incl. Cognito-backed
--     prod where auth.app_users may not hold the account by email):
--       psql ... -v target_user_id=36ebbe2f-e46d-4d28-867b-904f6baee4c7 \
--         -f backend/scripts/cleanup-orphan-goal-progress.sql
--   * Fallback — omit target_user_id and it resolves by demo email from
--     auth.app_users (only works where that table holds the account).
--
-- Connect as the RDS master / a role that can see auth, user_domain and analytics
-- schemas. Widen demo_config (or drop the user_id scope) to clean all users.
-- ============================================================================

\if :{?target_user_id}
\else
  \set target_user_id ''
\endif
\set demo_email 'wang2579706652@gmail.com'

begin;

create temporary table demo_config on commit drop as
select coalesce(
    nullif(:'target_user_id', '')::uuid,
    (select u.id from auth.app_users u where u.email = :'demo_email')
) as user_id;

do $$
begin
    if not exists (select 1 from demo_config where user_id is not null) then
        raise exception 'Could not resolve a user. Pass -v target_user_id=<uuid> or ensure the demo email exists in auth.app_users.';
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

-- Verify: should now be one row per goal type for the targeted account.
select p.goal_type, count(1) as type_rows
from analytics.analytics_user_goal_progress p
join demo_config c on c.user_id = p.user_id
group by p.goal_type
order by p.goal_type;

commit;

-- NOTE: /analytics/goals/progress is @Cacheable (analyticsGoalProgress, ~5 min TTL
-- when Redis-backed). After a direct DB cleanup the endpoint may serve a stale
-- cached list until the entry expires, the analytics-service restarts, or any
-- GoalUpdated event for the user fires evictAllForUser (events must be ON).
