-- RC-8: event consumers need (a) profile inputs for the local TDEE estimate
-- (UserProfileUpdated carries heightCm/activityLevel), (b) a date anchor for the
-- running "today" nutrition totals, and (c) an idempotency ledger so replayed
-- events (at-least-once delivery) are applied exactly once.

alter table recommendation_context add column height_cm numeric(5, 1);
alter table recommendation_context add column activity_level varchar(40);
alter table recommendation_context add column today_date date;

create table recommendation_processed_events (
    event_id uuid primary key,
    event_type varchar(120) not null,
    user_id uuid,
    processed_at timestamp with time zone not null
);

create index idx_recommendation_processed_events_user
    on recommendation_processed_events (user_id, processed_at desc);
