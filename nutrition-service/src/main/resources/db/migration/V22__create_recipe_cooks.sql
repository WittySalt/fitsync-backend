-- A record of "I cooked this", kept apart from eating it. One row is one time a user cooked a recipe, with the
-- servings made and when the cooking ended. The client sends its own id for each cook so that a retry never
-- writes a second row. Deleting marks deleted_at instead of removing the row.

create table recipe_cooks (
    cook_id uuid primary key,
    user_id uuid not null,
    recipe_id uuid not null,
    client_cook_id uuid not null,
    servings numeric(5, 2) not null,
    started_at timestamp with time zone,
    finished_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    deleted_at timestamp with time zone,
    constraint fk_recipe_cooks_recipe foreign key (recipe_id) references recipes (recipe_id) on delete cascade,
    constraint uq_recipe_cooks_client unique (user_id, client_cook_id),
    constraint ck_recipe_cooks_servings check (servings > 0)
);

create index idx_recipe_cooks_user_finished on recipe_cooks (user_id, finished_at desc);
