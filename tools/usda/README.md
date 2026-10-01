# USDA food catalog seed (NU-12b) and micronutrient seed (Diary batch 1)

`generate_seed.py` writes two prod-only Flyway seeds under `nutrition-service/src/main/resources/db/seed/`
from the USDA FoodData Central **SR Legacy** dataset (public domain):

| Seed | What it holds | Command |
| --- | --- | --- |
| `V900__seed_usda_foods.sql` | The foods: catalog rows, a "100 g" serving and a per-100 g nutrient profile each | `python tools/usda/generate_seed.py <csv_dir> <V900.sql>` |
| `V901__usda_micronutrients.sql` | Vitamins, minerals, water, the drink flag and the plate group of the categories V900 adds | `python tools/usda/generate_seed.py --micronutrients <csv_dir> <V901.sql>` |

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
```

The dataset CSVs are **not** committed (only the generated SQL is). Both outputs are deterministic: ids are
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
