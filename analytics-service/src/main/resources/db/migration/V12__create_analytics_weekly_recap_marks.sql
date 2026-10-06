-- The weeks a WeeklyRecapGenerated event was emitted for, one row per user and week. The Monday cron
-- had no catch-up, so a stack that was off at 00:15 UTC left the coach quoting a recap two weeks old.
-- Analytics now also runs the recap on startup and skips every user who already has a row here for the
-- last full week, so each week's recap is emitted once however often the service restarts.

create table analytics_weekly_recap_marks (
    user_id uuid not null,
    week_start date not null,
    emitted_at timestamp with time zone not null,
    constraint pk_analytics_weekly_recap_marks primary key (user_id, week_start)
);
