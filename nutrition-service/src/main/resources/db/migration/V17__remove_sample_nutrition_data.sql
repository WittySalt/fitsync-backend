-- Removes the rows V6 seeded for the sample account: its meals, its custom food, its targets and its
-- preference projection. The shared food catalog rows that V6 also inserted (brands, foods, servings,
-- barcodes and nutrient profiles) are left in place because other people's meals may already point at
-- them. Meal items and their nutrient details are removed through their meal, and the meal keeps the
-- created_by marker so that anything someone has since replaced is left alone. Each statement is a no-op
-- when the rows are already gone.
delete from meal_item_nutrient_details
where meal_item_id in (
    select i.meal_item_id
    from meal_items i
    join meal_logs m on m.meal_id = i.meal_id
    where m.meal_id in (
        '48111111-1111-1111-1111-111111111111',
        '48111111-1111-1111-1111-111111111112')
      and m.created_by = 'sample-data');

delete from meal_items
where meal_id in (
    select m.meal_id
    from meal_logs m
    where m.meal_id in (
        '48111111-1111-1111-1111-111111111111',
        '48111111-1111-1111-1111-111111111112')
      and m.created_by = 'sample-data');

delete from meal_logs
where meal_id in (
    '48111111-1111-1111-1111-111111111111',
    '48111111-1111-1111-1111-111111111112')
  and created_by = 'sample-data';

delete from custom_foods
where custom_food_id = '47111111-1111-1111-1111-111111111111'
  and created_by = 'sample-data';

delete from nutrition_targets
where user_id = '11111111-1111-1111-1111-111111111111'
  and created_by = 'sample-data';

delete from nutrition_preference_projections
where user_id = '11111111-1111-1111-1111-111111111111'
  and created_by = 'sample-data';
