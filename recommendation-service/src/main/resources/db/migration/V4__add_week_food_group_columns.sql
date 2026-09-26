-- Weekly food-group balance for the coach narrative: MealLogged (EV-1) carries a
-- per-food-group calorie breakdown; the handler accumulates it over the user's
-- current week (Monday-anchored, user timezone) so the A1 prompt can phrase
-- "meat-heavy / veg-light" without reading analytics internals.

alter table recommendation_context add column nutrition_week_start date;
alter table recommendation_context add column week_food_group_calories varchar(1000);
