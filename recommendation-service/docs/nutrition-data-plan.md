# FitSync Food Database — Build Plan (open-data sourced)

> **Status:** Design / operational plan, written while recommendation-service was developed on its
> own branch. Goal: give analytics the most comprehensive, well-structured food data
> achievable from open data sources, with maximum room for future work — at capstone-feasible effort.
>
> Companion to `recommendation-service/README.md` and `analytics-collaboration.md`.

---

## 1. Goal & principles

- **Analytics-grade food data:** every logged item should carry food group, processing level, complete
  macros, and (where available) micros — so analytics can do "meat-heavy / veg-light", "ultra-processed
  share", "fiber adequacy", etc.
- **Two-source canonical catalog:** one internal schema, fed by multiple open datasets, each used for
  what it is good at. Never bind the model to a single source.
- **Provenance + completeness first-class:** always record where a value came from and whether it is
  actually present (crowdsourced data is patchy — `null` must never be read as `0`).
- **Right-sized for a capstone:** bulk-load only what is small and clean; fetch the rest on demand and
  cache. Don't ingest millions of rows you'll never use.

---

## 2. Source selection

| Dataset | License | Strong at | Weak at | Role in FitSync |
|---|---|---|---|---|
| **USDA FoodData Central (FDC)** | **Public domain (CC0)** — no constraints | Generic/raw/cooked whole foods; complete macros **+ micros**; standardized nutrient IDs; **portion→grams** tables (FNDDS) | US-centric branded data; no NOVA/Nutri-Score | **Generic-food spine + nutrient richness + portion conversion** |
| **Open Food Facts (OFF)** | **ODbL** (share-alike + attribution) | Barcoded/packaged products; **NOVA group, Nutri-Score**, additives, allergens, ingredient analysis; global brands | Crowdsourced → sparse/inconsistent; weak on generic foods; messy category tags; EU-skewed | **Barcode lookups + diet-quality signals (NOVA/Nutri-Score) + dietary flags** |

**Why two sources:** users log both packaged foods (barcode → OFF) and generic/home-cooked foods
("100g chicken breast" → FDC). Neither alone covers real eating. FDC is the clean, license-free spine;
OFF adds barcode coverage and the diet-quality signals FDC lacks.

### 2.1 FDC data types (pick deliberately)
- **SR Legacy** (~7,800 generic foods, very complete macros+micros) → **the MVP spine. Small, clean, public domain.**
- **Foundation Foods** (lab-analyzed, richest) → augment SR over time.
- **FNDDS / Survey** → **portion measures** ("1 cup", "1 medium") → grams. Key for unit normalization.
- **Branded Foods** (~1.4M, US) → optional; OFF covers barcodes globally.

### 2.2 Licensing — must handle (capstone is publishable)
- **FDC:** public domain. Nothing required.
- **OFF:** **ODbL** — (a) **attribute** ("Data from Open Food Facts, ODbL"); (b) **share-alike** *only if
  you redistribute the database itself*. Using OFF data inside the app (not redistributing the DB) → just
  attribution. Add an attribution line in the app/about page and in `event-contracts` provenance.

---

## 3. Two ingestion strategies (this is the capstone shortcut)

Don't bulk-import OFF's ~3M-product dump. Use **bulk for the small clean source, on-demand for the rest**:

### A. Bulk-load USDA SR Legacy → the generic spine
- One-time import of SR Legacy (CSV/JSON full download). ~7.8k foods → trivial to load.
- Gives every generic food complete macros+micros + a USDA food category. This alone covers most
  home-cooked logging and gives analytics a reliable base.

### B. On-demand-cache OFF for barcodes → catalog grows with real usage
```
barcode scan → look up local FoodCatalogItem
   ├─ hit  → use it
   └─ miss → OFF REST API (api world readonly) → normalize → INSERT into catalog (provenance=OFF) → use
```
- The catalog grows organically with **what users actually eat**, not 3M rows you'll never serve.
- Each fetched product is normalized once and cached → zero repeat cost, no giant ETL.
- Cache the OFF NOVA/Nutri-Score/additives at the same time (free signal).

> This pattern is the cost/perf win: **bulk only the small clean dataset; lazy-load + cache the huge messy one.**

---

## 4. Canonical model (how sources land in your existing entities)

Your entities already cover most of this — additions are marked **NEW**.

```
FoodCatalogItemEntity          # one row per canonical food (generic or product)
  + sourceDataset    NEW       # USDA_SR | USDA_FOUNDATION | OFF | CUSTOM
  + sourceRef        NEW       # fdcId / barcode / custom id
  + foodGroupCode    NEW       # normalized dietary group (see §5)
  + novaGroup        NEW       # 1–4 (OFF) — nullable
  + nutriScore       NEW       # A–E (OFF) — nullable
  + dataCompleteness NEW       # 0–1, how many key nutrients present
  categoryCode (exists)        # raw source category (OFF tag / USDA category)

FoodNutrientProfileEntity (exists)   # per-100g basis nutrient facts (+ satFat/transFat/K/cholesterol)
MealItemNutrientDetailEntity (exists)# flexible per-nutrient rows → vitamins/minerals from FDC/OFF
FoodCategoryEntity (exists)          # raw hierarchical taxonomy (keep for provenance)

FoodGroupMappingEntity         # NEW: source category → normalized FoodGroup (see §5)
```

And on the **meal line item** (`AbstractFoodLineItemEntity`) — snapshot the classification at log time
(the 🔴 fix from the entity review), so analysis is immune to later recategorization and works for
custom foods:
```java
String  foodGroupCode;      // NEW snapshot
String  sourceCategoryCode; // NEW snapshot (provenance)
Integer novaGroup;          // NEW snapshot
String  nutriScore;         // NEW snapshot
BigDecimal gramsConsumed;   // NEW canonical grams (see §6)
String  sourceDataset;      // NEW provenance
```
`CustomFoodEntity` must also get `foodGroupCode` (today it has no category at all → blind spot).

---

## 5. The food-group layer (the part open data does NOT give you)

Neither OFF's product tags nor USDA categories are a clean dietary food-group taxonomy. **Build one.**

```java
enum FoodGroup { PROTEIN_ANIMAL, PROTEIN_PLANT, VEGETABLE, FRUIT,
                 GRAIN_WHOLE, GRAIN_REFINED, DAIRY, FAT_OIL,
                 BEVERAGE_SUGARY, BEVERAGE_OTHER, SNACK_SWEET, MIXED, OTHER }
```
```java
@Entity @Table(name = "food_group_map")
class FoodGroupMappingEntity {           // source category → FoodGroup
  @Id UUID id;
  String sourceDataset;                  // USDA_SR | OFF
  String sourceCategoryCode;             // e.g. "Poultry Products" / "en:chicken-breasts"
  @Enumerated(STRING) FoodGroup foodGroup;
}
```
**Build it semi-automatically:**
1. **Seed by prefix/rule.** USDA has ~25 top-level food categories → map each to a FoodGroup in one pass.
   OFF category tags are prefixed (`en:vegetables`, `en:meats`, `en:sugary-snacks`) → prefix rules cover
   the bulk.
2. **Manual cleanup** of the long tail (a few hours; the head categories cover most volume).
3. At ingest/log time: `source category → mapping → foodGroupCode` written onto the catalog item and
   snapshotted onto the meal item.
4. Unmapped → `OTHER`, and **log it** so you can extend the map. Never silently drop.

This mapping table *is* the asset that makes "肉多菜少" possible — and it's reusable forever.

---

## 6. Normalization rules (apply at ingest / log time)

| Concern | Rule |
|---|---|
| Energy units | OFF stores **kJ** primarily → store kcal too (`kcal = kJ / 4.184`); keep both. |
| Sodium vs salt | OFF gives **salt (g)** → `sodium_mg = salt_g × 1000 / 2.5`. |
| Nutrient basis | Normalize all profiles to **per 100 g** (you already have `basisQuantity`/`basisUnit`). |
| Portions → grams | At log time compute `gramsConsumed` from quantity×serving using FDC FNDDS measures (or product serving_quantity). Analytics then never does unit math. |
| Language | Pick `en` fields from OFF; fall back to product name. |
| Missing values | Record presence; compute `dataCompleteness`. **Reject** a record below a macro-completeness threshold (e.g. missing energy+protein) rather than store garbage. |

---

## 7. ETL pipeline (operational)

```
1. ACQUIRE   USDA SR Legacy full download (one-time); FDC API key for live generic lookups.
             OFF: REST API for on-demand barcode (no bulk dump needed for MVP).
2. STAGE     Raw rows → staging tables (or object storage), untouched, with fetch timestamp.
3. NORMALIZE units/basis/portions/language (§6); drop sub-threshold records.
4. CLASSIFY  source category → FoodGroup (§5); unmapped → OTHER + log.
5. MERGE     key by barcode (OFF) / fdcId (USDA); provenance recorded; prefer USDA for generic,
             OFF for barcoded. No blind overwrite — keep source rows, one canonical projection.
6. LOAD      upsert FoodCatalogItem + FoodNutrientProfile + nutrient details (idempotent on sourceRef).
7. INDEX     search by name/brand/barcode (Postgres full-text is enough at this scale).
8. EMIT      nutrition events project the snapshotted fields (§4) so analytics gets them downstream.
```
**Idempotency:** upsert on `(sourceDataset, sourceRef)` so re-imports/re-fetches don't duplicate.

---

## 8. Refresh & versioning

- **USDA:** quarterly releases → re-import on a manual cadence; version the import (`import_batch`).
- **OFF:** on-demand cache stays fresh enough; optionally re-fetch a cached product if older than N months.
- Keep `import_batch` / `fetched_at` so analytics can pin to a data version and you can re-run mappings.

---

## 9. Phased rollout

| Phase | Deliverable | Effort |
|---|---|---|
| **0 (MVP)** | Bulk-load USDA SR Legacy as generic spine; OFF on-demand-cache for barcodes; `FoodGroup` enum + seeded `food_group_map`; P0 entity snapshots (§4); attribution line | low–med |
| **1** | `gramsConsumed` portion normalization (FNDDS measures); `dataCompleteness` + presence flags; food-group long-tail cleanup | med |
| **2** | Capture NOVA/Nutri-Score/additives/allergens on catalog (free from OFF); meal-level denormalized rollup; nutrition events project new fields | med |
| **3 (Full)** | USDA Foundation + Branded; full OFF bulk import + search infra; scheduled refresh + versioning | high |

**Capstone target = Phase 0 (+ bits of 1–2):** small clean USDA spine + lazy OFF barcode cache + a
hand-seeded food-group map. That already unlocks food-group balance, processing-level, and complete
macro analysis — with no million-row ETL.

---

## 10. Entity-change checklist (backward-compatible, additive only)

- `AbstractFoodLineItemEntity` (+ `MealItemEntity` via it): add `foodGroupCode`, `sourceCategoryCode`,
  `novaGroup`, `nutriScore`, `gramsConsumed`, `sourceDataset` — all nullable.
- `FoodCatalogItemEntity`: add `sourceDataset`, `sourceRef`, `foodGroupCode`, `novaGroup`, `nutriScore`,
  `dataCompleteness`.
- `CustomFoodEntity`: add `foodGroupCode`.
- New: `FoodGroup` enum, `FoodGroupMappingEntity`.
- `MealLoggedPayload` (event): add optional `foodGroupCalories` map + per-item group (project, don't persist anew).

All additive (new optional fields / new types) → existing consumers unaffected, per the contract-evolution
principles in `analytics-collaboration.md` §4.0.

---

## 11. Caveats to verify against your actual import

- The **completeness/coverage** of OFF micronutrients and NOVA in *your* imported snapshot varies by
  region — run a field-fill-rate stat on your data before committing to Phase 2 scope.
- Exact USDA/OFF **download URLs, dump formats, and FDC API key signup** should be confirmed against the
  current FDC and OFF docs at build time (formats drift).
- Confirm **OFF attribution wording** required by the current ODbL/OFF terms before release.
