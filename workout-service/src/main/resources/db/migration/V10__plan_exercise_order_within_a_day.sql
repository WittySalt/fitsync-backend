-- The order of a plan's exercises was unique across the whole plan. A plan with a week orders its
-- exercises within each day, so Monday and Thursday both have a first exercise. The index now
-- covers the day as well. A plan without a week has no day, and an index treats a missing day as
-- different every time, so for those plans the service checks the order before it writes.
drop index uk_workout_plan_exercises_plan_sequence;

create unique index uk_workout_plan_exercises_plan_day_sequence
    on workout_plan_exercises (plan_id, plan_day_id, sequence_no);
