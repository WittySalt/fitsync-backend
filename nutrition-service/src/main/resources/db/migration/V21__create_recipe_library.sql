-- Recipes: a shared library of system recipes next to the user's own recipes. System recipes have no
-- owner (user_id is null), carry their source and a picture link, and are marked as published when they
-- show in the library. Deleting a recipe marks deleted_at instead of removing the row. Nutrition, tags and
-- diet flags are still computed on read from the ingredients, so none of them is stored here.

alter table recipes add column scope varchar(10) not null default 'USER';
alter table recipes alter column user_id drop not null;
alter table recipes add column source varchar(30);
alter table recipes add column source_ref varchar(60);
alter table recipes add column source_url varchar(500);
alter table recipes add column image_url varchar(500);
alter table recipes add column image_credit varchar(120);
alter table recipes add column category varchar(60);
alter table recipes add column cuisine varchar(60);
alter table recipes add column prep_minutes integer;
alter table recipes add column published boolean not null default true;
alter table recipes add column deleted_at timestamp with time zone;

create index idx_recipes_library on recipes (scope, published, category, deleted_at);
create index idx_recipes_scope_name on recipes (scope, name);
create index idx_recipes_source_ref on recipes (source, source_ref);

-- The original ingredient line, shown as written, and the grams it was parsed to. When grams is set the
-- nutrition is taken per 100 g of the food times these grams, with no serving conversion.
alter table recipe_ingredients add column display_text varchar(200);
alter table recipe_ingredients add column grams numeric(10, 2);

create table recipe_steps (
    step_id uuid primary key,
    recipe_id uuid not null,
    step_number integer not null,
    text varchar(1000) not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_recipe_steps_recipe foreign key (recipe_id) references recipes (recipe_id) on delete cascade,
    constraint uq_recipe_steps_number unique (recipe_id, step_number)
);

create table recipe_favorites (
    favorite_id uuid primary key,
    user_id uuid not null,
    recipe_id uuid not null,
    saved_at timestamp with time zone not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_recipe_favorites_recipe foreign key (recipe_id) references recipes (recipe_id) on delete cascade,
    constraint uq_recipe_favorites_pair unique (user_id, recipe_id)
);

create index idx_recipe_favorites_user on recipe_favorites (user_id, saved_at);

-- A meal item or a template item that came from a recipe keeps the recipe and the servings, so the rows
-- of one recipe can be shown together.
alter table meal_items add column recipe_id uuid;
alter table meal_items add column recipe_servings numeric(10, 2);
alter table meal_template_items add column recipe_id uuid;
alter table meal_template_items add column recipe_servings numeric(10, 2);

create index idx_meal_items_recipe on meal_items (recipe_id);
