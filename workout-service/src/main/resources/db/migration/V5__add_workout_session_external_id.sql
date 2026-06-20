alter table workout_sessions
    add column external_id varchar(120);

-- Idempotency key for DEVICE / IMPORTED sessions.
-- PostgreSQL and H2 both allow multiple NULL values in a unique index, so existing
-- MANUAL / PLAN rows with external_id = NULL do not conflict. H2 2.x does not
-- support PostgreSQL's partial-index WHERE syntax; this full unique index gives
-- the same effective behavior for non-null external_id values in both databases.
create unique index uk_workout_sessions_user_source_external_id
    on workout_sessions (user_id, source, external_id);
