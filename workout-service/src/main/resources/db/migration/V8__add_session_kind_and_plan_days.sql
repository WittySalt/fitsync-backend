-- Every workout session gets a kind, which is what the interface switches on: strength, cardio,
-- mobility or sport. Workout plans get a table for a schedule by day of the week.

alter table workout_sessions
    add column kind varchar(20);

alter table workout_sessions
    add column sport_activity varchar(40);

alter table workout_sessions
    add column plan_day_id uuid;

-- The kind of an existing session comes from its workout type. A custom session takes the kind of
-- most of its exercises, with the mapping SessionKindResolver uses, and a custom session without
-- any exercise of a known type is a sport, as it is when it is written. A tie goes to strength,
-- then cardio, then mobility. At write time a tie goes to the earliest exercise, and the order of
-- old exercises is not worth a second pass over the table.
update workout_sessions ws
set kind = case
    when ws.workout_type = 'STRENGTH' then 'STRENGTH'
    when ws.workout_type in ('CARDIO', 'HIIT') then 'CARDIO'
    when ws.workout_type in ('YOGA', 'PILATES', 'MOBILITY') then 'MOBILITY'
    when ws.workout_type = 'SPORTS' then 'SPORT'
    when not exists (
        select 1
        from workout_exercise_logs wel
        join exercises e on e.exercise_id = wel.exercise_id
        where wel.session_id = ws.session_id
          and e.exercise_type in ('STRENGTH', 'SPORT', 'CARDIO', 'MOBILITY', 'BALANCE')
    ) then 'SPORT'
    when (
        select count(*)
        from workout_exercise_logs wel
        join exercises e on e.exercise_id = wel.exercise_id
        where wel.session_id = ws.session_id
          and e.exercise_type in ('STRENGTH', 'SPORT')
    ) >= (
        select count(*)
        from workout_exercise_logs wel
        join exercises e on e.exercise_id = wel.exercise_id
        where wel.session_id = ws.session_id
          and e.exercise_type = 'CARDIO'
    ) and (
        select count(*)
        from workout_exercise_logs wel
        join exercises e on e.exercise_id = wel.exercise_id
        where wel.session_id = ws.session_id
          and e.exercise_type in ('STRENGTH', 'SPORT')
    ) >= (
        select count(*)
        from workout_exercise_logs wel
        join exercises e on e.exercise_id = wel.exercise_id
        where wel.session_id = ws.session_id
          and e.exercise_type in ('MOBILITY', 'BALANCE')
    ) then 'STRENGTH'
    when (
        select count(*)
        from workout_exercise_logs wel
        join exercises e on e.exercise_id = wel.exercise_id
        where wel.session_id = ws.session_id
          and e.exercise_type = 'CARDIO'
    ) >= (
        select count(*)
        from workout_exercise_logs wel
        join exercises e on e.exercise_id = wel.exercise_id
        where wel.session_id = ws.session_id
          and e.exercise_type in ('MOBILITY', 'BALANCE')
    ) then 'CARDIO'
    else 'MOBILITY'
end
where ws.kind is null;

alter table workout_sessions
    alter column kind set not null;

create table workout_plan_days (
    plan_day_id uuid primary key,
    plan_id uuid not null references workout_plans(plan_id) on delete cascade,
    day_of_week integer not null,
    kind varchar(20) not null,
    title varchar(120) not null,
    sport_activity varchar(40),
    estimated_duration_minutes integer,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint ck_workout_plan_days_day_of_week check (day_of_week between 1 and 7)
);

create unique index uk_workout_plan_days_plan_day
    on workout_plan_days (plan_id, day_of_week);

alter table workout_plan_exercises
    add column plan_day_id uuid references workout_plan_days(plan_day_id);
