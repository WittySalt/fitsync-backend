alter table workout_exercise_set_logs
    add column logged boolean not null default false;

-- Existing set logs predate the seeded-template distinction and represent real
-- performed data, so backfill them to logged = true. Only future WO-1 plan-seeded
-- sets (written after this migration) keep the default false.
update workout_exercise_set_logs set logged = true;
