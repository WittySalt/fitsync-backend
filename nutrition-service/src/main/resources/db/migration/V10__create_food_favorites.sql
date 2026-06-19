-- NU-2: per-user favorite catalog foods for one-tap re-logging. Unique per (user, food).

create table food_favorites (
    favorite_id uuid primary key,
    user_id uuid not null,
    food_id uuid not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint uq_food_favorites_user_food unique (user_id, food_id)
);

create index idx_food_favorites_user on food_favorites (user_id, date_time_created desc);
