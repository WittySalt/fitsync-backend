#!/usr/bin/env python3
"""
Generate Flyway seed migrations of foods from the USDA FoodData Central SR Legacy CSV dataset.

Two outputs, both prod-only (placed under db/seed, NOT db/migration, so they do not run in the H2 test
profile):

  NU-12b  the foods themselves (V900):
      python generate_seed.py <sr_legacy_csv_dir> <V900.sql>

  Diary batch 1  the vitamins, minerals, water and drink flags for the same foods (V901):
      python generate_seed.py --micronutrients <sr_legacy_csv_dir> <V901.sql>

The dataset (public domain) is the unzipped FoodData_Central_sr_legacy_food_csv_*. Both outputs are
deterministic: ids are uuid5 values of the fdc_id, so V901 updates and inserts by the very ids V900 wrote, and
generating again gives the same files.

V901 needs to know which nutrients to write. It reads them from the `nutrients` insert in the V18 migration
(code, display name, unit, USDA nutrient number), so the list lives in one place. The USDA nutrient number
(nutrient_nbr, for example 401 for vitamin C) is turned into the id the food_nutrient.csv file uses through
nutrient.csv.
"""
import argparse
import csv
import re
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

# --- Diary batch 1 (V901) -------------------------------------------------------------------------
# The plate group of the categories that the V900 seed adds under OTHER. V18 maps the categories that
# already exist when it runs, but a fresh database runs V18 before V900, so V901 sets these as well.
NEW_LEAF_FOOD_GROUPS = {"BEVERAGES": "DRINKS", "SWEETS": "SWEETS", "SNACKS": "SWEETS"}
# USDA nutrient numbers (nutrient_nbr) of the values that live on food_nutrient_profiles.
NBR_WATER = "255"
NBR_SATURATED_FAT = "606"
NBR_TRANS_FAT = "605"
NBR_POTASSIUM = "306"
NBR_CHOLESTEROL = "601"
NBR_ALCOHOL = "221"
# A food counts toward water when it is a drink: not alcoholic, and mostly water, so that a drink powder or a
# concentrate does not. 0.5 g of alcohol per 100 g is the contract's line between alcoholic and not.
ALCOHOL_LIMIT_G = 0.5
MIN_WATER_G = 50.0
PLANT_MILK = re.compile(r"(almond|rice|oat|soy|cashew|hemp) milk")
V18_NUTRIENT_ROW = re.compile(
    r"\('([A-Z0-9_]+)','([^']+)','([^']+)','(VITAMIN|MINERAL|OTHER)',(\d+),(\d+),(true|false),")
USDA_UNITS = {"G": "g", "MG": "mg", "UG": "µg"}
DEFAULT_V18 = (Path(__file__).resolve().parent.parent.parent
               / "nutrition-service/src/main/resources/db/migration/V18__nutrition_diary_reference_data.sql")


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


def select_foods(base, wanted_nutrient_ids):
    """
    The foods both seeds are about, with their amounts of the wanted nutrients (per 100 g).

    Returns (rows, used_new_leaves, amounts). Each row is (fdc_id, description, category code) and the rows
    are in the order V900 writes them. amounts maps fdc_id to {nutrient id: amount string}.
    """
    csv.field_size_limit(10_000_000)
    categories = {row["id"]: row["description"]
                  for row in csv.DictReader(open(base / "food_category.csv", encoding="utf-8"))}

    foods = {}
    for row in csv.DictReader(open(base / "food.csv", encoding="utf-8")):
        if row["data_type"] != "sr_legacy_food":
            continue
        foods[row["fdc_id"]] = {"desc": row["description"],
                                "cat": categories.get(row["food_category_id"], "")}

    # Stream the large food_nutrient.csv, keeping only wanted nutrients for known foods.
    wanted = set(wanted_nutrient_ids) | WANTED_NUTRIENTS
    nutr = {fid: {} for fid in foods}
    for row in csv.DictReader(open(base / "food_nutrient.csv", encoding="utf-8")):
        fid = row["fdc_id"]
        nid = row["nutrient_id"]
        if fid in nutr and nid in wanted:
            nutr[fid][nid] = row["amount"]

    selected = []  # (fdc_id, description, category code)
    used_new_leaves = set()
    for fid, food in foods.items():
        code = CATEGORY_MAP.get(food["cat"])
        if not code:
            continue
        if N_ENERGY_KCAL not in nutr.get(fid, {}):  # require calories
            continue
        desc = food["desc"]
        if code == "DAIRY" and "egg" in desc.lower():
            code = "EGGS"
        if code not in EXISTING_LEAVES:
            used_new_leaves.add(code)
        selected.append((fid, desc, code))
    selected.sort(key=lambda r: r[1].lower())
    return selected, used_new_leaves, nutr


def main(csv_dir, out_path):
    base = Path(csv_dir)
    selected, used_new_leaves, nutr = select_foods(base, set())
    rows = []  # (food_id, display, normalized, code, serving_id, profile_id, kcal, p, c, f, fib, sug, sod)
    for fid, desc, code in selected:
        n = nutr[fid]
        rows.append((
            uid("food", fid), desc, desc.lower(), code,
            uid("serving", fid), uid("profile", fid),
            num(n.get(N_ENERGY_KCAL), 0), num(n.get(N_PROTEIN), 2), num(n.get(N_CARB), 2),
            num(n.get(N_FAT), 2), num(n.get(N_FIBER), 2), num(n.get(N_SUGAR), 2),
            num(n.get(N_SODIUM), 2),
        ))

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


# --- V901 -----------------------------------------------------------------------------------------

def read_nutrients(v18_path):
    """The rows of the `nutrients` insert in V18: code, display name, unit, USDA number, sort order."""
    text = Path(v18_path).read_text(encoding="utf-8")
    start = text.index("insert into nutrients")
    end = text.index(";", start)
    nutrients = []
    for m in V18_NUTRIENT_ROW.finditer(text[start:end]):
        code, name, unit, _kind, nbr, sort_order, _shown = m.groups()
        nutrients.append({"code": code, "name": name, "unit": unit, "nbr": nbr, "sort": int(sort_order)})
    if not nutrients:
        raise SystemExit(f"no nutrient rows found in {v18_path}")
    return nutrients


def read_nutrient_ids(base):
    """nutrient_nbr -> (id, unit) from nutrient.csv, the bridge between the USDA number and the file's id."""
    ids = {}
    for row in csv.DictReader(open(base / "nutrient.csv", encoding="utf-8")):
        nbr = row["nutrient_nbr"]
        if not nbr:
            continue  # a nutrient with no USDA number cannot be matched by number
        if nbr in ids:
            raise SystemExit(f"nutrient number {nbr} is on more than one row of nutrient.csv")
        ids[nbr] = (row["id"], USDA_UNITS.get(row["unit_name"], row["unit_name"]))
    return ids


def counts_toward_water(code, desc, water_g, alcohol_g):
    """The contract rule: non-alcoholic BEVERAGES, liquid milks and plant milks, as long as they are mostly water."""
    if alcohol_g > ALCOHOL_LIMIT_G:
        return False
    if water_g is None or water_g < MIN_WATER_G:
        return False
    d = desc.lower()
    if code == "BEVERAGES":
        return True
    if d.startswith("milk,") or d.startswith("milk substitutes"):
        return True
    return "soymilk" in d or PLANT_MILK.search(d) is not None


def main_micronutrients(csv_dir, out_path, v18_path):
    base = Path(csv_dir)
    nutrients = read_nutrients(v18_path)
    id_by_nbr = read_nutrient_ids(base)

    columns = []  # (nutrient row, usda id)
    for nutrient in nutrients:
        if nutrient["nbr"] not in id_by_nbr:
            raise SystemExit(f"USDA nutrient number {nutrient['nbr']} ({nutrient['code']}) is not in nutrient.csv")
        usda_id, usda_unit = id_by_nbr[nutrient["nbr"]]
        if usda_unit != nutrient["unit"]:
            raise SystemExit(f"{nutrient['code']} is {nutrient['unit']} in V18 but {usda_unit} in nutrient.csv")
        columns.append((nutrient, usda_id))
    profile_ids = {name: id_by_nbr[nbr][0] for name, nbr in (
        ("water", NBR_WATER), ("sat", NBR_SATURATED_FAT), ("trans", NBR_TRANS_FAT),
        ("potassium", NBR_POTASSIUM), ("cholesterol", NBR_CHOLESTEROL), ("alcohol", NBR_ALCOHOL))}

    wanted = {usda_id for _, usda_id in columns} | set(profile_ids.values())
    selected, _used_new_leaves, nutr = select_foods(base, wanted)

    out = []
    out.append("-- Nutrition Diary batch 1: vitamins, minerals, water and drink flags for the USDA foods of V900.")
    out.append("-- Generated by tools/usda/generate_seed.py --micronutrients. Prod-only (db/seed): not applied in tests.")
    out.append("-- The ids are the uuid5 values V900 used, so every statement lands on the row V900 wrote.")
    out.append("")

    # Categories V900 added under OTHER get their plate group (V18 cannot, on a database that runs V18 first).
    out.append("-- Plate group of the categories V900 adds.")
    for code, group in NEW_LEAF_FOOD_GROUPS.items():
        out.append(f"update food_categories set food_group = '{group}' where code = '{code}';")
    out.append("")

    out.append("-- Profile columns V900 left empty.")
    profile_updates = 0
    for fid, desc, code in selected:
        n = nutr[fid]
        assignments = []
        for column, key in (("water_g", "water"), ("saturated_fat_grams", "sat"), ("trans_fat_grams", "trans"),
                            ("potassium_mg", "potassium"), ("cholesterol_mg", "cholesterol")):
            value = num(n.get(profile_ids[key]), 2)
            if value != "null":
                assignments.append(f"{column} = {value}")
        if assignments:
            out.append(f"update food_nutrient_profiles set {', '.join(assignments)} "
                       f"where nutrient_profile_id = '{uid('profile', fid)}';")
            profile_updates += 1
    out.append("")

    out.append("-- Foods whose drink counts toward water.")
    water_ids = []
    for fid, desc, code in selected:
        n = nutr[fid]
        water = to_float(n.get(profile_ids["water"]))
        alcohol = to_float(n.get(profile_ids["alcohol"])) or 0.0
        if counts_toward_water(code, desc, water, alcohol):
            water_ids.append(uid("food", fid))
    for i in range(0, len(water_ids), 200):
        ids = ",".join(f"'{food_id}'" for food_id in water_ids[i:i + 200])
        out.append(f"update food_catalog_items set counts_toward_water = true where food_id in ({ids});")
    out.append("")

    out.append("-- One row per food and nutrient with a value, from the per-100 g USDA amounts. Display name, unit and")
    out.append("-- sort order come from the nutrients table that V18 created.")
    detail_values = []
    for fid, desc, code in selected:
        n = nutr[fid]
        for nutrient, usda_id in columns:
            amount = to_float(n.get(usda_id))
            if amount is None or round(amount, 2) <= 0:
                continue  # missing, zero, or too small for numeric(10, 2)
            detail_values.append(f"('{uid('detail', fid, nutrient['code'])}','{uid('food', fid)}',"
                                 f"'{nutrient['code']}',{round(amount, 2)})")
    for i in range(0, len(detail_values), 1000):
        out.append("insert into food_nutrient_details (detail_id, food_id, nutrient_code, display_name, amount,"
                   " unit, sort_order, date_effective_from, date_effective_to, active, date_time_created,"
                   " date_time_updated, created_by, updated_by, version)")
        out.append("select cast(v.detail_id as uuid), cast(v.food_id as uuid), v.nutrient_code, n.display_name,"
                   " v.amount, n.unit, n.sort_order, date '2020-01-01', null, true,"
                   f" {TS}, {TS}, 'usda-import', 'usda-import', 0")
        out.append("from (values")
        out.append(",\n".join(detail_values[i:i + 1000]))
        out.append(") as v(detail_id, food_id, nutrient_code, amount)")
        out.append("join nutrients n on n.code = v.nutrient_code;")
        out.append("")

    Path(out_path).write_text("\n".join(out), encoding="utf-8")
    print(f"foods matched: {len(selected)}; profile updates: {profile_updates}; "
          f"food_nutrient_details rows: {len(detail_values)}; counts_toward_water foods: {len(water_ids)}")


def to_float(value):
    if value is None or value == "":
        return None
    try:
        return float(value)
    except ValueError:
        return None


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("csv_dir", help="the unzipped SR Legacy CSV directory")
    parser.add_argument("output", help="the SQL file to write")
    parser.add_argument("--micronutrients", action="store_true",
                        help="write the V901 micronutrient seed instead of the V900 food seed")
    parser.add_argument("--nutrients-sql", default=str(DEFAULT_V18),
                        help="the V18 migration that defines the nutrients table (V901 only)")
    args = parser.parse_args()
    if args.micronutrients:
        main_micronutrients(args.csv_dir, args.output, args.nutrients_sql)
    else:
        main(args.csv_dir, args.output)
