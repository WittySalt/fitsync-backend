-- How many seconds one rep takes when the exercise screen counts along with the person. A plan
-- exercise may set it, and a session started from the plan keeps its own copy, so a later change
-- to the plan does not rewrite a workout that already happened. Empty means the screen uses the
-- default for the kind of exercise. Both columns are new and nullable, so existing rows stay valid.
alter table workout_plan_exercises
    add column tempo_seconds numeric(3, 1);

alter table workout_plan_exercises
    add constraint ck_workout_plan_exercises_tempo
        check (tempo_seconds is null or (tempo_seconds >= 1.0 and tempo_seconds <= 10.0));

alter table workout_exercise_logs
    add column tempo_seconds numeric(3, 1);

alter table workout_exercise_logs
    add constraint ck_workout_exercise_logs_tempo
        check (tempo_seconds is null or (tempo_seconds >= 1.0 and tempo_seconds <= 10.0));
