-- AN-1: nightly-materialized workout streak rollup. best_streak_* is recomputed per user by the
-- daily rollup job from analytics_user_daily_workout active days, so the streaks read endpoint
-- never has to scan a user's full history at request time. The live "current" streak is NOT
-- stored here — it stays on analytics_user_dashboard_snapshot.active_streak_days so both the
-- dashboard and the streaks endpoint share one streak definition.

create table analytics_user_streak_rollup (
    user_id uuid primary key,
    best_streak_days integer not null default 0,
    best_streak_from date,
    best_streak_to date,
    last_active_date date,
    computed_at timestamp with time zone not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);
