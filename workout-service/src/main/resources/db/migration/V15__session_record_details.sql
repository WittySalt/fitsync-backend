-- What a finished session records about itself, for the session page in History.

-- When the session ended, completed or cancelled. A cancelled session has no completion time, so
-- without this it had no duration either. Completed sessions take it from their completion time.
alter table workout_sessions
    add column ended_at timestamp with time zone;

update workout_sessions
set ended_at = completed_at
where completed_at is not null;

-- Calories and effort can be estimated by the server when the person gave none. The flags say so,
-- so a value the person entered is never shown as an estimate, and the reverse.
alter table workout_sessions
    add column calories_estimated boolean not null default false;

alter table workout_sessions
    add column effort_estimated boolean not null default false;

-- A deleted session is kept and marked. Lists, totals and insights leave it out; the final purge
-- of an account removes it with everything else.
alter table workout_sessions
    add column deleted_at timestamp with time zone;

-- The rest the person actually took after a set, which can differ from the planned rest when it was
-- skipped or lengthened. Empty for sets logged without the exercise screen and for the last set.
alter table workout_exercise_set_logs
    add column rest_taken_seconds integer;

alter table workout_exercise_set_logs
    add constraint ck_workout_exercise_set_logs_rest_taken
        check (rest_taken_seconds is null or (rest_taken_seconds >= 0 and rest_taken_seconds <= 21600));
