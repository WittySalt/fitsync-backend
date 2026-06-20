# NU-8b — Persist & expand the external (OpenFoodFacts) food source (for Codex)

<!-- Kickoff prompt — copy the block below verbatim to hand this to Codex. -->
> **Kickoff prompt for Codex:**
>
> In `backend/nutrition-service` there is a read-only OpenFoodFacts barcode fallback (NU-8):
> `external/ExternalFoodSource` + `external/OpenFoodFactsFoodSource` (Spring `RestClient`),
> `config/ExternalFoodProperties` (`fitsync.nutrition.external-food.*`, **disabled by default**), wired
> into `NutritionServiceImpl.getFoodByBarcode` — on a local-catalog miss it returns a **non-persisted**
> `FoodDetailView` (`foodId == null`, source `IMPORTED`). Complete it per the spec in
> `nutrition-service/NU-8B-EXTERNAL-FOODS-TASK.md` (read it fully first).
>
> Working rules:
> - Reuse the existing `ExternalFoodSource`/`OpenFoodFactsFoodSource`; do NOT hand-roll a new client.
> - Persist external foods using the SAME entity pattern the catalog already uses (mirror `cloneFood`
>   in `NutritionServiceImpl`): `food_catalog_items` + `food_nutrient_profiles` + `food_servings`
>   (+ `food_barcodes`); no new tables/migrations needed.
> - **SAST gate (this bit it me): run `mvn -pl nutrition-service -am test` AND
>   `mvn -pl nutrition-service spotbugs:check` (effort=Max/threshold=Low) before pushing.** Never log
>   user input or `ex.toString()` (CRLF_INJECTION_LOGS — use `log.warn("msg", throwable)`); never
>   case-fold (`equalsIgnoreCase`/`toLowerCase`) in a validation decision (IMPROPER_UNICODE).
> - The real OFF source is disabled in tests — stub `ExternalFoodSource` with a test bean (mirror how
>   `OpenFoodFactsFoodSourceTests` builds a source) so tests never hit the network.
> - One task = one focused commit. Show your plan (files, endpoints, persistence flow, tests) before
>   coding.

---

## Context (already shipped — NU-8)
- `ExternalFoodSource.findByBarcode(barcode)` → `Optional<FoodDetailView>`; no-ops (empty) when disabled
  or on any error.
- `OpenFoodFactsFoodSource` maps OFF per-100g nutriments → a `FoodDetailView` (foodId null, basis 100 g,
  brand, calories+macros+fiber/sugar/sodium). `toFoodDetailView(JsonNode, barcode)` is unit-tested.
- `getFoodByBarcode`: local barcode hit → catalog food; local miss → `externalFoodSource.findByBarcode`
  → else 404.
- Catalog persistence shape (see `cloneFood` + V7/V8/V13 seeds): `food_catalog_items`
  (scope/source/display_name/normalized_name/category_code/default_serving_id/verified/effective dates),
  `food_nutrient_profiles` (basis 100 g + calories/macros/micros), `food_servings` ("100 g" default),
  `food_barcodes`, category taxonomy `food_categories` (V8 + the `OTHER` group from V13).

## Goal
Turn the ephemeral external lookup into a **growing catalog** + make search fall back to OFF.

### NU-8b.1 — Persist an external hit into the catalog (write-through / import)
- Add a way to **import** an external food so it becomes a real, reusable catalog entry with a real
  `foodId`. Prefer a **dedicated endpoint** `POST /api/v1/nutrition/foods/import/barcode/{barcode}`
  (a write on a GET is a smell) that: fetches via `ExternalFoodSource`, and if found, persists a
  `food_catalog_items` (scope `GLOBAL`, source `IMPORTED`, verified=false) + per-100g
  `food_nutrient_profiles` + a "100 g" `food_servings` + a `food_barcodes` row, then returns the
  persisted `FoodDetailView` (real `foodId`). 404 when not found / disabled.
- **Idempotent / no dupes:** derive a deterministic `food_id` from the OFF barcode (or check
  `food_barcodes` for the barcode first) — re-importing the same barcode returns the existing food, does
  not insert twice.
- **Category mapping:** map OFF `categories_tags`/category to a V8 `category_code` best-effort; fall back
  to a sensible default (e.g. the `OTHER`/uncategorised group) so the food is still browsable.
- After import, `getFoodByBarcode` for that barcode must hit **locally** (no external call).

### NU-8b.2 — Search fallback (secondary)
- When `GET /foods/search` has a non-blank `q` and the local result count is below a small threshold,
  also query OFF search (`ExternalFoodSource` gains a `search(query, limit)` method) and append the
  external results, clearly distinguishable (e.g. `foodId == null` / source `IMPORTED`). Disabled-source
  ⇒ no change. Keep pagination semantics sane.

### NU-8b.3 — Ops
- Stays **disabled by default**; document enabling on the online env via
  `fitsync.nutrition.external-food.enabled=true` (needs outbound HTTP to OpenFoodFacts). Add a short note
  to the service README. Add a small per-host rate-limit / timeout guard if not already present.

## Acceptance
- Import persists a catalog food (mocked `ExternalFoodSource`); re-import is idempotent (one row);
  post-import barcode lookup is served locally; disabled source ⇒ import 404 and search unchanged.
- `mvn -pl nutrition-service -am test` green; `spotbugs:check` 0 bugs; new endpoints documented with
  OpenAPI annotations matching the existing controller style; no new Flyway migration required.
