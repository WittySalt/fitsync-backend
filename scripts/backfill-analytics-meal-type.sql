-- ============================================================================
-- Backfill of analytics_meal_contributions.meal_type for meals projected before
-- analytics migration V10 added the column.
-- ----------------------------------------------------------------------------
-- WHEN TO RUN
--   Once after analytics V10 is applied, on a database that already holds meals.
--   Until then those rows have a null meal_type and the weekly energy view counts
--   them all as "other". Run as the database owner (a role that sees both the
--   analytics and the nutrition schema), locally and on RDS alike:
--     psql -h <host> -U <owner> -d <db> -f scripts/backfill-analytics-meal-type.sql
--
-- WHAT IT DOES
--   Copies meal_logs.meal_type (upper case, as the event carries it) into the
--   contribution row with the same meal_id.
--
-- IDEMPOTENT
--   Only rows whose meal_type is still null are touched, so a second run changes
--   nothing. Meals that no longer exist in the nutrition schema stay null.
-- ============================================================================

update analytics.analytics_meal_contributions c
   set meal_type = upper(left(ml.meal_type, 20))
  from nutrition.meal_logs ml
 where ml.meal_id = c.meal_id
   and c.meal_type is null
   and ml.meal_type is not null;
