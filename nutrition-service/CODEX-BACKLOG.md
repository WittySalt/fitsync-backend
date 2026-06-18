# Nutrition-Service Implementation Backlog (for Codex)

> **Status (2026-06-19):** Being implemented directly by Claude (Codex quota nearly exhausted), not
> Codex. **DONE:** NU-1 (recent/frequent foods); NU-12 demo seed (`V7` — 40 curated foods); NU-15
> category taxonomy (`V8` — `food_categories` tree + `GET /categories` + subtree/exact filter on
> `/foods/search`). Per the owner, **food corpus + search + category browse was pulled ahead of the
> rest of the fast-logging layer (NU-2..5)**. Revised order: NU-1,12,15(done) → NU-13(search
> ranking/pagination) → NU-7(recipe) → NU-2..6 → NU-8..11. **Next Flyway migration is now V9.**
> Prod = PostgreSQL, tests = H2 `MODE=PostgreSQL` (no `pg_trgm`/full-text — gates NU-14).


<!-- ────────────────────────────────────────────────────────────────────────
     KICKOFF PROMPT — copy the block below verbatim to hand this backlog to Codex.
     ──────────────────────────────────────────────────────────────────────── -->
> **Kickoff prompt for Codex:**
>
> You implemented the FitSync `backend/nutrition-service`. I've written a ratified implementation
> backlog at `backend/nutrition-service/CODEX-BACKLOG.md` (tasks NU-1 through NU-11, with
> conventions, acceptance criteria, and execution order). Read it in full before starting.
>
> Start with the P0 fast-logging tasks in order: NU-1 → NU-2 → NU-3 → NU-4 → NU-5. They are
> independent of each other but build the logging-convenience surface together; do not jump to
> P1+ until P0 is done and green.
>
> Working rules:
> - Follow the conventions at the top of the backlog (DTOs as `record`s, entities extend
>   `BaseAuditableEntity`/`BaseEffectiveEntity`, JWT `CurrentUserPrincipal` scoping, Flyway
>   `V{n}__` migrations — next is **V9** (V1-V8 applied), mirrored tests).
> - Reuse existing patterns — outbox+relay, item-snapshot writing in `replaceMealItems`, and
>   catalog entry via `cloneFood` already exist. Do NOT hand-roll a new producer or a second
>   meal-write path.
> - **Respect the ratified service boundary (see Context):** nutrition owns "today/now"
>   strong-consistency views only. Do NOT add cross-period/range aggregation or trend endpoints —
>   those belong to analytics-service. If a task seems to need historical trends, stop and flag it.
> - One task = one focused commit (or PR). After each task: write/extend unit tests, run
>   `mvn -pl nutrition-service test`, confirm green before moving on.
> - For NU-8 (external food API) and NU-11 (AI parsing), confirm outbound HTTP / API-key
>   availability before coding; if unavailable, mark blocked rather than guessing.
> - If any acceptance criterion is ambiguous or conflicts with existing code, pause and ask
>   before implementing.
>
> Begin with NU-1. Show me your plan for it (files to add/change, the new repository queries, and
> the test cases) before writing code.

---

> **Context.** `backend/nutrition-service` is the transactional **write** side for food logging.
> Today it has: Meal CRUD + time-range/mealType filter + latest; Custom-food CRUD; food catalog
> search/detail/servings/barcode + `cloneFood` (GLOBAL→USER private copy); single-day
> `GET /summary/daily` (4 macros, intake vs target vs remaining); a target read-model projected
> from goal-service `GoalUpdated`; a preference projection; and an Outbox relay emitting
> `MealLogged`/`MealUpdated`/`MealDeleted`. `meal_item` rows snapshot food/brand/serving + macros +
> micros; `FoodNutrientProfileEntity` already stores fiber/sugar/sodium/potassium/cholesterol/sat/
> trans fat (**captured but not surfaced in the summary**). `MealItemInput.externalFoodRef` is
> reserved but unimplemented.
>
> **Ratified service boundary (do NOT violate).** Trend ownership is split by *time window +
> consistency need*, not feature name. **nutrition owns "today/now"** strong-consistency views
> (single-day reads on the transactional DB). **analytics-service owns "yesterday-and-before"**
> cross-period analysis (weekly/monthly trends, averages, on-target days, distribution, compare —
> eventual-consistency, pre-aggregated; it consumes the Meal events). Therefore: **nutrition's
> `summary` stays single-day and gains NO range aggregation.** Cross-period UI calls analytics.
>
> **Already-true fact that reshapes NU-5.** A manual no-catalog meal item ALREADY works:
> `replaceMealItems` accepts an item with no `foodCatalogItemId`/`customFoodId` as long as
> `foodName` + `unit` + `calories` are present (`NutritionServiceImpl.java:757-763`). So NU-5 is a
> thin convenience wrapper, **not** a constraint-relaxation prerequisite — nothing else depends on
> it.
>
> **Conventions to follow (do not reinvent):**
> - DTOs are `record` types in `com.fitsync.nutrition.dto`.
> - Entities extend `BaseAuditableEntity` (mutable) or `BaseEffectiveEntity` (effective-dated
>   catalog data), from the persistence support module.
> - Outbound event contracts live in the `contracts`/`event-contracts` module under
>   `com.fitsync.contracts.events.nutrition.*`. The **outbox + relay** pattern already exists here
>   (`OutboxEventEntity`, `OutboxDomainEventPublisher`, `OutboxRelayScheduler`,
>   `SpringKafkaEventProducer`, `FallbackKafkaEventProducer`) — reuse it for any new event.
> - Meal writes go through `replaceMealItems` which snapshots nutrition onto each item — reuse it;
>   do not create a parallel write path.
> - Flyway migrations live in `src/main/resources/db/migration` (next is **V9**; V7 = food seed, V8 = categories). Never edit an
>   applied migration.
> - Tests mirror `src/main` into `src/test`. Each task keeps `mvn -pl nutrition-service test` green.
> - All endpoints are user-scoped via JWT (`CurrentUserPrincipal`); every query must filter by
>   `userId`, every owned-resource fetch must 404 on another user's id (mirror `getOwnedMeal`).

**Recommended execution order:** NU-1 → NU-2 → NU-3 → NU-4 → NU-5 (fast logging) → NU-6
(nutrition depth) → NU-7 (recipe) → NU-9 → NU-10 (small wins) → NU-8 (external API) → NU-11 (AI).

---

## P0 — Fast logging (highest ROI; pure recombination of existing meal_item data)

### NU-1 · Recent / frequent foods
**Problem.** Repeat logging is the 80% case, but every entry requires a fresh catalog search +
re-entering the serving. There is no "log what I had before" surface.

**Changes.**
- `GET /api/v1/nutrition/foods/recent` and `/foods/frequent` (param `limit`, sane default/cap).
- Derive from the user's `meal_items`: dedup by food identity (prefer `foodCatalogItemId`, else
  `customFoodId`, else `externalFoodRef`, else normalized `foodName`); `recent` ranks by latest
  `consumedAt` (join via `meal_logs`), `frequent` by occurrence count.
- Each result carries the **last-used** serving/quantity/unit + macro snapshot so the frontend can
  one-tap re-log. Reuse an existing view DTO (`FoodSearchResultView`) or add a focused record.
- Read-only; new repository queries only. **No migration.**

**Acceptance.** Only the caller's items appear; dedup collapses repeats to one row carrying
last-used serving; `frequent` ordering matches occurrence count; empty history returns `[]`.

### NU-2 · Favorites
**Changes.**
- New table `food_favorites` (`user_id`, polymorphic food ref — store ref type + id covering
  catalog/custom/external, `created_at`); unique per (user, food ref). New migration (V8+).
- `POST /foods/{foodId}/favorite`, `DELETE /foods/{foodId}/favorite`, `GET /favorites`.
- Decide and document the favorite-target representation (a catalog/custom food vs an arbitrary
  logged item) — keep it consistent with how NU-1 identifies a food.

**Acceptance.** Favoriting is idempotent (no duplicate rows); unfavorite is 204; `GET /favorites`
is user-scoped and returns enough to re-log without another lookup.

### NU-3 · Meal templates ("saved meals")
**Problem.** Fixed breakfasts/snacks are re-entered item-by-item every day.

**Changes.**
- New tables `meal_templates` + `meal_template_items` mirroring `meal_logs`/`meal_items` **minus**
  `consumed_at` (templates are time-less). Migration.
- `POST /meal-templates` (accept an inline item list **and** a "save from existing meal"
  `fromMealId` mode), `GET /meal-templates`, `GET /meal-templates/{id}`, `DELETE`.
- `POST /meals/from-template/{id}` — materializes the template into a real meal at a supplied
  `consumedAt` (+ optional `mealType` override), reusing the `replaceMealItems` snapshot path so
  `MealLogged` fires exactly as a normal create.

**Acceptance.** Save-from-meal copies all items with their snapshots; from-template create emits
`MealLogged` and is byte-for-byte equivalent to a manual create of the same items; templates are
user-scoped (404 on another user's template).

### NU-4 · Copy meal / copy day
**Changes.**
- `POST /meals/{mealId}/copy` (body: target `consumedAt`, optional `mealType`) — clones a meal +
  its item snapshots to a new meal id.
- `POST /days/{date}/copy-to` (body: target `date`, `timezone`) — copies all meals of the source
  day to the target day, preserving per-meal time-of-day.
- Reuse the existing snapshot write path; emit `MealLogged` per created meal. **No migration.**
- Handle timezone exactly as `getDailySummary` does (`resolveZone`, day window in the user's zone).

**Acceptance.** Copied meals are independent rows (editing the copy doesn't touch the source);
each emits `MealLogged`; copying an empty day is a no-op (not an error); cross-DST day handled.

### NU-5 · Quick-add convenience endpoint
**Note.** Manual no-catalog items already work (Context). This task only adds ergonomics.

**Changes.**
- `POST /api/v1/nutrition/meals/quick-add` — single call taking `calories` (+ optional
  protein/carbs/fat, `mealType`, `consumedAt`, `name`) and creating a meal with one synthetic
  item. For a pure-calorie entry, default the synthetic item's `foodName` (e.g. "Quick add") and
  `unit` (e.g. "serving") so the caller isn't forced to supply them.
- Route through `replaceMealItems`; emit `MealLogged` unchanged.

**Acceptance.** kcal-only body succeeds and produces a valid single-item meal + `MealLogged`;
optional macros flow through; validation still rejects negative/zero-quantity nonsense.

---

## P1 — Nutrition depth (data already in the DB; low cost / high return)

### NU-6 · Surface micronutrients + daily limits in the summary
**Problem.** `meal_item` already stores `fiberGrams`/`sugarGrams`/`sodiumMg`, but `calculateTotals`
+ the `Totals` record (`NutritionServiceImpl.java:~984`) only sum the 4 macros and
`DailyNutritionSummaryView` only exposes them. Captured data is invisible.

**Changes.**
- Extend the `Totals` record + `calculateTotals` to also sum fiber/sugar/sodium; add those totals
  to `DailyNutritionSummaryView` (**additive** fields only).
- Add configurable DRI/WHO default daily references (fiber target, sugar/sodium upper limits) and
  emit per-nutrient **over/under** flags for the day. Keep nulls when no reference is configured.
- This stays single-day — does **not** breach the analytics boundary.

**Acceptance.** Totals match the sum of the day's item snapshots; over-limit flag trips at the
configured threshold; absent reference → null flag, not false. Coordinate the additive DTO change
with the frontend (see [[project-frontend-uiux]]).

---

## P1/P2 — Differentiator

### NU-7 · Recipe (multi-ingredient composed food)
**Problem.** Custom foods are single-nutrition blobs; users who cook can't compose from
ingredients.

**Changes.**
- New tables `recipes` + `recipe_ingredients` (each ingredient refs a catalog/custom food +
  quantity/unit); recipe carries `servings`. Migration.
- Auto-aggregate nutrition from ingredients; expose **per-serving** values. Decide and document
  recompute-on-read vs materialized totals (note the choice).
- Make a recipe usable as a single food in a meal — materialize a USER-scoped catalog entry
  (reuse the `cloneFood`/catalog-entry path) or surface it as a custom food; keep one consistent
  representation.
- CRUD: `POST/GET/PUT/DELETE /recipes`, scaling by serving count.

**Acceptance.** Aggregated nutrition equals the sum of ingredient contributions at the chosen
basis; scaling servings scales totals linearly; logging a recipe into a meal snapshots its
nutrition like any other item and emits `MealLogged`.

---

## P2/P3 — Deferred (per ratified priority)

### NU-8 · External food DB fallback (OpenFoodFacts / USDA)
**Changes.** Wire the reserved `externalFoodRef`. When local catalog search/barcode misses, fall
back to an external provider via a client (config-driven provider + base URL + key; cache +
rate-limit). On selection, persist the chosen food into the catalog via the existing
clone/catalog-entry path so subsequent logging is local. **Confirm outbound HTTP is permitted
before coding;** if not, mark blocked.

**Acceptance.** Local hit never calls out; miss returns external candidates clearly tagged as
external; selecting one creates a reusable catalog item; provider failure degrades to "no results"
rather than a 5xx.

### NU-9 · Water / hydration tracking
**Changes.** New table `water_logs` (`user_id`, `amount_ml`, `logged_at`, `timezone`). Migration.
`POST /water`, `GET /water?date=` (+ daily total, timezone-aware like the summary). Optional: fold
a `waterMl` total into the daily summary (additive).

**Acceptance.** Daily total respects the user's timezone day window; user-scoped; negative amounts
rejected.

### NU-10 · Food-diary export
**Changes.** `GET /api/v1/nutrition/export?from&to&format=csv|pdf` — the user's meals + items over
a range. Apply a range cap (mirror analytics AN-3 style). PDF via the existing document tooling if
available, else CSV first.

**Acceptance.** Export is user-scoped; over-cap range returns 400; CSV columns documented.

### NU-11 · AI logging (natural language / photo)
**Changes.** `POST /api/v1/nutrition/meals/ai-parse` — accepts NL text ("2 eggs and a slice of
toast") or an image reference and returns a **parsed draft** of meal items (NOT auto-saved) via
Claude structured output. Must be **async / separate endpoint — never inside the synchronous
`createMeal` path.** Use the latest Claude model (see the Claude API reference). **Confirm API key
/ availability before coding;** if absent, mark blocked.

**Acceptance.** Returns a draft the user confirms before it goes through the normal create path;
parsing failure returns a clean error, never a partial save; the synchronous logging path is
untouched.

---

## Food corpus + search (pulled forward — the "tasty fitness meals + ensure nutrition" goal)

> **Grounded facts (verified).** The catalog *schema* is already rich (category_code, brand,
> verified, scope, source, density, effective-dates, multi-serving, full macro+micro profile,
> nutrient_details). The gap was **data volume** (only 2 seed foods), not the model. Prod DB is
> **PostgreSQL**; tests run on **H2 `MODE=PostgreSQL`** which has no `pg_trgm`/full-text.
>
> **Boundary (do NOT violate; mirrors the today-vs-trends split).** `recommendation-service` is an
> empty shell. Meal *suggestion* ("what to eat today") belongs THERE, consuming nutrition's corpus +
> recipes + the user's targets/preferences (`dietary_preference_code` is already projected into
> nutrition). nutrition provides the **bricks** (food corpus + recipe primitives); recommendation
> **builds the house**. Do not put meal recommendation inside nutrition-service.

### NU-12 · Food catalog corpus expansion
**Problem.** With ~2 seeded foods, search/logging/recipe flows have nothing to chew on; the core
loop can't be demonstrated.

**Done so far.** `V7__seed_food_catalog_expansion.sql` — 40 curated generic foods (USDA FoodData
Central per-100g basis), each with one "100 g" default serving + a per-100g nutrient profile, GLOBAL/
OFFICIAL/verified, across the main fitness categories. Demo-grade.

**Next (scale).** Build a one-off **USDA FoodData Central importer** (Foundation Foods + SR Legacy
subsets — public domain, generic ingredients). Map: USDA per-100g nutrients → `food_nutrient_profiles`
(basis 100 g); USDA household measures → `food_servings`; USDA category → `category_code`. Ship as a
profile-gated `CommandLineRunner` / CLI (not in the test boot path); the dataset file stays out of the
committed migrations (large). Keep idempotency (skip existing `food_id`/source keys).

**Acceptance.** Importer is idempotent and re-runnable; bad rows are skipped + logged, not fatal;
seeded foods are searchable via the existing `/foods/search`.

### NU-15 · Category taxonomy (e-commerce-style hierarchical browse/filter) — DONE
**Done.** `V8__create_food_categories.sql` — `food_categories` adjacency-list tree (parent_id +
denormalized materialized `path`) with a curated 2-level taxonomy (6 groups → 12 subcategories whose
`code` matches the existing `food_catalog_items.category_code`, so **no food backfill**). Owned by
nutrition-service (food master data; `recommendation-service` is the consumer, not the owner).
`FoodCategoryEntity`/`FoodCategoryRepository`/`FoodCategoryView`; `GET /api/v1/nutrition/categories`
returns the nested tree; `/foods/search?categoryId=&includeSubcategories=` filters by subtree
(`path LIKE :prefix%`, portable H2+PG) or exact code. Curated/fixed — users cannot create
categories; `active`+audit columns are kept so an admin maintenance API (**NU-15b**, deferred) can be
added without schema change.

**Follow-ups (NU-15b, deferred):** admin category CRUD; add `category_code`/`category_id` to
custom_foods + `CreateCustomFoodRequest` so user-created foods can be categorized ("add by category").

### NU-13 · Search pushdown: pagination + relevance ranking + filters (H2-safe, no new deps)
**Problem.** `searchAccessibleFoods` does `LIKE %q%` on name, orders owner→verified→name, and the
service truncates to 20 in Java (`.limit(FOOD_SEARCH_LIMIT)`) — no real pagination, no relevance, no
filters.

**Changes.**
- Push pagination into SQL (`Pageable` / limit+offset); return enough for the client to page (total
  or hasMore). Stop the Java-side `.limit`.
- Relevance ranking via portable `CASE`: exact-name match > prefix match > substring; tie-break by
  verified desc, global-vs-owner, name. Must work on both H2 and Postgres.
- Optional filters: `brand`, `verified`, `scope` as query params (additive). **Category filtering is
  already done in NU-15** (`categoryId` + `includeSubcategories`).

**Acceptance.** Paging returns stable, non-overlapping pages; exact/prefix matches rank above
substring; filters narrow correctly; empty query still works; existing search test stays green.

### NU-14 · Fuzzy / typo-tolerant search (Postgres-only — DEFER)
**Changes.** Add `pg_trgm` similarity + a GIN index (`create extension pg_trgm` in a migration) so
"yogrt"→"yogurt". **H2 cannot run this**, so either move the affected tests to **Testcontainers
Postgres** or guard the trigram path behind a dialect check. Defer until the corpus is large enough
that ranking/typo-tolerance actually matters.

**Acceptance.** Misspelled queries return the intended food above noise; non-Postgres path degrades
to NU-13 behavior; trigram tests run on a real Postgres.

## Definition of done (every task)
- `mvn -pl nutrition-service test` green; new logic has unit tests; owned-resource endpoints have
  a cross-user 404 test.
- New endpoints documented with OpenAPI annotations matching the existing controller style.
- Schema changes ship as a new `V{n}__...sql` Flyway migration (next is V9; never edit an applied
  one).
- Backward-compatible DTO changes only (add fields; don't remove/rename) — especially the NU-6
  summary change.
- No cross-period/range aggregation added to nutrition (analytics owns trends).
