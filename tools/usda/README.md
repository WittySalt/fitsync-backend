# USDA food catalog seed (NU-12b) and micronutrient seed (Diary batch 1)

`generate_seed.py` writes two prod-only Flyway seeds under `nutrition-service/src/main/resources/db/seed/`
from the USDA FoodData Central **SR Legacy** dataset (public domain):

| Seed | What it holds | Command |
| --- | --- | --- |
| `V900__seed_usda_foods.sql` | The foods: catalog rows, a "100 g" serving and a per-100 g nutrient profile each | `python tools/usda/generate_seed.py <csv_dir> <V900.sql>` |
| `V901__usda_micronutrients.sql` | Vitamins, minerals, water, the drink flag and the plate group of the categories V900 adds | `python tools/usda/generate_seed.py --micronutrients <csv_dir> <V901.sql>` |
| `V904__usda_food_portions.sql` | The household measures (1 cup, 1 slice, 6 pieces) of the same foods | `python tools/usda/generate_seed.py --portions <csv_dir> <V904.sql>` |

## Regenerate

```bash
# 1. Download + unzip the SR Legacy CSV bundle (~6 MB, no API key needed)
curl -o sr.zip "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip"
unzip sr.zip -d sr

# 2. The foods (V900)
python tools/usda/generate_seed.py \
  sr/FoodData_Central_sr_legacy_food_csv_2018-04 \
  nutrition-service/src/main/resources/db/seed/V900__seed_usda_foods.sql

# 3. The micronutrients (V901), for the same foods
python tools/usda/generate_seed.py --micronutrients \
  sr/FoodData_Central_sr_legacy_food_csv_2018-04 \
  nutrition-service/src/main/resources/db/seed/V901__usda_micronutrients.sql

# 4. The household measures (V904), for the same foods
python tools/usda/generate_seed.py --portions   sr/FoodData_Central_sr_legacy_food_csv_2018-04   nutrition-service/src/main/resources/db/seed/V904__usda_food_portions.sql
```

The dataset CSVs are **not** committed (only the generated SQL is). All outputs are deterministic: ids are
`uuid5` values of the USDA `fdc_id`, so generating again gives the same files, and V901 lands on exactly the rows
V900 wrote. Current V900 output: 7,448 foods (those with calories and a mappable category).

## What V901 does

For every food V900 writes (the same selection, so the same ids):

- fills `water_g`, `saturated_fat_grams`, `trans_fat_grams`, `potassium_mg` and `cholesterol_mg` on the
  food's nutrient profile, leaving a column alone when USDA has no value for the food;
- inserts one `food_nutrient_details` row per nutrient of the `nutrients` table that has a value, skipping
  missing values, zeros and amounts too small for `numeric(10, 2)`;
- sets `food_catalog_items.counts_toward_water` for drinks: non-alcoholic foods of the `BEVERAGES` category,
  liquid milks (`Milk, ...`) and plant milks (soymilk, almond, rice, oat, cashew and hemp milk). A food with more
  than 0.5 g of alcohol per 100 g never counts, and neither does one with less than 50 g of water per 100 g, which
  keeps drink powders and dry or condensed milk out;
- sets `food_categories.food_group` for the categories V900 adds (`BEVERAGES` is `DRINKS`, `SWEETS` and `SNACKS`
  are `SWEETS`), because a fresh database runs V18 before V900.

The nutrients to write are read from the `insert into nutrients` statement of the V18 migration (code, display
name, unit and USDA nutrient number), so that list is kept in one place. The script passes `--nutrients-sql` if
the migration lives somewhere else. The USDA number (`nutrient_nbr`, for example 401 for vitamin C) is turned into
the id that `food_nutrient.csv` uses through `nutrient.csv`, and the script stops if a unit in V18 does not match
the unit USDA gives.

## What V904 does

For every food V900 writes, one `food_servings` row per household measure in `food_portion.csv`:

- SR Legacy marks nearly every portion as measure unit 9999 ("undetermined") and puts the words in `modifier`, so
  the label is the amount and those words ("1 cup, chopped", "6 pieces", "1 sandwich"). When a portion does name a
  unit in `measure_unit.csv`, that name is used. A trailing weight ("item 7.6 oz") is dropped from the label, and a
  portion with no gram weight, a repeated label or a label over 80 characters is skipped.
- `quantity` is the amount, `unit` the first word of the label, `grams_equivalent` the weight of the whole portion
  (`quantity` of that unit), and `sort_order` the USDA `seq_num` plus one, so the "100 g" row stays first.
- The portion with the lowest `seq_num` becomes the default: its row gets `default_serving = true`, the food's
  "100 g" row loses it, and `food_catalog_items.default_serving_id` points at the new row. A food with no usable
  portion keeps "100 g" as its default.
- Ids are `uuid5` values of the USDA portion id. Current output: 13,531 rows for 7,183 of the 7,448 foods.

## Brands (V25 and V905)

V900 stores the brand inside the name ("McDONALD'S, BIG MAC"). `V25__brand_usda_foods` (a Java migration in
`db/migration`, rules in `UsdaBrandRules`) moves a listed chain or maker into `brand_id`, rewrites the display name
to the rest in title case and keeps the full original in `normalized_name`. Flyway runs V25 before V900 on a new
database, so V25 only has an effect on a database that already holds the V900 foods. On a new database the same
rules run again as `V905__brand_usda_foods` in `db/seed`, after V900 to V904. Regenerating V900 does not undo
this: V900 keeps writing the USDA names and V25 or V905 rewrite them, in that order. New brands go into the list in
`UsdaBrandRules`; the next run of V905 does not repeat on foods that already have a brand, so a new brand needs its own
migration that calls `UsdaBrandBackfill`.

## Try it without the real data

`test-fixture/` is a handful of made-up rows in the SR Legacy column layout (`food.csv`, `food_nutrient.csv`,
`nutrient.csv`, `food_category.csv`) that covers the rules above: a cola, a banana, whole and dry milk, a beer, a
soymilk, beef, spinach, and foods that must be skipped.

```bash
python tools/usda/generate_seed.py tools/usda/test-fixture /tmp/fx_v900.sql
python tools/usda/generate_seed.py --micronutrients tools/usda/test-fixture /tmp/fx_v901.sql
```

## Why a separate `db/seed` location

`spring.flyway.locations` includes `classpath:db/seed` in all real profiles but **not** in the
`test` profile (`application-test.yml` overrides it to `classpath:db/migration` only), so the seed rows are
applied on deploy to the online DB but never slow the H2 test suite. Flyway shares one version namespace across
both locations, which is why the seeds start at V900 and the schema migrations stay below it.
