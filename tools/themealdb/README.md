# TheMealDB system recipes (seed V902)

`import_recipes.py` turns the recipes of TheMealDB into FitSync system recipes (scope SYSTEM, source THEMEALDB)
whose ingredients are linked to the USDA foods that `tools/usda/generate_seed.py` wrote into V900. The output is
the prod-only seed `nutrition-service/src/main/resources/db/seed/V902__themealdb_system_recipes.sql`. The JSON
and the USDA CSV files are not committed; only the script, the hand-checked ingredient map, the report and the
generated SQL are.

TheMealDB is used under its free API (key `1`) for an educational project. Images are linked, not copied.

## 1. Fetch the TheMealDB data

Three API calls, all with the public test key `1`:

```
https://www.themealdb.com/api/json/v1/1/categories.php            all categories
https://www.themealdb.com/api/json/v1/1/filter.php?c=<category>   the ids of the meals of one category
https://www.themealdb.com/api/json/v1/1/lookup.php?i=<idMeal>     one full meal (ingredients, measures, steps)
```

Call `categories.php` once, `filter.php?c=` for every category, and `lookup.php?i=` for every id found. Save the
full meals as one JSON list in `meals.json` inside a directory of your choice:

```python
import json, time, urllib.parse, urllib.request
base = "https://www.themealdb.com/api/json/v1/1/"
get = lambda p: json.load(urllib.request.urlopen(base + p, timeout=30))
ids = {}
for c in get("categories.php")["categories"]:
    for m in get("filter.php?c=" + urllib.parse.quote(c["strCategory"]))["meals"] or []:
        ids[m["idMeal"]] = None
    time.sleep(0.2)
meals = []
for i in ids:
    meals.append(get("lookup.php?i=" + i)["meals"][0])
    time.sleep(0.15)
json.dump(meals, open("mealdb/meals.json", "w", encoding="utf-8"))
```

The importer only reads `meals.json` (793 meals at the time of writing).

## 2. Get the USDA data

The unzipped SR Legacy CSV set, the same one used for V900 (see `tools/usda/README.md`): `food.csv`,
`food_portion.csv`, `measure_unit.csv`, `food_category.csv`, `food_nutrient.csv`.

## 3. Run

```bash
python tools/themealdb/import_recipes.py <mealdb_dir> <usda_dir> \
  nutrition-service/src/main/resources/db/seed/V902__themealdb_system_recipes.sql \
  --report tools/themealdb/IMPORT_REPORT.md

python tools/themealdb/import_recipes.py --self-test    # measure parser and step splitter checks
```

The result is deterministic: running it twice gives a byte-identical file. The script also checks that the
quotes and parentheses of every statement balance. The V902 migration needs the columns and the
`recipe_steps` table of the recipe library migration (`V21__create_recipe_library.sql`), so it
cannot run against a database before that migration.

## How it decides

- **Ingredient to food.** `ingredient_map.csv` (columns `mealdb_name`, `fdc_id`, `note`) wins. It was checked by
  hand against `food.csv` and covers the most frequent ingredients and many of the rarer ones; an `fdc_id` of
  `NONE` marks an ingredient with no sensible USDA match, which skips every recipe that uses it. Names that are
  not in the map go through an automatic scorer (plural-insensitive word match, preferring "raw" and the plain
  form, avoiding baby food, restaurant and brand items).
- **Measure to grams.** Weights and volumes are converted directly; cups, spoons and counts ("2 large",
  "3 cloves") use the gram weight of the matching row in `food_portion.csv`; ml uses the density of a cup
  portion, else 1 g/ml. Ranges take the lower number. Pinch 0.5 g, dash 0.6 g, garnish 2 g, handful 30 g.
  "To taste", "to serve" and an empty measure are 0 g for salt, pepper, spices, herbs and water only. A bare
  preparation note ("Chopped") is a 2 g garnish for herbs, spices, chilli, garlic and similar flavourings.
  "Juice of N" for lemon, lime or orange is N times the USDA juice yield of one fruit. For any other ingredient
  an unreadable or vague measure skips the whole recipe.
- **Servings.** TheMealDB has none: 4 for mains, 6 for Dessert, 4 for Side and Starter, 2 for Breakfast.
- **Steps.** `strInstructions` is split at line breaks, empty lines and "STEP 1" style labels are dropped,
  leading numbering is stripped, and a single long paragraph is split into steps of at most three sentences.
  The text is kept as written.
- **Sanity filter.** A recipe whose energy per serving is below 80 or above 1500 kcal is left out and listed in
  `IMPORT_REPORT.md`.

To add or fix a mapping, edit `ingredient_map.csv` (lower-case `mealdb_name`) and run the importer again.
