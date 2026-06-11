create table exercises (
    exercise_id uuid primary key,
    owner_user_id uuid,
    code varchar(100) not null,
    name varchar(120) not null,
    description varchar(1000),
    exercise_type varchar(30) not null,
    primary_muscle_group varchar(40) not null,
    equipment varchar(40) not null,
    difficulty varchar(30) not null,
    status varchar(30) not null,
    custom boolean not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create unique index uk_exercises_code
    on exercises (code);

create index idx_exercises_owner_status_name
    on exercises (owner_user_id, status, name);

create table workout_plans (
    plan_id uuid primary key,
    user_id uuid not null,
    name varchar(120) not null,
    description varchar(1000),
    goal_focus varchar(40) not null,
    workout_type varchar(30) not null,
    difficulty varchar(30) not null,
    estimated_duration_minutes integer,
    status varchar(30) not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_workout_plans_user_updated_at
    on workout_plans (user_id, date_time_updated desc);

create table workout_plan_exercises (
    plan_exercise_id uuid primary key,
    plan_id uuid not null references workout_plans(plan_id) on delete cascade,
    exercise_id uuid not null references exercises(exercise_id),
    exercise_name_snapshot varchar(120) not null,
    sequence_no integer not null,
    target_sets integer,
    target_reps integer,
    target_weight_kg numeric(10, 2),
    target_duration_seconds integer,
    target_distance_km numeric(10, 2),
    rest_seconds integer,
    notes varchar(300)
);

create unique index uk_workout_plan_exercises_plan_sequence
    on workout_plan_exercises (plan_id, sequence_no);

create index idx_workout_plan_exercises_exercise
    on workout_plan_exercises (exercise_id);

create table workout_plan_exercise_set_targets (
    set_target_id uuid primary key,
    plan_exercise_id uuid not null references workout_plan_exercises(plan_exercise_id) on delete cascade,
    set_number integer not null,
    target_reps integer,
    target_weight_kg numeric(10, 2),
    target_duration_seconds integer,
    target_distance_km numeric(10, 2),
    rest_seconds integer
);

create unique index uk_workout_plan_exercise_set_targets_set
    on workout_plan_exercise_set_targets (plan_exercise_id, set_number);

create table workout_sessions (
    session_id uuid primary key,
    user_id uuid not null,
    plan_id uuid,
    name varchar(120) not null,
    workout_type varchar(30) not null,
    status varchar(30) not null,
    source varchar(30) not null,
    started_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    duration_minutes integer,
    calories_burned integer,
    perceived_exertion integer,
    notes varchar(1000),
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_workout_sessions_plan
        foreign key (plan_id) references workout_plans(plan_id)
);

create index idx_workout_sessions_user_started_at
    on workout_sessions (user_id, started_at desc);

create table workout_exercise_logs (
    exercise_log_id uuid primary key,
    session_id uuid not null references workout_sessions(session_id) on delete cascade,
    exercise_id uuid not null references exercises(exercise_id),
    exercise_name_snapshot varchar(120) not null,
    sequence_no integer not null,
    performed_sets integer,
    performed_reps integer,
    total_volume_kg numeric(10, 2),
    distance_km numeric(10, 2),
    duration_seconds integer,
    average_heart_rate integer,
    max_heart_rate integer,
    notes varchar(300)
);

create unique index uk_workout_exercise_logs_session_sequence
    on workout_exercise_logs (session_id, sequence_no);

create index idx_workout_exercise_logs_exercise
    on workout_exercise_logs (exercise_id);

create table workout_exercise_set_logs (
    set_log_id uuid primary key,
    exercise_log_id uuid not null references workout_exercise_logs(exercise_log_id) on delete cascade,
    set_number integer not null,
    reps integer,
    weight_kg numeric(10, 2),
    duration_seconds integer,
    distance_km numeric(10, 2),
    rest_seconds integer,
    perceived_exertion integer,
    notes varchar(300)
);

create unique index uk_workout_exercise_set_logs_set
    on workout_exercise_set_logs (exercise_log_id, set_number);

create table outbox_events (
    event_id uuid primary key,
    topic varchar(120) not null,
    event_type varchar(120) not null,
    aggregate_type varchar(120) not null,
    aggregate_id varchar(120) not null,
    user_id uuid,
    event_version integer not null,
    payload text not null,
    status varchar(30) not null,
    date_time_produced timestamp with time zone not null,
    date_time_published timestamp with time zone,
    error_message varchar(500),
    date_time_created timestamp with time zone not null
);

create index idx_workout_outbox_events_status_created_at
    on outbox_events (status, date_time_created);
