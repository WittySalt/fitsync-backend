# Food Data — Clonability & Validation Runbook

> **Status:** Operational runbook, written while recommendation-service was developed on its own branch.
> Answers: *can the open food datasets be cloned locally, and is the data good enough to build on?*
> Companion to `nutrition-data-plan.md`.

---

## 1. Feasibility — measured sizes (probed 2026-06, verify before you pull)

| Artifact | Size | Clonable locally? |
|---|---|---|
| **USDA SR Legacy** (CSV zip) | **~5.8 MB** | ✅ trivially — bulk-load it |
| OFF **CSV** (`.csv.gz`, tab-separated, 211 cols) | **~1.19 GB** gz (~9 GB raw) | ⚠️ heavy but possible; flattened columns |
| OFF **Parquet** (Hugging Face `openfoodfacts/product-database`) | **~7.6 GB** | ✅ for analytics via DuckDB (query in place — no DB load) |
| OFF **JSONL** (`.jsonl.gz`) | **~11.5 GB** gz | ❌ impractical for a capstone |
| OFF **MongoDB dump** (`.gz`) | **~14 GB** gz | ❌ don't |

**Verdict.** USDA SR Legacy: clone it, it's tiny and public-domain. OFF: **do not full-clone the DB.**
For *validation/analytics* use the Parquet + DuckDB (query the file directly). For *production* use the
**on-demand barcode cache** (`nutrition-data-plan.md` §3B) — never bulk-load OFF.

Pre-flight any pull with a HEAD request so you know the size before committing disk/bandwidth:
```sh
curl -sIL "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz" \
  | grep -i 'content-length\|location'
```

---

## 2. Data-quality reality — measured, not assumed

A 25,000-row head sample of the OFF CSV (streamed, not full download) gave these fill rates:

| Field | Fill % | Field | Fill % |
|---|---|---|---|
| code (barcode) | 100% | energy-kcal_100g | **27%** |
| product_name | 86% | proteins_100g | **26%** |
| categories_tags | **37%** | fat / carbs_100g | **26%** |
| food_groups_tags | **27%** | fiber_100g | 16% |
| nova_group | **28%** | salt / sodium_100g | 19% |
| nutriscore_grade (real A–E) | **26%** | serving_size | 45% |
| pnns_groups_2 (real group) | **26%** | additives / allergens | 17% / 7% |

**Key findings:**
1. **OFF is ~75% empty for the fields we care about.** Core macros, NOVA, Nutri-Score, and food-group
   tags all sit around **26–28%** populated. Only barcode/name/country are dense.
2. **Beware placeholder inflation.** `nutriscore_grade` and `pnns_groups_2` looked 98% filled, but the
   bulk is the literal string `unknown` / `not-applicable`. **Real** values are 26%. Always exclude
   `unknown`/`not-applicable`/empty — never count placeholders as data.
3. **OFF's own food grouping is weak** (`food_groups_tags` 27%, `categories_tags` 37%). The food-group
   mapping layer (`nutrition-data-plan.md` §5) + USDA categories must carry the load; expect a large
   `OTHER` bucket from OFF alone.

> ⚠️ **Sampling caveat:** this is a *head-of-file* sample (sorted by barcode), which skews toward older /
> sparser entries — treat the numbers as an order-of-magnitude smoke test, not the final rate. Run §3 for
> an unbiased, market-filtered figure.

**Implications for the plan (reinforced by evidence):**
- Lean on **USDA as the clean spine**; treat OFF as a sparse supplement.
- **Threshold hard** at import: reject records missing core macros; store presence/`dataCompleteness` flags.
- The **on-demand-cache** strategy is now doubly justified — scanned products skew toward *popular,
  well-populated* items, so the cached subset's quality is far better than the 26% global average, and
  you avoid drowning in empty rows from a bulk import.

---

## 3. Proper local validation (run this — unbiased)

The §2 numbers are a head sample. For real per-market fill rates, query the Parquet with **DuckDB** (no
database load; reads the 7.6 GB file directly, streaming):

```sh
# 1. one-time: get DuckDB (single binary) and the parquet
#    (or point DuckDB at the HF URL directly if disk is tight)
curl -L -o food.parquet \
  "https://huggingface.co/datasets/openfoodfacts/product-database/resolve/main/food.parquet"

# 2. unbiased fill rates, filtered to your target market (example: countries containing 'united-states')
duckdb -c "
  SELECT
    count(*) AS n,
    100.0*count(energy_kcal_100g)/count(*)            AS pct_kcal,
    100.0*count(proteins_100g)/count(*)               AS pct_protein,
    100.0*count(NULLIF(nova_group,''))/count(*)       AS pct_nova,
    100.0*count(*) FILTER (WHERE nutriscore_grade IN ('a','b','c','d','e'))/count(*) AS pct_nutriscore,
    100.0*count(NULLIF(food_groups_tags,''))/count(*) AS pct_foodgroups
  FROM 'food.parquet'
  WHERE list_contains(countries_tags, 'en:united-states');   -- adjust to your market
"
```
(Column names differ slightly between the CSV and Parquet schemas — run `DESCRIBE SELECT * FROM
'food.parquet' LIMIT 1` first and adjust.)

**USDA SR Legacy** (the clean spine) — confirm clonable + inspect in seconds:
```sh
curl -L -o sr.zip "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip"
unzip -o sr.zip -d sr/
duckdb -c "SELECT count(*) FROM read_csv_auto('sr/food.csv');"   # ~7.8k generic foods, dense macros+micros
```

---

## 4. Go / no-go rubric

| Check | Threshold | If it fails |
|---|---|---|
| USDA SR Legacy loads & has macros+micros | yes | unlikely (public-domain, stable) — re-confirm current filename |
| OFF Parquet readable via DuckDB | yes | fall back to CSV gz (1.2 GB) |
| OFF macro fill-rate **in your market** | ≥ ~50% on popular subset | rely more on USDA + on-demand cache; don't bulk-import |
| OFF `food_groups`/category usable | any | expect low → build the §5 mapping layer; large OTHER bucket OK |

**Bottom line:** *USDA = clone & bulk-load now. OFF = don't clone the DB; validate via Parquet/DuckDB,
serve via on-demand barcode cache.* The data is good enough to build on **provided you threshold and lean
on USDA** — exactly what `nutrition-data-plan.md` already prescribes; this run confirms it with numbers.
