create table analytics_user_daily_body (
    user_id uuid not null,
    metric_date date not null,
    weight_kg numeric(6, 2),
    body_fat_rate numeric(5, 2),
    waist_cm numeric(6, 2),
    bmi numeric(6, 2),
    weight_delta_from_start numeric(6, 2),
    weight_delta_from_previous numeric(6, 2),
    projection_updated_at timestamp with time zone not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint pk_analytics_user_daily_body primary key (user_id, metric_date)
);

create index idx_analytics_user_daily_body_user_date
    on analytics_user_daily_body (user_id, metric_date desc);

create table analytics_user_daily_workout (
    user_id uuid not null,
    activity_date date not null,
    completed_workout_count integer not null default 0,
    total_workout_minutes integer not null default 0,
    total_calories_burned integer not null default 0,
    total_exercise_count integer not null default 0,
    rolling_7_day_workout_count integer not null default 0,
    rolling_28_day_workout_count integer not null default 0,
    projection_updated_at timestamp with time zone not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint pk_analytics_user_daily_workout primary key (user_id, activity_date)
);

create index idx_analytics_user_daily_workout_user_date
    on analytics_user_daily_workout (user_id, activity_date desc);

create table analytics_user_daily_nutrition (
    user_id uuid not null,
    activity_date date not null,
    meal_count integer not null default 0,
    total_calories_consumed integer,
    total_protein_grams numeric(10, 2),
    total_carbs_grams numeric(10, 2),
    total_fat_grams numeric(10, 2),
    calorie_target integer,
    protein_target_grams numeric(10, 2),
    calorie_adherence_ratio numeric(6, 4),
    macro_adherence_score numeric(6, 4),
    projection_updated_at timestamp with time zone not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint pk_analytics_user_daily_nutrition primary key (user_id, activity_date)
);

create index idx_analytics_user_daily_nutrition_user_date
    on analytics_user_daily_nutrition (user_id, activity_date desc);

create table analytics_user_goal_progress (
    goal_id uuid primary key,
    user_id uuid not null,
    goal_type varchar(40) not null,
    goal_status varchar(30) not null,
    target_value numeric(10, 2),
    target_unit varchar(20),
    target_date date,
    current_value numeric(10, 2),
    progress_ratio numeric(6, 4),
    days_remaining integer,
    on_track boolean not null default false,
    last_evaluated_at timestamp with time zone not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_analytics_user_goal_progress_user_id
    on analytics_user_goal_progress (user_id, target_date, goal_id);

create table analytics_user_dashboard_snapshot (
    user_id uuid primary key,
    timezone varchar(80),
    latest_weight_kg numeric(6, 2),
    latest_body_fat_rate numeric(5, 2),
    bmi numeric(6, 2),
    weight_change_30d numeric(6, 2),
    today_workout_count integer not null default 0,
    week_workout_count integer not null default 0,
    week_workout_minutes integer not null default 0,
    active_streak_days integer not null default 0,
    today_calories_consumed integer,
    today_calories_target integer,
    today_protein_grams numeric(10, 2),
    today_protein_target_grams numeric(10, 2),
    generated_at timestamp with time zone not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create table analytics_processed_events (
    event_id uuid primary key,
    event_type varchar(120) not null,
    user_id uuid,
    processed_at timestamp with time zone not null
);

create index idx_analytics_processed_events_user_processed_at
    on analytics_processed_events (user_id, processed_at desc);
