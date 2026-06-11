create table meal_logs (
    meal_id uuid primary key,
    user_id uuid not null,
    meal_type varchar(30) not null,
    name varchar(120),
    source varchar(30) not null,
    consumed_at timestamp with time zone not null,
    timezone varchar(80),
    note varchar(300),
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_meal_logs_user_consumed_at
    on meal_logs (user_id, consumed_at desc);

create table meal_items (
    meal_item_id uuid primary key,
    meal_id uuid not null,
    custom_food_id uuid,
    external_food_ref varchar(120),
    food_name varchar(120) not null,
    brand_name varchar(120),
    quantity numeric(10, 2) not null,
    unit varchar(30) not null,
    serving_size numeric(10, 2),
    serving_unit varchar(30),
    calories integer not null,
    protein_grams numeric(10, 2),
    carbs_grams numeric(10, 2),
    fat_grams numeric(10, 2),
    fiber_grams numeric(10, 2),
    sugar_grams numeric(10, 2),
    sodium_mg numeric(10, 2),
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_meal_items_meal_logs foreign key (meal_id) references meal_logs (meal_id) on delete cascade
);

create index idx_meal_items_meal_id
    on meal_items (meal_id);

create table custom_foods (
    custom_food_id uuid primary key,
    user_id uuid not null,
    name varchar(120) not null,
    brand_name varchar(120),
    serving_size numeric(10, 2) not null,
    serving_unit varchar(30) not null,
    calories integer not null,
    protein_grams numeric(10, 2),
    carbs_grams numeric(10, 2),
    fat_grams numeric(10, 2),
    fiber_grams numeric(10, 2),
    sugar_grams numeric(10, 2),
    sodium_mg numeric(10, 2),
    verified boolean not null default false,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_custom_foods_user_name
    on custom_foods (user_id, name);

create table outbox_events (
    event_id uuid primary key,
    topic varchar(120) not null,
    event_type varchar(120) not null,
    aggregate_type varchar(120) not null,
    aggregate_id varchar(120) not null,
    user_id uuid,
    version integer not null,
    payload text not null,
    status varchar(30) not null,
    occurred_at timestamp with time zone not null,
    published_at timestamp with time zone,
    error_message varchar(500),
    created_at timestamp with time zone not null
);

create index idx_nutrition_outbox_events_status_created_at
    on outbox_events (status, created_at);
