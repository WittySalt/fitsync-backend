-- NU-17: seed external_food_category_map (FD-2). Maps open-data source categories onto the EXISTING
-- internal food_categories leaf codes (see V8). This is the food-group layer open data does not provide.
--
-- Deliberately partial: only confident food-group mappings are seeded. Genuinely mixed/ambiguous source
-- categories (Sweets, Beverages, Snacks, Fast Foods, Soups, Spices, Baby/Restaurant Foods, etc.) are left
-- UNMAPPED on purpose — the importer counts them as unmapped so the gap is visible and the map can be
-- extended, rather than mis-classifying them and polluting analytics. There is intentionally no internal
-- SWEETS/BEVERAGE/SNACK leaf yet (would be a taxonomy change, FD-8 / future).
--
-- internal_category_code values are leaf codes from food_categories; the dietary group is derivable via
-- the category's path (e.g. MEAT_POULTRY -> /proteins/meat_poultry/). See nutrition-data-plan.md §5.

-- USDA FoodData Central — SR Legacy food categories (exact category names) -----------------------------
insert into external_food_category_map (
    mapping_id, source_dataset, source_category_code, internal_category_code,
    date_time_created, date_time_updated, created_by, updated_by, version
) values
('ca810001-0000-0000-0000-000000000000','USDA_SR','Dairy and Egg Products','DAIRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca810002-0000-0000-0000-000000000000','USDA_SR','Poultry Products','MEAT_POULTRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca810003-0000-0000-0000-000000000000','USDA_SR','Sausages and Luncheon Meats','MEAT_POULTRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca810004-0000-0000-0000-000000000000','USDA_SR','Beef Products','MEAT_POULTRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca810005-0000-0000-0000-000000000000','USDA_SR','Pork Products','MEAT_POULTRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca810006-0000-0000-0000-000000000000','USDA_SR','Lamb, Veal, and Game Products','MEAT_POULTRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca810007-0000-0000-0000-000000000000','USDA_SR','Finfish and Shellfish Products','SEAFOOD',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca810008-0000-0000-0000-000000000000','USDA_SR','Vegetables and Vegetable Products','VEGETABLES',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca810009-0000-0000-0000-000000000000','USDA_SR','Fruits and Fruit Juices','FRUITS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca81000a-0000-0000-0000-000000000000','USDA_SR','Legumes and Legume Products','LEGUMES',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca81000b-0000-0000-0000-000000000000','USDA_SR','Nut and Seed Products','NUTS_SEEDS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca81000c-0000-0000-0000-000000000000','USDA_SR','Fats and Oils','FATS_OILS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca81000d-0000-0000-0000-000000000000','USDA_SR','Cereal Grains and Pasta','GRAINS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca81000e-0000-0000-0000-000000000000','USDA_SR','Breakfast Cereals','GRAINS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca81000f-0000-0000-0000-000000000000','USDA_SR','Baked Products','GRAINS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0);

-- Open Food Facts — category tags (exact tag string) --------------------------------------------------
insert into external_food_category_map (
    mapping_id, source_dataset, source_category_code, internal_category_code,
    date_time_created, date_time_updated, created_by, updated_by, version
) values
('ca820001-0000-0000-0000-000000000000','OFF','en:meats','MEAT_POULTRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820002-0000-0000-0000-000000000000','OFF','en:poultry','MEAT_POULTRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820003-0000-0000-0000-000000000000','OFF','en:beef','MEAT_POULTRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820004-0000-0000-0000-000000000000','OFF','en:pork','MEAT_POULTRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820005-0000-0000-0000-000000000000','OFF','en:seafood','SEAFOOD',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820006-0000-0000-0000-000000000000','OFF','en:fishes','SEAFOOD',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820007-0000-0000-0000-000000000000','OFF','en:eggs','EGGS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820008-0000-0000-0000-000000000000','OFF','en:dairies','DAIRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820009-0000-0000-0000-000000000000','OFF','en:cheeses','DAIRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca82000a-0000-0000-0000-000000000000','OFF','en:yogurts','DAIRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca82000b-0000-0000-0000-000000000000','OFF','en:milks','DAIRY',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca82000c-0000-0000-0000-000000000000','OFF','en:vegetables','VEGETABLES',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca82000d-0000-0000-0000-000000000000','OFF','en:fruits','FRUITS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca82000e-0000-0000-0000-000000000000','OFF','en:legumes','LEGUMES',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca82000f-0000-0000-0000-000000000000','OFF','en:nuts','NUTS_SEEDS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820010-0000-0000-0000-000000000000','OFF','en:seeds','NUTS_SEEDS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820011-0000-0000-0000-000000000000','OFF','en:fats','FATS_OILS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820012-0000-0000-0000-000000000000','OFF','en:vegetable-oils','FATS_OILS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820013-0000-0000-0000-000000000000','OFF','en:cereals','GRAINS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820014-0000-0000-0000-000000000000','OFF','en:breads','GRAINS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820015-0000-0000-0000-000000000000','OFF','en:pastas','GRAINS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820016-0000-0000-0000-000000000000','OFF','en:rice','GRAINS',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca820017-0000-0000-0000-000000000000','OFF','en:tofu','PLANT_PROTEIN',timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0);
