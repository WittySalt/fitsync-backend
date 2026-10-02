#!/usr/bin/env python3
"""
Turn TheMealDB recipes into FitSync system recipes linked to USDA foods (seed V902).

    python import_recipes.py <mealdb_dir> <usda_dir> <out.sql> [--report report.md]
    python import_recipes.py --self-test

<mealdb_dir> holds meals.json (a list of full meals as lookup.php returns them). <usda_dir> is the unzipped
USDA SR Legacy CSV set that tools/usda/generate_seed.py reads. The output is deterministic: ids are uuid5
values, rows are in a fixed order, and the timestamps are constants, so running it twice gives the same file.

Only foods that V900 wrote can be linked (the food ids come from generate_seed.uid), so every ingredient row
points at a row that exists.
"""
import argparse
import csv
import importlib.util
import json
import re
import uuid
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
MAP_CSV = HERE / "ingredient_map.csv"

RECIPE_NS = uuid.UUID("6f0a1d2e-7c34-4b51-9a8e-000000000000")
TS = "timestamp with time zone '2026-10-03 08:00:00+00'"
AUDIT = f"{TS},{TS},'themealdb-import','themealdb-import',0"
IMAGE_CREDIT = "Photo from TheMealDB"

KCAL_MIN, KCAL_MAX = 80, 1500
SERVINGS = {"Dessert": 6, "Side": 4, "Starter": 4, "Breakfast": 2}
DEFAULT_SERVINGS = 4  # Beef, Chicken, Lamb, Pork, Seafood, Vegetarian, Vegan, Pasta, Goat, Miscellaneous

# vague amounts, in grams (rule of the task)
VAGUE_GRAMS = {"pinch": 0.5, "dash": 0.6, "garnish": 2.0, "handful": 30.0}
ZERO_PHRASES = {"", "to taste", "to serve", "as required", "as needed"}
# words of a measure that is only a preparation or garnish note ("Chopped", "To garnish", "sprigs of fresh")
PREP_ONLY_WORDS = {"chopped", "sliced", "diced", "finely", "thinly", "roughly", "minced", "crushed", "grated",
                   "peeled", "shredded", "ground", "fresh", "dried", "torn", "to", "for", "garnish", "garnishing",
                   "leaves", "leaf", "sprig", "sprigs", "of", "a", "the", "and", "or", "bunch", "sprinkling",
                   "sprinking", "spinkling", "topping", "top", "dusting", "optional", "serving", "decoration",
                   "crumbled", "julienned", "pinch"}
GARNISH_GRAMS = 2.0
# USDA has no per-item weight for these, so a typical one is used: a shallot 25 g, a stalk of lemongrass 15 g
COUNT_FALLBACK_G = {"170499": 25.0, "168573": 15.0}
FLAVOURING_WORDS = ("chilli", "chili", "garlic", "ginger", "shallot", "chive", "lemongrass", "lemon grass",
                    "peppercorn", "vanilla", "scotch bonnet", "jalapeno", "spring onion", "scallion")
# raw juice food (fdc id) and a fallback weight in grams of the juice of one fruit
JUICE = {"lemon": ("167747", 45.0), "lemons": ("167747", 45.0), "lime": ("168156", 30.0),
         "limes": ("168156", 30.0), "orange": ("169098", 85.0), "oranges": ("169098", 85.0)}

WEIGHT_G = {"g": 1.0, "gram": 1.0, "grams": 1.0, "kg": 1000.0, "mg": 0.001,
            "oz": 28.3495, "ounce": 28.3495, "ounces": 28.3495,
            "lb": 453.592, "lbs": 453.592, "pound": 453.592, "pounds": 453.592}
ML_PER = {"ml": 1.0, "millilitre": 1.0, "millilitres": 1.0, "milliliter": 1.0, "milliliters": 1.0,
          "l": 1000.0, "litre": 1000.0, "litres": 1000.0, "liter": 1000.0, "liters": 1000.0}
VOLUME = {  # unit word -> (portion word, ml)
    "tsp": ("tsp", 4.92892), "teaspoon": ("tsp", 4.92892), "teaspoons": ("tsp", 4.92892),
    "tbs": ("tbsp", 14.7868), "tbsp": ("tbsp", 14.7868), "tblsp": ("tbsp", 14.7868),
    "tbls": ("tbsp", 14.7868), "tablespoon": ("tbsp", 14.7868), "tablespoons": ("tbsp", 14.7868),
    "cup": ("cup", 236.588), "cups": ("cup", 236.588),
}
PREP_WORDS = {"chopped", "sliced", "diced", "beaten", "finely", "thinly", "minced", "crushed", "grated",
              "peeled", "boiled", "steamed", "mashed", "skinned", "ground", "dried", "fresh", "cut",
              "shredded", "halved", "quartered", "roughly", "thin", "free", "x"}
COUNT_WORDS = {"large", "medium", "small", "clove", "cloves", "slice", "slices", "can", "cans", "packet",
               "sprig", "sprigs", "leaf", "leaves", "bunch", "head", "stick", "sticks", "bulb", "jar",
               "stalk", "stalks", "piece", "pieces", "bag", "pot", "tub", "tubs", "scoop", "strip",
               "strips", "whole", "knob", "fillet", "fillets", "breast", "breasts", "thigh", "thighs"}
NON_COUNT_LABEL_WORDS = {"cup", "tbsp", "tsp", "oz", "lb", "fl", "serving", "cubic", "pint", "quart",
                         "gallon", "liter", "milliliter", "g", "ml", "tablespoon", "teaspoon", "package",
                         "container", "yield", "yields"}
# a label that starts with one of these is a slice or a measure, not a whole item, so a size after it does not count
PART_FIRST_WORDS = {"slice", "slices", "leaf", "wedge", "ring", "rings", "strip", "piece", "pieces", "cup", "tbsp",
                    "tsp", "oz", "fl", "lb", "tablespoon", "teaspoon", "serving", "sprig", "stalk", "stick"}
PLAIN_COUNT_ORDER = ["medium", "each", "unit", "item", "fruit", "whole", "large", "small"]
LIQUID_WORDS = ("water", "juice", "milk", "oil", "vinegar", "sauce", "wine", "broth", "stock", "beer", "cream",
                "syrup", "soup", "liquor", "coffee")
SEASONING_NAMES = {"parsley", "coriander", "cilantro", "basil", "mint", "thyme", "rosemary", "dill", "sage",
                   "oregano", "chives", "tarragon", "marjoram", "bay leaf", "bay leaves", "lemongrass",
                   "curry leaves", "salt", "pepper", "water"}

_UNICODE_FRACTIONS = {"½": " 1/2", "¼": " 1/4", "¾": " 3/4", "⅓": " 1/3",
                      "⅔": " 2/3", "⅛": " 1/8", "⅜": " 3/8", "⅝": " 5/8",
                      "⅞": " 7/8", "⅕": " 1/5"}


# --------------------------------------------------------------------------------------------- loading

def load_generate_seed():
    path = HERE.parent / "usda" / "generate_seed.py"
    spec = importlib.util.spec_from_file_location("usda_generate_seed", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Usda:
    """The foods V900 wrote, with their kcal, portions and USDA category."""

    def __init__(self, base):
        base = Path(base)
        gs = load_generate_seed()
        self.uid = gs.uid
        selected, _, nutr = gs.select_foods(base, set())
        csv.field_size_limit(10_000_000)
        cat_names = {r["id"]: r["description"]
                     for r in csv.DictReader(open(base / "food_category.csv", encoding="utf-8"))}
        food_cat = {r["fdc_id"]: cat_names.get(r["food_category_id"], "")
                    for r in csv.DictReader(open(base / "food.csv", encoding="utf-8"))}
        units = {r["id"]: r["name"] for r in csv.DictReader(open(base / "measure_unit.csv", encoding="utf-8"))}
        self.foods = {}
        for fid, desc, _code in selected:
            self.foods[fid] = {"desc": desc, "cat": food_cat.get(fid, ""),
                               "id": fid, "kcal": float(nutr[fid].get("1008") or 0)}
        self.portions = defaultdict(list)  # fdc_id -> [(seq, label, grams for one unit)]
        for r in csv.DictReader(open(base / "food_portion.csv", encoding="utf-8")):
            fid = r["fdc_id"]
            if fid not in self.foods:
                continue
            try:
                amount = float(r["amount"] or 1) or 1.0
                grams = float(r["gram_weight"])
            except ValueError:
                continue
            unit = units.get(r["measure_unit_id"], "")
            unit = "" if unit == "undetermined" else unit
            label = " ".join(x for x in (unit, r["portion_description"], r["modifier"]) if x).lower()
            self.portions[fid].append((int(r["seq_num"] or 0), label, grams / amount))
        for fid in self.portions:
            self.portions[fid].sort()


# ------------------------------------------------------------------------------------ ingredient match

def singular(word):
    if len(word) > 4 and word.endswith("ies"):
        return word[:-3] + "y"
    if len(word) > 4 and word.endswith(("oes", "ches", "shes", "xes", "sses")):
        return word[:-2]
    if len(word) > 3 and word.endswith("s") and not word.endswith(("ss", "us")):
        return word[:-1]
    return word


def tokens(text):
    return [singular(w) for w in re.findall(r"[a-z]+", text.lower())]


BAD_CATEGORIES = {"Baby Foods", "Fast Foods", "Restaurant Foods", "American Indian/Alaska Native Foods",
                  "Meals, Entrees, and Side Dishes", "Snacks"}
BAD_WORDS = {"baby", "infant", "formula", "babyfood", "junior", "toddler", "usda", "commodity"}
PENALTY_WORDS = {"cooked": 1.5, "canned": 1.0, "dried": 1.0, "frozen": 1.0, "fried": 1.5, "boiled": 1.0,
                 "roasted": 1.0, "dehydrated": 1.5, "powder": 1.0, "salted": 0.5, "sweetened": 0.5,
                 "cured": 1.0, "smoked": 1.0, "prepared": 1.0, "drained": 0.5, "restaurant": 3.0,
                 "mashed": 1.0, "dry": 1.0}
BRAND = re.compile(r"\b[A-Z][a-z]+'s\b|\b(?:KRAFT|CAMPBELL|PILLSBURY|HEINZ|KELLOGG|NABISCO|MCDONALD|"
                   r"BURGER KING|WENDY|PEPPERIDGE|OSCAR MAYER|ARCHWAY|GENERAL MILLS|NESTLE|GOYA)\b", re.I)


class Matcher:
    """Automatic scorer from an ingredient name to a USDA description."""

    def __init__(self, usda):
        self.usda = usda
        self.index = []  # (fdc_id, first-segment tokens, all tokens set, description)
        for fid, f in usda.foods.items():
            if f["cat"] in BAD_CATEGORIES:
                continue
            desc = f["desc"]
            toks = tokens(desc)
            if BAD_WORDS & set(toks) or BRAND.search(desc):
                continue
            first = tokens(desc.split(",")[0])
            self.index.append((fid, first, set(toks), desc))
        self.index.sort(key=lambda x: x[0])

    def candidates(self, name, limit=6):
        q = tokens(name)
        if not q:
            return []
        qset = set(q)
        scored = []
        for fid, first, allt, desc in self.index:
            if not qset <= allt:
                continue
            score = 0.0
            if first == q:
                score += 10
            elif qset <= set(first):
                score += 6 - 0.5 * (len(first) - len(q))
            else:
                score += 2
            if re.search(r"\braw\b", desc.lower()):
                score += 2.5
            score -= 0.4 * (len(desc.split(",")) - 1)
            score -= 0.2 * (len(allt) - len(q))
            for w, p in PENALTY_WORDS.items():
                if w in allt:
                    score -= p
            scored.append((-score, desc.lower(), fid, desc))
        scored.sort()
        return [(fid, desc, -s) for s, _, fid, desc in scored[:limit]]

    def best(self, name):
        found = self.candidates(name, 1)
        return found[0][0] if found else None


def read_map(path=MAP_CSV):
    mapping = {}
    if not Path(path).exists():
        return mapping
    with open(path, encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            mapping[row["mealdb_name"].strip().lower()] = row["fdc_id"].strip()
    return mapping


# ------------------------------------------------------------------------------------------ measures

def _norm_measure(text):
    t = (text or "").lower().strip()
    for k, v in _UNICODE_FRACTIONS.items():
        t = t.replace(k, v)
    t = t.replace("–", "-").replace("—", "-")
    return re.sub(r"\s+", " ", t).strip()


_NUM_RE = re.compile(r"(\d+(?:\.\d+)?)(?:\s+(\d+)/(\d+)|/(\d+))?")


def _to_number(m):
    whole = float(m.group(1))
    if m.group(2):
        return whole + int(m.group(2)) / int(m.group(3))
    if m.group(4):  # "1/2"
        return whole / int(m.group(4))
    return whole


def parse_amount(text):
    """
    Split a measure into (quantity, unit word, rest, grams in parentheses).
    quantity is None when there is no leading number. A range takes the lower bound.
    """
    t = _norm_measure(text)
    mult = re.match(r"^(\d+)\s*x\s*(\d+(?:\.\d+)?)\s*(g|ml|kg|l)\b", t)
    if mult:
        return float(mult.group(1)) * float(mult.group(2)), mult.group(3), "", None
    m = _NUM_RE.match(t)
    if not m:
        return None, None, t, None
    qty = _to_number(m)
    rest = t[m.end():]
    rng = re.match(r"^\s*(?:-|to|or)\s*(?=\d)", rest)
    if rng:  # a range: skip the upper bound
        m2 = _NUM_RE.match(rest[rng.end():])
        if m2:
            rest = rest[rng.end() + m2.end():]
    paren = re.search(r"\(\s*(\d+(?:\.\d+)?)\s*(?:g|grams|ml)\b", rest)
    paren_g = float(paren.group(1)) if paren else None
    rest = rest.strip()
    um = re.match(r"^([a-z]+)", rest)
    unit = um.group(1) if um else None
    after = rest[um.end():] if um else rest
    return qty, unit, after.strip(), paren_g


def pick_portion(portions, word):
    """
    Grams for one of the portion that is called the word: a label that starts with it ("clove", "large"), the
    simplest first, else a label that has it as a size or part after the item ("potato medium", "head, large").
    """
    matches, later = [], []
    for seq, label, g in portions:
        outside = re.sub(r"\([^)]*\)", " ", label)
        toks = re.findall(r"[a-z]+", outside)
        if not toks:
            continue
        if singular(toks[0]) == singular(word):
            matches.append((len(toks), seq, g))
        elif toks[0] not in PART_FIRST_WORDS and singular(word) in {singular(t) for t in toks[1:]}:
            later.append((len(toks), seq, g))
    found = matches or later
    return min(found)[2] if found else None


def pick_plain_count(portions):
    for word in PLAIN_COUNT_ORDER:
        g = pick_portion(portions, word)
        if g:
            return g
    for seq, label, g in portions:
        first = re.findall(r"[a-z]+", label)
        if first and first[0] not in NON_COUNT_LABEL_WORDS:
            return g
    return None


def density(portions):
    """Grams per ml from a cup portion (else tbsp, tsp), or None."""
    for word, ml in (("cup", 236.588), ("tbsp", 14.7868), ("tsp", 4.92892)):
        g = pick_portion(portions, word)
        if g:
            return g / ml
    return None


def is_seasoning(name, food):
    low = name.lower()
    if low in SEASONING_NAMES or any(low.endswith(" " + h) for h in SEASONING_NAMES):
        return True
    return bool(food) and (food["cat"] == "Spices and Herbs" or food["desc"].lower().startswith("water, "))


def is_flavouring(name, food):
    """Herbs, spices, chilli, garlic and similar: the foods a bare garnish note can stand for."""
    low = name.lower()
    return is_seasoning(name, food) or any(w in low for w in FLAVOURING_WORDS)


def citrus_juice(name, measure, usda):
    """
    "Juice of 2" (also "zest and juice of 1", "juice of 1/2") for lemon, lime or orange: the raw juice food and
    the grams of N fruits' juice, from the USDA "yields" portion of that juice (fallback constants), the zest
    ignored. "Zest of 1" alone counts as a 2 g garnish of the fruit. Returns (fdc_id, grams) or None.
    """
    key = name.lower()
    t = _norm_measure(measure)
    if key not in JUICE or not re.search(r"juice|zest", t):
        return None
    fdc, fallback = JUICE[key]
    if "juice" not in t:
        return (None, GARNISH_GRAMS)
    word = re.search(r"\bof\s+(a half|half|one|an?|\d+(?:\s+\d+/\d+|/\d+|\.\d+)?)", t)
    if word:
        w = word.group(1)
        n = 0.5 if "half" in w else 1.0 if w in ("one", "a", "an") else _to_number(_NUM_RE.match(w))
    else:
        m = _NUM_RE.search(t)
        n = _to_number(m) if m else 1.0
    per = fallback
    for seq, label, g in usda.portions.get(fdc, []):
        if "yield" in label and "wedge" not in label:
            per = g
            break
    return fdc, n * per


def is_liquidish(food):
    d = food["desc"].lower()
    return food["cat"] in ("Beverages", "Fats and Oils", "Alcoholic Beverages", "Soups, Sauces, and Gravies") \
        or any(w in d for w in LIQUID_WORDS)


CAN_GRAMS = 400.0
CAN_FOODS = ("coconut", "tomato", "chickpea", "bean", "tuna")
OIL_TBSP_G = 13.5


def household_amount(t, name, food, portions):
    """
    Cooking notes with no number: "for frying", "to fry" or "drizzle" on an oil is 1 tbsp of that oil; "splash" on
    a liquid is 15 ml; "knob" on butter is 10 g. "1 can" or "2 tins" of coconut milk, tomatoes, chickpeas, beans
    or tuna is 400 g each (a standard 400 g can). Returns grams or None when the rule does not apply.
    """
    desc = food["desc"].lower()
    is_oil = food["cat"] == "Fats and Oils" and desc.startswith("oil")
    if is_oil and re.match(r"^(?:for (?:shallow |deep )?frying|to fry|drizzle\b.*)$", t):
        return pick_portion(portions, "tbsp") or OIL_TBSP_G
    if t in ("splash", "a splash", "1 splash") and is_liquidish(food):
        return 15.0 * (density(portions) or 1.0)
    if desc.startswith("butter") and re.fullmatch(r"(?:1 |a |one )?knob", t):
        return 10.0
    m = re.fullmatch(r"(\d+(?:\.\d+)?)\s*(?:can|cans|tin|tins)", t)
    if m and any(w in desc or w in name.lower() for w in CAN_FOODS):
        return float(m.group(1)) * CAN_GRAMS
    return None


def measure_to_grams(measure, name, food, portions):
    """Returns (grams, None) on success and (None, reason) when the measure cannot be resolved."""
    t = _norm_measure(measure)
    seasoning = is_seasoning(name, food)
    special = household_amount(t, name, food, portions)
    if special is not None:
        return special, None
    if t in ZERO_PHRASES:
        return (0.0, None) if seasoning else (None, f"vague measure '{t or '(empty)'}' for {name}")
    for word, g in VAGUE_GRAMS.items():
        if re.fullmatch(rf"(?:a |1 |one |small |large )?{word}(?: of)?", t):
            return g, None
    if t in ("to garnish", "for garnish", "to garnish with"):
        return GARNISH_GRAMS, None
    m = re.match(r"^(\d+)\s+(pinch|dash|handful|handfull)$", t)
    if m:
        word = "handful" if m.group(2).startswith("handf") else m.group(2)
        return int(m.group(1)) * VAGUE_GRAMS[word], None
    qty, unit, _after, paren_g = parse_amount(t)
    if qty is None:
        words = re.findall(r"[a-z]+", t)
        if words and all(w in PREP_ONLY_WORDS for w in words):
            if is_flavouring(name, food):
                return GARNISH_GRAMS, None
            return None, f"vague measure '{t}' for {name}"
        return (0.0, None) if seasoning else (None, f"vague measure '{t}' for {name}")
    if unit in WEIGHT_G:
        return qty * WEIGHT_G[unit], None
    if unit in ML_PER:
        return qty * ML_PER[unit] * (density(portions) or 1.0), None
    if unit in VOLUME:
        word, ml = VOLUME[unit]
        g = pick_portion(portions, word)
        if g:
            return qty * g, None
        d = density(portions)
        if d is None:
            if not (seasoning or is_liquidish(food)):
                return None, f"no gram weight for '{unit}' of {name}"
            d = 1.0
        return qty * ml * d, None
    if paren_g is not None:
        return qty * paren_g, None
    if unit is None or unit in PREP_WORDS:
        g = pick_plain_count(portions) or COUNT_FALLBACK_G.get(food.get("id"))
        if g:
            return qty * g, None
        return (0.0, None) if seasoning else (None, f"no count weight for {name}")
    g = pick_portion(portions, unit)
    if g is None and unit in ("large", "medium", "small"):
        g = pick_portion(portions, "medium") or COUNT_FALLBACK_G.get(food.get("id"))
    if g:
        return qty * g, None
    if seasoning:
        return 0.0, None
    return None, f"no portion '{unit}' for {name}"


# --------------------------------------------------------------------------------------------- steps

_LABEL_ONLY = re.compile(r"^\s*(?:step\s*)?\d+\s*[.:)\-]?\s*$", re.I)
_LEAD_NUM = re.compile(r"^\s*(?:step\s*\d+\s*[:.\-)]?|\d+\s*[.)])\s+", re.I)
_SENTENCE_END = re.compile(r"([.!?])\s+(?=[A-Z])")
# a full stop after one of these ends an abbreviation, not a sentence ("approx. Add" stays together)
_ABBREVIATIONS = {"approx", "ca", "min", "mins", "max", "tbsp", "tsp", "oz", "lb", "lbs", "vs", "etc", "eg", "ie",
                  "approximately"}
MAX_STEP_SENTENCES = 2


def split_sentences(text):
    """Split at . ! ? followed by whitespace and a capital letter; never inside 3.5 or after approx."""
    sentences, start = [], 0
    for m in _SENTENCE_END.finditer(text):
        if m.group(1) == ".":
            words = re.findall(r"[A-Za-z.]+$", text[start:m.start()])
            last = words[0].replace(".", "").lower() if words else ""
            if last in _ABBREVIATIONS:
                continue
        sentences.append(text[start:m.end(1)].strip())
        start = m.end()
    sentences.append(text[start:].strip())
    return [x for x in sentences if x]


def split_steps(instructions):
    lines = []
    for raw in re.split(r"\r?\n", instructions or ""):
        line = raw.strip()
        if not line or _LABEL_ONLY.match(line):
            continue
        line = _LEAD_NUM.sub("", line, count=1).strip()
        if line:
            lines.append(line)
    steps = []
    for line in lines:
        sentences = split_sentences(line)
        steps.extend(" ".join(sentences[i:i + MAX_STEP_SENTENCES])
                     for i in range(0, len(sentences), MAX_STEP_SENTENCES))
    return steps


# ------------------------------------------------------------------------------------------- SQL

def sql_str(value):
    if value is None or str(value).strip() == "":
        return "null"
    return "'" + str(value).strip().replace("'", "''") + "'"


def rid(*parts):
    return str(uuid.uuid5(RECIPE_NS, "fitsync-themealdb:" + ":".join(str(p) for p in parts)))


def fmt(n):
    return f"{n:.2f}"


HEADER = """\
-- V902: TheMealDB system recipes linked to USDA foods (scope SYSTEM, source THEMEALDB).
-- Generated by tools/themealdb/import_recipes.py. Prod-only (db/seed): not applied in tests.
--
-- Source: TheMealDB free API (key "1"), used for an educational project; images are linked, not copied
-- (image_url points at TheMealDB, image_credit says "Photo from TheMealDB"). The instructions are split into
-- steps as written, without a model and without rewriting.
-- Ingredients: every ingredient is matched to a USDA SR Legacy food that V900 wrote (the ids are the same
-- uuid5 values), by an automatic scorer plus the hand-checked tools/themealdb/ingredient_map.csv. quantity
-- and grams are the amount in grams, display_text is the ingredient line as TheMealDB wrote it.
-- Vague amounts: pinch = 0.5 g, dash = 0.6 g, garnish = 2 g, handful = 30 g. "to taste", "to serve", an empty
-- measure (and any other measure that cannot be read) count as 0 g, but only for salt, pepper, spices, herbs
-- and water. For any other ingredient such a measure makes the whole recipe skipped.
-- Garnish notes: a measure with no number and only a preparation word ("Chopped", "Sliced", "To garnish") counts
-- as a garnish of 2 g for herbs, spices, chilli, garlic and similar flavourings; for anything else it skips the
-- recipe. "Juice of N" (also "zest and juice of N", "juice of 1/2") for lemon, lime or orange is N times the USDA
-- raw juice weight of one fruit (lemon 48 g, lime 44 g, orange 86 g from food_portion; 45, 30, 85 g when a food
-- has no such portion), linked to the raw juice food, the zest ignored. "Zest of N" alone is a 2 g garnish.
-- Cooking notes: "for frying", "to fry" or "drizzle" on a cooking oil is 1 tbsp of that oil (USDA tbsp portion,
-- else 13.5 g); "splash" on a liquid is 15 ml; "knob" on butter is 10 g. "1 can" or "1 tin" (N cans) of coconut
-- milk, tomatoes, chickpeas, beans or tuna is 400 g each: a standard 400 g can is assumed.
-- Counts of a shallot (25 g) or a stalk of lemongrass (15 g) use a typical weight, as USDA has none for them.
-- A recipe is also skipped when an ingredient has no USDA match, or when its energy per serving is below
-- {kmin} or above {kmax} kcal.
-- Servings: TheMealDB has none. The importer uses 4 for mains (Beef, Chicken, Lamb, Pork, Seafood,
-- Vegetarian, Vegan, Pasta, Goat, Miscellaneous), 6 for Dessert, 4 for Side and Starter, 2 for Breakfast.
-- Re-runnable: every insert is ON CONFLICT DO NOTHING; ids are uuid5 of "fitsync-themealdb:" + idMeal
-- (+ ":ing:" n or ":step:" n).
"""


def write_sql(out_path, recipes):
    out = [HEADER.format(kmin=KCAL_MIN, kmax=KCAL_MAX)]
    rcols = ("recipe_id, user_id, scope, source, source_ref, source_url, image_url, image_credit, category, "
             "cuisine, prep_minutes, published, name, servings, note, date_time_created, date_time_updated, "
             "created_by, updated_by, version")
    icols = ("ingredient_id, recipe_id, food_catalog_item_id, food_serving_id, quantity, display_text, grams, "
             "sort_order, date_time_created, date_time_updated, created_by, updated_by, version")
    scols = ("step_id, recipe_id, step_number, text, date_time_created, date_time_updated, created_by, "
             "updated_by, version")

    def batched(table, cols, rows, size=200):
        for i in range(0, len(rows), size):
            out.append(f"insert into {table} ({cols}) values")
            out.append(",\n".join(rows[i:i + size]))
            out.append("on conflict do nothing;\n")

    rrows, irows, srows = [], [], []
    for r in recipes:
        rrows.append(
            f"('{r['id']}',null,'SYSTEM','THEMEALDB',{sql_str(r['mealId'])},{sql_str(r['source_url'])},"
            f"{sql_str(r['image'])},'{IMAGE_CREDIT}',{sql_str(r['category'])},{sql_str(r['area'])},null,true,"
            f"{sql_str(r['name'])},{fmt(r['servings'])},null,{AUDIT})")
        for n, ing in enumerate(r["ingredients"], 1):
            irows.append(
                f"('{rid(r['mealId'], 'ing', n)}','{r['id']}','{ing['food_id']}',null,{fmt(ing['grams'])},"
                f"{sql_str(ing['display'])},{fmt(ing['grams'])},{n},{AUDIT})")
        for n, text in enumerate(r["steps"], 1):
            srows.append(f"('{rid(r['mealId'], 'step', n)}','{r['id']}',{n},{sql_str(text)},{AUDIT})")
    batched("recipes", rcols, rrows)
    batched("recipe_ingredients", icols, irows)
    batched("recipe_steps", scols, srows)
    Path(out_path).write_text("\n".join(out), encoding="utf-8", newline="\n")


def check_sql(path):
    """Balanced quotes and parentheses per statement (a statement ends at a ';' outside a string)."""
    text = Path(path).read_text(encoding="utf-8")
    depth, in_str, count, i = 0, False, 0, 0
    while i < len(text):
        c = text[i]
        if in_str:
            if c == "'":
                if text[i + 1:i + 2] == "'":
                    i += 1
                else:
                    in_str = False
        elif c == "'":
            in_str = True
        elif c == "-" and text[i:i + 2] == "--":
            i = text.find("\n", i)
            if i < 0:
                break
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth < 0:
                raise ValueError(f"unbalanced ')' near offset {i}")
        elif c == ";":
            if depth != 0:
                raise ValueError(f"statement {count + 1} ends inside parentheses")
            count += 1
        i += 1
    if in_str or depth != 0:
        raise ValueError("unterminated string or parenthesis")
    return count


# ------------------------------------------------------------------------------------------- driver

def ingredient_lines(meal):
    for i in range(1, 21):
        name = (meal.get(f"strIngredient{i}") or "").strip()
        if name:
            yield name, (meal.get(f"strMeasure{i}") or "").strip()


def run(mealdb_dir, usda_dir, out_path, report_path):
    meals = json.load(open(Path(mealdb_dir) / "meals.json", encoding="utf-8"))
    meals.sort(key=lambda m: int(m["idMeal"]))
    usda = Usda(usda_dir)
    matcher = Matcher(usda)
    override = read_map()
    resolved = {}

    def resolve(name):
        key = name.lower()
        if key not in resolved:
            val = override.get(key)
            if val is None:
                val = matcher.best(key)
            resolved[key] = None if val in (None, "NONE") else val
            if resolved[key] and resolved[key] not in usda.foods:
                resolved[key] = None
        return resolved[key]

    recipes, skipped, reasons = [], [], Counter()
    for meal in meals:
        fails, ings = [], []
        for name, measure in ingredient_lines(meal):
            fdc = resolve(name)
            if fdc is None:
                fails.append(("unmatched ingredient", f"unmatched ingredient: {name.lower()}"))
                continue
            juice = citrus_juice(name, measure, usda)
            if juice:
                fdc = juice[0] or fdc
                grams, why = juice[1], None
            else:
                grams, why = measure_to_grams(measure, name, usda.foods[fdc], usda.portions.get(fdc, []))
            if grams is None:
                kind = "vague measure" if why.startswith("vague") else "no gram weight"
                fails.append((kind, why))
                continue
            ings.append({"food_id": usda.uid("food", fdc), "fdc": fdc, "grams": round(grams, 2),
                         "display": f"{measure} {name}".strip()})
        cat = meal.get("strCategory") or ""
        servings = SERVINGS.get(cat, DEFAULT_SERVINGS)
        if fails:
            skipped.append((meal, fails[0][0], fails[0][1]))
            for r in sorted({f[1] for f in fails}):
                reasons[r] += 1
            continue
        kcal = sum(usda.foods[i["fdc"]]["kcal"] * i["grams"] / 100.0 for i in ings) / servings
        if not ings or kcal < KCAL_MIN or kcal > KCAL_MAX:
            skipped.append((meal, "suspicious kcal", f"kcal per serving {kcal:.0f}"))
            reasons[f"suspicious kcal per serving (below {KCAL_MIN} or above {KCAL_MAX})"] += 1
            continue
        steps = split_steps(meal.get("strInstructions"))
        if not steps:
            skipped.append((meal, "no steps", "no instructions"))
            reasons["no instructions"] += 1
            continue
        recipes.append({"id": rid(meal["idMeal"]), "mealId": meal["idMeal"], "name": meal["strMeal"].strip(),
                        "category": cat, "area": meal.get("strArea"), "image": meal.get("strMealThumb"),
                        "source_url": meal.get("strSource"), "servings": servings,
                        "ingredients": ings, "steps": steps, "kcal": kcal})
    write_sql(out_path, recipes)
    stmts = check_sql(out_path)
    print(f"meals: {len(meals)}; imported: {len(recipes)}; skipped: {len(skipped)}; statements: {stmts}")
    if report_path:
        write_report(report_path, meals, recipes, skipped, reasons)


def write_report(path, meals, recipes, skipped, reasons):
    by_cat = Counter(r["category"] for r in recipes)
    tot_cat = Counter(m["strCategory"] for m in meals)
    skip_kind = Counter(k for _, k, _ in skipped)
    lines = ["# TheMealDB import report", "",
             f"Meals read: {len(meals)}. Imported: {len(recipes)}. Skipped: {len(skipped)}.",
             "Generated by import_recipes.py; the output is deterministic.", "",
             "## Imported per category", "", "| Category | Imported | Of |", "| --- | ---: | ---: |"]
    for c in sorted(tot_cat):
        lines.append(f"| {c} | {by_cat.get(c, 0)} | {tot_cat[c]} |")
    lines += ["", "## Skipped per reason (the first reason of each recipe)", "", "| Reason | Recipes |",
              "| --- | ---: |"]
    for k, v in skip_kind.most_common():
        lines.append(f"| {k} | {v} |")
    lines += ["", "## The 30 most common reasons (a recipe can have several)", "", "| Reason | Recipes |",
              "| --- | ---: |"]
    for k, v in reasons.most_common(30):
        lines.append(f"| {k.replace('|', '/')} | {v} |")
    lines += ["", f"## Suspicious energy (below {KCAL_MIN} or above {KCAL_MAX} kcal per serving, excluded)", ""]
    sus = [(m, d) for m, k, d in skipped if k == "suspicious kcal"]
    for m, d in sus:
        lines.append(f"- {m['strMeal']} ({m['strCategory']}, {m['idMeal']}): {d}")
    if not sus:
        lines.append("None.")
    lines += ["", "## Sanity check: kcal per serving of 10 imported recipes", "",
              "| Recipe | Category | Servings | kcal per serving |", "| --- | --- | ---: | ---: |"]
    step = max(1, len(recipes) // 10)
    for r in recipes[::step][:10]:
        lines.append(f"| {r['name']} | {r['category']} | {r['servings']:g} | {r['kcal']:.0f} |")
    kc = sorted(r["kcal"] for r in recipes)
    if kc:
        lines += ["", f"Imported recipes range from {kc[0]:.0f} to {kc[-1]:.0f} kcal per serving, "
                      f"median {kc[len(kc) // 2]:.0f}."]
    Path(path).write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


# ---------------------------------------------------------------------------------------- self test

def self_test():
    onion = {"desc": "Onions, raw", "cat": "Vegetables and Vegetable Products", "kcal": 40}
    port = [(1, "cup, chopped", 160.0), (2, "tbsp chopped", 10.0), (3, "large", 150.0),
            (4, 'medium (2-1/2" dia)', 110.0), (5, "small", 70.0), (6, "cup", 150.0)]
    garlic = [(1, "cup", 136.0), (2, "tsp", 2.8), (3, "clove", 3.0)]
    pepper = {"desc": "Spices, pepper, black", "cat": "Spices and Herbs", "kcal": 251}
    cases = [
        (("200g", "onion", onion, port), 200.0),
        (("1 1/2 kg", "onion", onion, port), 1500.0),
        (("½ cup", "onion", onion, port), 75.0),
        (("2-3 tbsp", "onion", onion, port), 20.0),
        (("2 tsp", "garlic", onion, garlic), 5.6),
        (("3 cloves", "garlic", onion, garlic), 9.0),
        (("1 large", "onion", onion, port), 150.0),
        (("1", "onion", onion, port), 110.0),
        (("2 lbs", "onion", onion, port), 907.184),
        (("pinch", "pepper", pepper, []), 0.5),
        (("to taste", "pepper", pepper, []), 0.0),
        (("400g/14oz", "onion", onion, port), 400.0),
        (("1/4 tsp", "garlic", onion, garlic), 0.7),
        (("1½ cups", "onion", onion, port), 225.0),
    ]
    for args, want in cases:
        got, why = measure_to_grams(*args)
        assert got is not None and abs(got - want) < 0.01, (args, got, why)
    assert measure_to_grams("to taste", "onion", onion, port)[0] is None
    assert measure_to_grams("handful", "onion", onion, port)[0] == 30.0
    assert split_steps("STEP 1\r\nMix the flour.\r\n\r\nSTEP 2\r\nBake it.") == ["Mix the flour.", "Bake it."]
    assert split_steps("1. Chop the onion.\r\n2) Fry it.\r\nStep 3: Serve.") == \
        ["Chop the onion.", "Fry it.", "Serve."]
    one = "Mix. Stir well. Add milk. Heat the pan. Pour it in. Cook for 5 minutes. Serve hot."
    assert split_steps(one) == ["Mix. Stir well.", "Add milk. Heat the pan.", "Pour it in. Cook for 5 minutes.",
                                "Serve hot."]
    # every paragraph is split, not only a lone one; numbers and abbreviations stay whole
    assert split_steps("Mix. Stir. Pour.\r\nBake it.") == ["Mix. Stir.", "Pour.", "Bake it."]
    assert split_steps("Add 3.5 kg of flour. Cook for approx. 5 minutes. Add oil! Is it done? Serve.") == \
        ["Add 3.5 kg of flour. Cook for approx. 5 minutes.", "Add oil! Is it done?", "Serve."]
    assert split_steps("Cook for approx. Add the salt. Stir.") == ["Cook for approx. Add the salt. Stir."]
    # "then" starts in lower case, so "Heat the oil. then add the onion." is one sentence; with "Serve." it makes two, one step.
    assert split_steps("Heat the oil. then add the onion. Serve.") == ["Heat the oil. then add the onion. Serve."]
    assert split_steps("1\r\n2\r\n  \r\n") == []
    oil = {"desc": "Oil, olive, salad or cooking", "cat": "Fats and Oils", "kcal": 884}
    assert measure_to_grams("For frying", "olive oil", oil, [(1, "tbsp", 13.5)])[0] == 13.5
    assert measure_to_grams("Drizzle", "oil", oil, [])[0] == 13.5
    assert abs(measure_to_grams("Splash", "olive oil", oil, [(1, "cup", 216.0)])[0] - 15 * 216 / 236.588) < 0.01
    butter = {"desc": "Butter, salted", "cat": "Dairy and Egg Products", "kcal": 717}
    assert measure_to_grams("Knob", "butter", butter, [])[0] == 10.0
    coco = {"desc": "Nuts, coconut milk, canned", "cat": "Nut and Seed Products", "kcal": 197}
    assert measure_to_grams("2 cans", "coconut milk", coco, [])[0] == 800.0
    assert measure_to_grams("1 Can", "onion", onion, port)[0] is None
    assert sql_str("it's") == "'it''s'"
    # supervisor rules: a bare garnish note is 2 g for flavourings only, citrus juice is N times the yield
    chilli = {"desc": "Peppers, hot chili, red, raw", "cat": "Vegetables and Vegetable Products", "kcal": 40}
    assert measure_to_grams("Chopped", "red chilli", chilli, [])[0] == 2.0
    assert measure_to_grams("To garnish", "parsley", onion, [])[0] == 2.0
    assert measure_to_grams("Sliced", "onion", onion, port)[0] is None
    usda = type("U", (), {"portions": {"167747": [(3, "lemon yields", 48.0)], "168156": []}})()
    assert citrus_juice("lemon", "Juice of 2", usda) == ("167747", 96.0)
    assert citrus_juice("Lemon", "zest and juice of 1/2", usda) == ("167747", 24.0)
    assert citrus_juice("lime", "Juice of 1", usda) == ("168156", 30.0)
    assert citrus_juice("lime", "juice of half", usda) == ("168156", 15.0)
    assert citrus_juice("lemon", "zest of 1", usda) == (None, 2.0)
    assert citrus_juice("onion", "juice of 1", usda) is None
    print("self-test OK")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("paths", nargs="*", help="<mealdb_dir> <usda_dir> <out.sql>")
    ap.add_argument("--report")
    ap.add_argument("--self-test", action="store_true")
    a = ap.parse_args()
    if a.self_test:
        self_test()
        return
    if len(a.paths) != 3:
        ap.error("expected <mealdb_dir> <usda_dir> <out.sql>")
    run(a.paths[0], a.paths[1], a.paths[2], a.report)


if __name__ == "__main__":
    main()
