create table analytics_workout_contributions (
    session_id uuid primary key,
    user_id uuid not null,
    activity_date date not null,
    duration_minutes integer,
    calories_burned integer,
    exercise_count integer,
    last_applied_at timestamp with time zone not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_analytics_workout_contributions_user_date
    on analytics_workout_contributions (user_id, activity_date desc);
