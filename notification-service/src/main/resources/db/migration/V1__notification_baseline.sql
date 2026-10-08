-- Everything notification-service knows about a user, all taken from domain events.
-- The emails it may send are in Documents/architecture/email-notifications-spec.md.

-- One row per user: where to send, and the settings that decide whether and when.
create table notification_recipients (
    user_id uuid primary key,
    email varchar(320),
    display_name varchar(120),
    email_enabled boolean not null default false,
    workout_reminder_enabled boolean not null default false,
    goal_progress_enabled boolean not null default false,
    weekly_report_enabled boolean not null default false,
    weekly_report_channel varchar(20),
    quiet_hours_start time,
    quiet_hours_end time,
    timezone varchar(64),
    preferred_workout_time time,
    -- Set while a deletion request is pending; such a user gets no email but E6.
    deletion_scheduled_for timestamp with time zone,
    last_workout_at timestamp with time zone,
    updated_at timestamp with time zone not null
);

-- The active plan's weekly template, replaced whole on every WorkoutPlanScheduleChanged.
create table notification_plan_days (
    user_id uuid not null,
    day_of_week integer not null,
    kind varchar(20) not null,
    title varchar(120) not null,
    estimated_minutes integer,
    exercise_count integer not null,
    primary key (user_id, day_of_week),
    constraint ck_notification_plan_days_day_of_week check (day_of_week between 1 and 7)
);

-- Goals as user-service last published them; E4 reads WEIGHT goals, E5 the weekly session goal.
create table notification_goals (
    goal_id uuid primary key,
    user_id uuid not null,
    goal_type varchar(40) not null,
    target_value numeric(10, 2),
    status varchar(20) not null,
    -- The weight the user had when the goal was first seen; it says which way the goal points.
    start_weight_kg numeric(6, 2),
    started_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_notification_goals_user on notification_goals (user_id);

create table notification_weights (
    metric_id uuid primary key,
    user_id uuid not null,
    weight_kg numeric(6, 2) not null,
    measured_at timestamp with time zone not null
);

create index idx_notification_weights_user_measured on notification_weights (user_id, measured_at);

-- Every email, from the moment it is decided until it is sent or dropped. The unique key is what
-- keeps an email from going out twice: one E1 per local day, one E4 per goal, one E5 per week.
create table notification_emails (
    email_id uuid primary key,
    user_id uuid not null,
    kind varchar(20) not null,
    dedupe_key varchar(80) not null,
    status varchar(20) not null,
    subject varchar(200) not null,
    body_text text not null,
    due_at timestamp with time zone not null,
    expires_at timestamp with time zone,
    attempts integer not null default 0,
    sent_at timestamp with time zone,
    created_at timestamp with time zone not null,
    constraint uk_notification_emails_dedupe unique (user_id, kind, dedupe_key)
);

create index idx_notification_emails_pending on notification_emails (status, due_at);
