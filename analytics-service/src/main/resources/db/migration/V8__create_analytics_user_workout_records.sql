-- AN-11: per-user personal-record tally projected from workout-service PersonalRecordAchieved
-- events. The achievements endpoint only needs "has this user ever set a PR" (PR_FIRST) plus a
-- count, so a single counter row per user is enough — no per-exercise public endpoint is opened.

create table analytics_user_workout_records (
    user_id uuid primary key,
    personal_record_count integer not null default 0,
    first_record_at timestamp with time zone,
    last_record_at timestamp with time zone,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);
