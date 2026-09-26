-- recommendation-service owns its own data (README §7). Two tables:
--   recommendation_context — per-user read model assembled from events (shared input)
--   recommendation_result  — cached, ready-to-serve recommendations (the read API source)

create table recommendation_context (
    user_id uuid not null,
    current_goal_type varchar(40),
    current_goal_target_value numeric(12, 3),
    current_goal_target_unit varchar(20),
    current_goal_target_date date,
    energy_target_calories integer,
    protein_target_grams numeric(10, 2),
    latest_weight_kg numeric(6, 2),
    latest_body_fat_rate numeric(5, 2),
    body_metrics_recorded_at timestamp with time zone,
    workout_days_per_week integer,
    dietary_preference varchar(40),
    focus_areas varchar(255),
    equipment_inventory varchar(500),
    recent_workout_count integer,
    recent_workout_minutes integer,
    recent_active_days integer,
    recent_week_start date,
    today_calories_consumed integer,
    today_protein_grams numeric(10, 2),
    current_streak_days integer,
    timezone varchar(80),
    stale boolean not null default true,
    context_hash varchar(64),
    last_assembled_at timestamp with time zone,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint pk_recommendation_context primary key (user_id)
);

create table recommendation_result (
    id uuid not null,
    user_id uuid not null,
    type varchar(60) not null,
    source varchar(20) not null,
    payload text not null,
    generated_at timestamp with time zone not null,
    expires_at timestamp with time zone,
    context_hash varchar(64),
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint pk_recommendation_result primary key (id),
    constraint uq_recommendation_result_user_type unique (user_id, type)
);

create index idx_recommendation_result_user_id
    on recommendation_result (user_id);

create index idx_recommendation_result_expires_at
    on recommendation_result (expires_at);
