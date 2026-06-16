create table analytics_meal_contributions (
    meal_id uuid primary key,
    user_id uuid not null,
    activity_date date not null,
    total_calories integer,
    total_protein_grams numeric(10, 2),
    total_carbs_grams numeric(10, 2),
    total_fat_grams numeric(10, 2),
    item_count integer not null default 0,
    last_applied_at timestamp with time zone not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_analytics_meal_contributions_user_date
    on analytics_meal_contributions (user_id, activity_date desc);
