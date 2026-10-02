-- ============================================================================
-- Backfill of the Nutrition Diary snapshot columns on meal items logged before
-- they existed.
-- ----------------------------------------------------------------------------
-- WHEN TO RUN
--   Once after the nutrition-service migration V18 and the V901 seed have been
--   applied to a database that already holds meals. Meals logged before then
--   carry no grams_consumed, saturated_fat_grams, water_ml, category_code or
--   vitamin and mineral rows, so the Diary shows their nutrients as unknown.
--   The script is NOT a Flyway migration, because it reads the catalogue that
--   the prod-only seed fills, and it touches rows of every user.
--
-- WHAT IT DOES
--   1. grams_consumed: the quantity times the serving's grams equivalent when
--      the item has a serving, else the quantity for g, gram and grams, else
--      the quantity times the food's density (or 1) for ml.
--   2. For items linked to a catalogue food that have grams: saturated_fat_grams,
--      category_code, and water_ml (only when the food counts toward water),
--      scaled by the portion against the nutrient profile's basis.
--   3. meal_item_nutrient_details from food_nutrient_details, scaled the same
--      way, for items that have no detail rows yet.
--
-- IDEMPOTENT
--   Every step fills only what is null or missing, never changes a value that
--   is already there, and never touches calories or macros. Running it twice
--   changes nothing the second time. Each statement is atomic on its own; to
--   make the whole script one unit run it with `psql --single-transaction`.
--
-- HOW TO RUN (connect as a role that can write the nutrition schema):
--     docker exec -i fitsync-postgres \
--       psql -U fitsync -d fitsync -v ON_ERROR_STOP=1 \
--       < backend/scripts/backfill-nutrition-snapshots.sql
-- ============================================================================

-- 1. grams_consumed
update nutrition.meal_items mi
   set grams_consumed = x.grams
  from (
        select i.meal_item_id,
               case
                   when s.grams_equivalent is not null
                       then round(i.quantity * s.grams_equivalent, 2)
                   when lower(i.unit) in ('g', 'gram', 'grams')
                       then round(i.quantity, 2)
                   when lower(i.unit) = 'ml'
                       then round(i.quantity * coalesce(f.density_grams_per_ml, 1), 2)
               end as grams
          from nutrition.meal_items i
          left join nutrition.food_servings s on s.serving_id = i.food_serving_id
          left join nutrition.food_catalog_items f on f.food_id = i.food_catalog_item_id
         where i.grams_consumed is null
       ) x
 where mi.meal_item_id = x.meal_item_id
   and x.grams is not null;

-- The portion of every catalogue-linked item against its food's nutrient profile: how many times the
-- profile's basis (for example 100 g) the item holds. A millilitre basis is reached from grams through
-- the density. This list is rebuilt on every run and dropped at the end.
create temp table snapshot_scale as
select i.meal_item_id,
       i.food_catalog_item_id as food_id,
       p.saturated_fat_grams,
       p.water_g,
       f.category_code,
       f.counts_toward_water,
       case
           when lower(p.basis_unit) in ('g', 'gram', 'grams')
               then i.grams_consumed / p.basis_quantity
           when lower(p.basis_unit) = 'ml'
               then i.grams_consumed / coalesce(f.density_grams_per_ml, 1) / p.basis_quantity
       end as factor
  from nutrition.meal_items i
  join nutrition.food_catalog_items f on f.food_id = i.food_catalog_item_id
  join lateral (
        select fp.basis_quantity, fp.basis_unit, fp.saturated_fat_grams, fp.water_g
          from nutrition.food_nutrient_profiles fp
         where fp.food_id = f.food_id
           and fp.active
           and fp.basis_quantity > 0
           and (fp.date_effective_from is null or fp.date_effective_from <= current_date)
           and (fp.date_effective_to is null or fp.date_effective_to >= current_date)
         order by fp.date_effective_from desc nulls last, fp.nutrient_profile_id
         limit 1
       ) p on true
 where i.grams_consumed is not null;

-- 2. saturated fat, water and category
update nutrition.meal_items mi
   set saturated_fat_grams = round(s.saturated_fat_grams * s.factor, 2)
  from snapshot_scale s
 where mi.meal_item_id = s.meal_item_id
   and mi.saturated_fat_grams is null
   and s.saturated_fat_grams is not null
   and s.factor is not null;

update nutrition.meal_items mi
   set water_ml = round(s.water_g * s.factor)::int
  from snapshot_scale s
 where mi.meal_item_id = s.meal_item_id
   and mi.water_ml is null
   and s.counts_toward_water
   and s.water_g is not null
   and s.factor is not null;

update nutrition.meal_items mi
   set category_code = s.category_code
  from snapshot_scale s
 where mi.meal_item_id = s.meal_item_id
   and mi.category_code is null
   and s.category_code is not null;

-- 3. vitamins and minerals
insert into nutrition.meal_item_nutrient_details
    (detail_id, meal_item_id, nutrient_code, display_name, amount, unit, sort_order,
     date_time_created, date_time_updated, created_by, updated_by, version)
select md5(s.meal_item_id::text || ':' || d.nutrient_code)::uuid,
       s.meal_item_id, d.nutrient_code, d.display_name,
       round(d.amount * s.factor, 2), d.unit, d.sort_order,
       now(), now(), 'backfill-snapshots', 'backfill-snapshots', 0
  from snapshot_scale s
  join nutrition.food_nutrient_details d on d.food_id = s.food_id
 where s.factor is not null
   and d.active
   and (d.date_effective_from is null or d.date_effective_from <= current_date)
   and (d.date_effective_to is null or d.date_effective_to >= current_date)
   and not exists (select 1
                     from nutrition.meal_item_nutrient_details x
                    where x.meal_item_id = s.meal_item_id)
on conflict do nothing;

drop table snapshot_scale;
