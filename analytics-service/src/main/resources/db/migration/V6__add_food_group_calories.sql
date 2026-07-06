-- AN-1: food-group energy aggregation. Both columns are nullable and encoded as "CODE=kcal;CODE=kcal"
-- (a compact internal format, not a wire contract). The per-meal snapshot on analytics_meal_contributions
-- lets the daily aggregate on analytics_user_daily_nutrition be delta-adjusted on meal update/delete,
-- consistent with how calories/macros are already projected. Fed by MealLogged/MealUpdated.foodGroupCalories.

alter table analytics_meal_contributions add column food_group_calories varchar(400);
alter table analytics_user_daily_nutrition add column food_group_calories varchar(600);
