alter table outbox_events rename column version to event_version;
alter table outbox_events rename column occurred_at to date_time_produced;
alter table outbox_events rename column published_at to date_time_published;
alter table outbox_events rename column created_at to date_time_created;

alter table meal_items add column food_catalog_item_id uuid;
alter table meal_items add column food_serving_id uuid;
alter table meal_items add column barcode varchar(40);
alter table meal_items add column snapshot_serving_label varchar(80);

alter table nutrition_targets add column carb_goal_id uuid;
alter table nutrition_targets add column carb_target_grams numeric(10, 2);
alter table nutrition_targets add column carb_target_date date;
alter table nutrition_targets add column fat_goal_id uuid;
alter table nutrition_targets add column fat_target_grams numeric(10, 2);
alter table nutrition_targets add column fat_target_date date;
alter table nutrition_targets rename column updated_at to projection_updated_at;
alter table nutrition_targets add column date_time_created timestamp with time zone default current_timestamp not null;
alter table nutrition_targets add column date_time_updated timestamp with time zone default current_timestamp not null;
alter table nutrition_targets add column created_by varchar(120) default 'system' not null;
alter table nutrition_targets add column updated_by varchar(120) default 'system' not null;
alter table nutrition_targets add column version bigint default 0 not null;

create table food_brands (
    brand_id uuid primary key,
    name varchar(120) not null,
    normalized_name varchar(120) not null,
    country_code varchar(10),
    date_effective_from date,
    date_effective_to date,
    active boolean not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create unique index idx_food_brands_normalized_name
    on food_brands (normalized_name);

create table food_catalog_items (
    food_id uuid primary key,
    scope varchar(20) not null,
    owner_user_id uuid,
    source varchar(20) not null,
    display_name varchar(120) not null,
    normalized_name varchar(120) not null,
    category_code varchar(40),
    brand_id uuid,
    default_serving_id uuid,
    verified boolean not null,
    density_grams_per_ml numeric(10, 4),
    date_effective_from date,
    date_effective_to date,
    active boolean not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_food_catalog_items_scope_name
    on food_catalog_items (scope, normalized_name);

create index idx_food_catalog_items_owner
    on food_catalog_items (owner_user_id);

create table food_servings (
    serving_id uuid primary key,
    food_id uuid not null,
    label varchar(80) not null,
    quantity numeric(10, 2) not null,
    unit varchar(30) not null,
    grams_equivalent numeric(10, 2),
    milliliters_equivalent numeric(10, 2),
    sort_order integer not null,
    default_serving boolean not null,
    date_effective_from date,
    date_effective_to date,
    active boolean not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_food_servings_food_id
    on food_servings (food_id, sort_order);

create table food_barcodes (
    barcode_id uuid primary key,
    food_id uuid not null,
    barcode varchar(40) not null unique,
    region_code varchar(10),
    primary_barcode boolean not null,
    date_effective_from date,
    date_effective_to date,
    active boolean not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create table food_nutrient_profiles (
    nutrient_profile_id uuid primary key,
    food_id uuid not null,
    basis_quantity numeric(10, 2) not null,
    basis_unit varchar(30) not null,
    calories integer not null,
    protein_grams numeric(10, 2),
    carbs_grams numeric(10, 2),
    fat_grams numeric(10, 2),
    saturated_fat_grams numeric(10, 2),
    trans_fat_grams numeric(10, 2),
    fiber_grams numeric(10, 2),
    sugar_grams numeric(10, 2),
    sodium_mg numeric(10, 2),
    potassium_mg numeric(10, 2),
    cholesterol_mg numeric(10, 2),
    date_effective_from date,
    date_effective_to date,
    active boolean not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_food_nutrient_profiles_food_id
    on food_nutrient_profiles (food_id);

create table meal_item_nutrient_details (
    detail_id uuid primary key,
    meal_item_id uuid not null,
    nutrient_code varchar(40) not null,
    display_name varchar(80) not null,
    amount numeric(10, 2) not null,
    unit varchar(20) not null,
    sort_order integer not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_meal_item_nutrient_details_meal_item
        foreign key (meal_item_id) references meal_items (meal_item_id) on delete cascade
);

create index idx_meal_item_nutrient_details_meal_item
    on meal_item_nutrient_details (meal_item_id, sort_order);

create table nutrition_preference_projections (
    user_id uuid primary key,
    preferred_units_code varchar(40),
    dietary_preference_code varchar(40),
    workout_days_per_week integer,
    notifications_enabled boolean not null,
    projection_updated_at timestamp with time zone not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);
