-- Plans and sessions each get one entry table. An entry is a thing done in order with a planned value
-- and an actual value: a SET entry is a move measured in sets, reps and weight (its sets stay in their
-- own table under it), a SEGMENT entry is a stretch of time such as a warm-up or an interval. Starting
-- a session copies the plan's entries into session entries, so a later edit of the plan never changes
-- a workout that already happened. Only renames and additive columns: every existing row becomes a SET
-- entry with its meaning untouched.

-- Plan entries.
alter table workout_plan_exercises rename to workout_plan_entries;

alter table workout_plan_entries
    add column entry_type varchar(8) not null default 'SET';

-- What a segment is: WARMUP, STEADY, WORK, EASY or COOLDOWN. Empty for a SET entry.
alter table workout_plan_entries
    add column kind varchar(10);

alter table workout_plan_entries
    add column label varchar(40);

-- The length of a segment, or of one set of a time-based move.
alter table workout_plan_entries
    add column planned_seconds integer;

-- How many times a segment repeats, for intervals. Segments only.
alter table workout_plan_entries
    add column rounds smallint;

alter table workout_plan_entries
    add column position integer;

update workout_plan_entries
set position = sequence_no;

alter table workout_plan_entries
    alter column position set not null;

alter table workout_plan_entries
    alter column exercise_id drop not null;

alter table workout_plan_entries
    add constraint ck_workout_plan_entries_type
        check ((entry_type = 'SET' and exercise_id is not null)
            or (entry_type = 'SEGMENT' and kind is not null));

alter table workout_plan_entries
    add constraint ck_workout_plan_entries_rounds
        check (rounds is null or (rounds >= 1 and rounds <= 50));

-- Session entries.
alter table workout_exercise_logs rename to workout_session_entries;

alter table workout_session_entries
    add column entry_type varchar(8) not null default 'SET';

alter table workout_session_entries
    add column kind varchar(10);

alter table workout_session_entries
    add column label varchar(40);

alter table workout_session_entries
    add column planned_seconds integer;

alter table workout_session_entries
    add column actual_seconds integer;

alter table workout_session_entries
    add column started_at timestamp with time zone;

-- The session's local date, so the history of one exercise can be read by date without the session.
alter table workout_session_entries
    add column logged_on date;

-- Whether a segment was done or skipped. A SET entry keeps the status on each of its sets.
alter table workout_session_entries
    add column status varchar(20);

alter table workout_session_entries
    add column position integer;

update workout_session_entries
set position = sequence_no;

alter table workout_session_entries
    alter column position set not null;

-- Existing entries take the date their session started on, in the database's own zone. New entries
-- take the session's local date in the person's zone.
update workout_session_entries
set logged_on = (
    select cast(s.started_at as date)
    from workout_sessions s
    where s.session_id = workout_session_entries.session_id);

alter table workout_session_entries
    alter column exercise_id drop not null;

alter table workout_session_entries
    add constraint ck_workout_session_entries_type
        check ((entry_type = 'SET' and exercise_id is not null)
            or (entry_type = 'SEGMENT' and kind is not null));

alter table workout_session_entries
    add constraint ck_workout_session_entries_actual
        check (actual_seconds is null or (actual_seconds >= 0 and actual_seconds <= 86400));

create index idx_workout_session_entries_session_position
    on workout_session_entries (session_id, position);

-- The partial index on (exercise_id, logged_on) for SET entries is created by the Java migration
-- V16_1, since H2, which the tests run on, has no partial indexes.

-- The plan day's target and setting for cardio and sport days.
alter table workout_plan_days
    add column target_kind varchar(12);

alter table workout_plan_days
    add column target_value numeric(7, 2);

alter table workout_plan_days
    add column location varchar(20);

alter table workout_plan_days
    add column effort_target smallint;

alter table workout_plan_days
    add constraint ck_workout_plan_days_effort_target
        check (effort_target is null or (effort_target >= 1 and effort_target <= 10));
