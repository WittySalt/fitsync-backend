# USDA food catalog seed (NU-12b)

Generates the prod-only Flyway seed `nutrition-service/src/main/resources/db/seed/V13__seed_usda_foods.sql`
from the USDA FoodData Central **SR Legacy** dataset (public domain).

## Regenerate

```bash
# 1. Download + unzip the SR Legacy CSV bundle (~6 MB, no API key needed)
curl -o sr.zip "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip"
unzip sr.zip -d sr

# 2. Generate the seed migration
python tools/usda/generate_seed.py \
  sr/FoodData_Central_sr_legacy_food_csv_2018-04 \
  nutrition-service/src/main/resources/db/seed/V13__seed_usda_foods.sql
```

The dataset CSVs are **not** committed (only the generated SQL is). Current output: ~7,448 foods
(those with calories + a mappable category), each with a per-100g nutrient profile and a "100 g"
default serving, mapped to the V8 category taxonomy (+ a new `OTHER` group for beverages/sweets/
snacks/soups-sauces/spices/prepared).

## Why a separate `db/seed` location

`spring.flyway.locations` includes `classpath:db/seed` in all real profiles but **not** in the
`test` profile (`application-test.yml` overrides it to `classpath:db/migration` only), so the ~7.4k
rows are applied on deploy to the online DB but never slow the H2 test suite. Flyway still versions
it as V13; tests simply don't resolve that location.
