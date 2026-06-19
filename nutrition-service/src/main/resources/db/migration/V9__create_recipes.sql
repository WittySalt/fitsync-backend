-- NU-7: user recipes composed from catalog foods. A recipe is a named set of ingredients plus a
-- yield (servings); its nutrition is recomputed on read by aggregating each ingredient's catalog
-- nutrition (no materialized totals, so it always reflects current catalog data).

create table recipes (
    recipe_id uuid primary key,
    user_id uuid not null,
    name varchar(120) not null,
    servings numeric(10, 2) not null,
    note varchar(300),
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_recipes_user_name on recipes (user_id, name);

create table recipe_ingredients (
    ingredient_id uuid primary key,
    recipe_id uuid not null,
    food_catalog_item_id uuid not null,
    food_serving_id uuid,
    quantity numeric(10, 2) not null,
    sort_order integer not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_recipe_ingredients_recipe foreign key (recipe_id) references recipes (recipe_id) on delete cascade
);

create index idx_recipe_ingredients_recipe on recipe_ingredients (recipe_id, sort_order);
