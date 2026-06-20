#!/usr/bin/env python3
"""
NU-12b: generate a Flyway seed migration of foods from the USDA FoodData Central
SR Legacy CSV dataset.

Usage:
    python generate_seed.py <sr_legacy_csv_dir> <output.sql>

The dataset (public domain) is the unzipped FoodData_Central_sr_legacy_food_csv_*.
Output is a single prod-only seed migration (placed under db/seed, NOT db/migration,
so it does not run in the H2 test profile). Re-runnable: deterministic UUIDs from
fdc_id, so re-generating is stable.
"""
import csv
import sys
import uuid
from pathlib import Path

# USDA nutrient internal ids (per-100g amounts in SR Legacy food_nutrient.csv)
N_ENERGY_KCAL = "1008"
N_PROTEIN = "1003"
N_CARB = "1005"
N_FAT = "1004"
N_FIBER = "1079"
N_SUGAR = "2000"
N_SODIUM = "1093"
WANTED_NUTRIENTS = {N_ENERGY_KCAL, N_PROTEIN, N_CARB, N_FAT, N_FIBER, N_SUGAR, N_SODIUM}

# USDA food_category.description -> our food_categories.code (V8 taxonomy + extensions).
# Categories not listed (Baby Foods, Branded, Quality Control) are skipped.
CATEGORY_MAP = {
    "Dairy and Egg Products": "DAIRY",          # split to EGGS by description below
    "Fats and Oils": "FATS_OILS",
    "Poultry Products": "MEAT_POULTRY",
    "Sausages and Luncheon Meats": "MEAT_POULTRY",
    "Pork Products": "MEAT_POULTRY",
    "Beef Products": "MEAT_POULTRY",
    "Lamb, Veal, and Game Products": "MEAT_POULTRY",
    "Finfish and Shellfish Products": "SEAFOOD",
    "Fruits and Fruit Juices": "FRUITS",
    "Vegetables and Vegetable Products": "VEGETABLES",
    "Nut and Seed Products": "NUTS_SEEDS",
    "Legumes and Legume Products": "LEGUMES",
    "Cereal Grains and Pasta": "GRAINS",
    "Breakfast Cereals": "GRAINS",
    "Baked Products": "GRAINS",
    "Beverages": "BEVERAGES",
    "Alcoholic Beverages": "BEVERAGES",
    "Sweets": "SWEETS",
    "Snacks": "SNACKS",
    "Soups, Sauces, and Gravies": "SOUPS_SAUCES",
    "Spices and Herbs": "SPICES",
    "Fast Foods": "PREPARED",
    "Meals, Entrees, and Side Dishes": "PREPARED",
    "Restaurant Foods": "PREPARED",
    "American Indian/Alaska Native Foods": "PREPARED",
}
# Leaf codes already present in the V8 taxonomy (do NOT re-insert these categories).
EXISTING_LEAVES = {"DAIRY", "EGGS", "FATS_OILS", "MEAT_POULTRY", "SEAFOOD", "FRUITS",
                   "VEGETABLES", "NUTS_SEEDS", "LEGUMES", "GRAINS"}
# New leaves to add under a new "OTHER" group: (code, display, slug, sort_order).
NEW_LEAVES = [
    ("BEVERAGES", "Beverages", "beverages", 1),
    ("SWEETS", "Sweets", "sweets", 2),
    ("SNACKS", "Snacks", "snacks", 3),
    ("SOUPS_SAUCES", "Soups & Sauces", "soups_sauces", 4),
    ("SPICES", "Spices & Herbs", "spices", 5),
    ("PREPARED", "Prepared & Restaurant", "prepared", 6),
]
NS = uuid.UUID("6f0a1d2e-7c34-4b51-9a8e-000000000000")
TS = "timestamp with time zone '2026-06-01 08:00:00+00'"
AUDIT = f"{TS},{TS},'usda-import','usda-import',0"


def uid(*parts):
    return str(uuid.uuid5(NS, "fitsync-usda:" + ":".join(parts)))


def sql_str(value, limit):
    if value is None:
        return "null"
    v = str(value).strip()[:limit].replace("'", "''")
    return f"'{v}'"


def num(value, scale):
    if value is None or value == "":
        return "null"
    try:
        return str(round(float(value), scale))
    except ValueError:
        return "null"


def main(csv_dir, out_path):
    csv.field_size_limit(10_000_000)
    base = Path(csv_dir)
    categories = {row["id"]: row["description"]
                  for row in csv.DictReader(open(base / "food_category.csv", encoding="utf-8"))}

    foods = {}
    for row in csv.DictReader(open(base / "food.csv", encoding="utf-8")):
        if row["data_type"] != "sr_legacy_food":
            continue
        foods[row["fdc_id"]] = {"desc": row["description"],
                                "cat": categories.get(row["food_category_id"], "")}

    # Stream the large food_nutrient.csv, keeping only wanted nutrients for known foods.
    nutr = {fid: {} for fid in foods}
    for row in csv.DictReader(open(base / "food_nutrient.csv", encoding="utf-8")):
        fid = row["fdc_id"]
        nid = row["nutrient_id"]
        if fid in nutr and nid in WANTED_NUTRIENTS:
            nutr[fid][nid] = row["amount"]

    rows = []  # (food_id, display, normalized, code, serving_id, profile_id, kcal, p, c, f, fib, sug, sod)
    used_new_leaves = set()
    for fid, food in foods.items():
        code = CATEGORY_MAP.get(food["cat"])
        if not code:
            continue
        n = nutr.get(fid, {})
        if N_ENERGY_KCAL not in n:  # require calories
            continue
        desc = food["desc"]
        if code == "DAIRY" and "egg" in desc.lower():
            code = "EGGS"
        if code not in EXISTING_LEAVES:
            used_new_leaves.add(code)
        rows.append((
            uid("food", fid), desc, desc.lower(), code,
            uid("serving", fid), uid("profile", fid),
            num(n.get(N_ENERGY_KCAL), 0), num(n.get(N_PROTEIN), 2), num(n.get(N_CARB), 2),
            num(n.get(N_FAT), 2), num(n.get(N_FIBER), 2), num(n.get(N_SUGAR), 2),
            num(n.get(N_SODIUM), 2),
        ))

    rows.sort(key=lambda r: r[1].lower())
    write_sql(out_path, rows, used_new_leaves)
    print(f"foods written: {len(rows)}; new leaf categories: {sorted(used_new_leaves)}")


def write_sql(out_path, rows, used_new_leaves):
    out = []
    out.append("-- NU-12b: USDA FoodData Central (SR Legacy, public domain) seed.")
    out.append("-- Generated by tools/usda/generate_seed.py. Prod-only (db/seed): not applied in tests.")
    out.append("")

    # Category extension: an OTHER group + the new leaves actually used.
    if used_new_leaves:
        grp = uid("category", "OTHER")
        out.append("insert into food_categories (category_id, parent_id, code, display_name, slug, path,"
                   " sort_order, active, date_time_created, date_time_updated, created_by, updated_by, version)")
        out.append("values")
        vals = [f"('{grp}',null,'OTHER','Other','other','/other/',7,true,{AUDIT})"]
        for code, display, slug, order in NEW_LEAVES:
            if code in used_new_leaves:
                vals.append(f"('{uid('category', code)}','{grp}','{code}',{sql_str(display,80)},"
                            f"'{slug}','/other/{slug}/',{order},true,{AUDIT})")
        out.append(",\n".join(vals) + ";")
        out.append("")

    def batched(table, cols, value_rows):
        for i in range(0, len(value_rows), 500):
            chunk = value_rows[i:i + 500]
            out.append(f"insert into {table} ({cols}) values")
            out.append(",\n".join(chunk) + ";")
            out.append("")

    cat_cols = ("food_id, scope, owner_user_id, source, display_name, normalized_name, category_code,"
                " brand_id, default_serving_id, verified, density_grams_per_ml, date_effective_from,"
                " date_effective_to, active, date_time_created, date_time_updated, created_by, updated_by,"
                " version, base_food_id, creation_mode")
    srv_cols = ("serving_id, food_id, label, quantity, unit, grams_equivalent, milliliters_equivalent,"
                " sort_order, default_serving, date_effective_from, date_effective_to, active,"
                " date_time_created, date_time_updated, created_by, updated_by, version")
    prof_cols = ("nutrient_profile_id, food_id, basis_quantity, basis_unit, calories, protein_grams,"
                 " carbs_grams, fat_grams, saturated_fat_grams, trans_fat_grams, fiber_grams, sugar_grams,"
                 " sodium_mg, potassium_mg, cholesterol_mg, date_effective_from, date_effective_to, active,"
                 " date_time_created, date_time_updated, created_by, updated_by, version")

    cat_v, srv_v, prof_v = [], [], []
    for (food_id, display, norm, code, serving_id, profile_id,
         kcal, p, c, f, fib, sug, sod) in rows:
        cat_v.append(f"('{food_id}','GLOBAL',null,'IMPORTED',{sql_str(display,120)},{sql_str(norm,120)},"
                     f"'{code}',null,'{serving_id}',true,null,date '2020-01-01',null,true,{AUDIT},null,'MANUAL')")
        srv_v.append(f"('{serving_id}','{food_id}','100 g',100.00,'g',100.00,null,1,true,date '2020-01-01',"
                     f"null,true,{AUDIT})")
        prof_v.append(f"('{profile_id}','{food_id}',100.00,'g',{kcal},{p},{c},{f},null,null,{fib},{sug},"
                      f"{sod},null,null,date '2020-01-01',null,true,{AUDIT})")

    batched("food_catalog_items", cat_cols, cat_v)
    batched("food_servings", srv_cols, srv_v)
    batched("food_nutrient_profiles", prof_cols, prof_v)

    Path(out_path).write_text("\n".join(out), encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
