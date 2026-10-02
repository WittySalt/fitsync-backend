alter table user_nutrition_preferences
    add column if not exists nutrient_reference_range varchar(20);
