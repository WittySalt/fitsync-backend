-- NU-9: per-user water (hydration) logging. Daily totals are computed in the user's timezone.

create table water_logs (
    water_id uuid primary key,
    user_id uuid not null,
    amount_ml integer not null,
    logged_at timestamp with time zone not null,
    timezone varchar(80),
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_water_logs_user_logged_at on water_logs (user_id, logged_at desc);
