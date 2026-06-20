-- NU-3: saved-meal templates. A template is a named, time-less set of items (mirroring meal_items
-- minus meal-specific fields); materializing it into a real meal reuses the normal meal-item path.

create table meal_templates (
    template_id uuid primary key,
    user_id uuid not null,
    name varchar(120) not null,
    meal_type varchar(30),
    note varchar(300),
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_meal_templates_user_name on meal_templates (user_id, name);

create table meal_template_items (
    template_item_id uuid primary key,
    template_id uuid not null,
    custom_food_id uuid,
    food_catalog_item_id uuid,
    food_serving_id uuid,
    external_food_ref varchar(120),
    barcode varchar(40),
    food_name varchar(120) not null,
    brand_name varchar(120),
    quantity numeric(10, 2) not null,
    unit varchar(30) not null,
    serving_size numeric(10, 2),
    serving_unit varchar(30),
    snapshot_serving_label varchar(80),
    calories integer not null,
    protein_grams numeric(10, 2),
    carbs_grams numeric(10, 2),
    fat_grams numeric(10, 2),
    fiber_grams numeric(10, 2),
    sugar_grams numeric(10, 2),
    sodium_mg numeric(10, 2),
    sort_order integer not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_meal_template_items_template foreign key (template_id) references meal_templates (template_id) on delete cascade
);

create index idx_meal_template_items_template on meal_template_items (template_id, sort_order);
