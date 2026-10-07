-- Leftovers: how many whole servings of a cook are still uneaten. It starts as the servings cooked minus the
-- servings logged from the Finished card and drops by one each time a leftover serving is logged. Cooks that
-- were recorded before this column existed keep 0, so they never show up as leftovers.

alter table recipe_cooks add column servings_left integer not null default 0;

alter table recipe_cooks add constraint ck_recipe_cooks_servings_left check (servings_left >= 0);
