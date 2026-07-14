# Recommendation / Analytics / Food-Data — Backlog

> Consolidated, actionable backlog for the recommendation initiative and everything it depends on.
> Branch-only (`claude/recommendation-interface-review-*`), **not for `main`**.
> Source docs: `README.md`, `analytics-collaboration.md`, `nutrition-data-plan.md`,
> `data-validation-runbook.md`.
>
> **Legend** — Status: ✅ done · 🟡 partial / already exists · ⬜ todo.
> Effort: S (≤½ day) · M (1–2 days) · L (3 days+). Scope: **MVP** (capstone) · Full (future).

---

## Current state (live)

The **food-data → snapshot → event → analytics** thread is complete and **merged to `main`**:

| PR | What landed on `main` |
|----|-----------------------|
| #3 | Food catalog provenance + `external_food_category_map` (food-group layer) + USDA importer |
| #4 | Meal-item classification snapshot, populated at meal-log time |
| #5 | `MealLogged` event carries `foodGroupCalories` breakdown (EV-1) |
| #6 | Analytics maintains a per-day food-group calorie aggregate (delta-safe) (AN-1) |

So **"meat-heavy / veg-light" is now a live analytics fact**. The next (and only remaining MVP) leg is
**building `recommendation-service` itself (EPIC RC)** — which lives on this branch and **never merges to
`main`**. See `recommendation-service/CLAUDE.md` for session-handoff context.

**RC progress (this branch):** RC-1 (scaffold: app + security + `recommendation_context`/`recommendation_result`
tables + CI), RC-8 (11 thin Kafka consumers → context projection + stale marking, idempotent via
`recommendation_processed_events`), RC-2 (rule engine: `service/rules` — R1 nutrition gap w/ TDEE
estimate, R2 weekly adherence, R3 goal pace w/ baseline capture at goal-set, R4 streak nudge, R5
cold-start; Flyway at V3), RC-4/RC-5 (`RecommendationResolver` cache→rule→template funnel with
context-hash change detection + TTL, 13-template `NarrativeTemplates` library; AI stage is a reserved
seam for RC-6), RC-7 (read API: `GET /recommendations`, `GET /{type}`, `POST /refresh` on the resolver)
and X-1 (gateway `recommendation-service-base-url` + `/api/v1/recommendations/**` proxy route — a
branch-only api-gateway change) are done. Next: **RC-6** (one live AI feature) + **X-4** (API key)
+ **X-3** (OFF attribution).

---

## MVP cut line (capstone)

Ship these and you have a demoable end-to-end thread (real open-data food → analytics fact →
recommendation → API) plus one live AI feature:
**FD-2, FD-3, FD-4, EV-1, AN-1, RC-1, RC-2, RC-4, RC-6 (one of A1/A4), X-1.**
Everything else is documented growth path.

---

## EPIC FD — Food data foundation (nutrition-service)

| ID | Item | Status | Effort | Scope | Depends |
|----|------|--------|--------|-------|---------|
| FD-0 | Provenance/diet-quality columns on `food_catalog_items` + `external_food_category_map` entity/repo + USDA importer skeleton + Flyway V13 | ✅ done | – | MVP | – |
| FD-0a | Existing OFF on-demand source (`ExternalFoodSource` / `OpenFoodFactsFoodSource`) | 🟡 exists | – | MVP | – |
| FD-0b | Existing curated `food_categories` taxonomy + `NutritionTargetEntity` (calorie/macro targets) | 🟡 exists | – | MVP | – |
| FD-1 | Validate the actual OFF import: fill-rate stats (DuckDB on Parquet / your snapshot), per `data-validation-runbook.md` | ⬜ todo | S | MVP | – |
| FD-2 | Seed `external_food_category_map`: USDA SR categories + OFF tags → existing `food_categories` leaf codes (Flyway V14 + importer test) | ✅ done | – | MVP | FD-0 |
| FD-3 | USDA SR Legacy CSV parser (RFC-4180, no new dep) + guarded one-time load runner (`fitsync.nutrition.usda-import.directory`) + tests | ✅ done | – | MVP | FD-0, FD-2 |
| FD-4 | Capture `nova_group`/`nutri_score`/`source_dataset`/`source_ref` on the OFF barcode-import path (fetch + parse from OFF, stamp at persist) + tests | ✅ done | – | MVP | FD-0, FD-0a |
| FD-11 | Unify: OFF source's hardcoded tag→code mapping (`OpenFoodFactsFoodSource.categoryCode`) should use the DB `external_food_category_map` (single source of truth) | ⬜ todo | S | Full | FD-2, FD-4 |
| FD-5 | Persist per-food nutrient profile + servings in the USDA importer (energy kJ↔kcal, salt↔sodium normalization) | ⬜ todo | M | Full | FD-3 |
| FD-6 | `gramsConsumed` portion normalization at log time (USDA FNDDS measures / OFF serving_quantity) | ⬜ todo | M | Full | FD-5 |
| FD-7 | Compute & store `data_completeness`; reject sub-threshold records on import | ⬜ todo | S | Full | FD-3 |
| FD-8 | Split `PRODUCE` taxonomy into VEGETABLE vs FRUIT (so "veg-light" is distinguishable from fruit) | ⬜ todo | S | Full | – |
| FD-9 | Capture OFF additives/allergens/ingredient-analysis for dietary-preference filtering | ⬜ todo | M | Full | FD-0a |
| FD-10 | Refresh/versioning: `import_batch` tagging; USDA quarterly re-import; OFF cache staleness re-fetch | ⬜ todo | M | Full | FD-3 |

## EPIC SNAP — Meal-item classification snapshot (the 🔴 entity fix)

| ID | Item | Status | Effort | Scope | Depends |
|----|------|--------|--------|-------|---------|
| SNAP-1 | Add nullable snapshot cols to `AbstractFoodLineItemEntity` (`category_code`, `nova_group`, `nutri_score`, `grams_consumed`, `source_dataset`); Flyway V15 alters both meal_items + meal_template_items | ✅ done | – | MVP | FD-2 |
| SNAP-2 | `CustomFoodEntity.category_code` + Flyway V16 (custom foods had no category) | ✅ done (#4) | – | MVP | SNAP-1 |
| SNAP-3 | Populate the snapshot at meal-log time in `NutritionServiceImpl` (resolve item → catalog/custom → category); grams_consumed deferred to FD-6 | ✅ done (#4) | – | MVP | SNAP-1 |

## EPIC EV — Event-contract enrichment (project existing data into events)

| ID | Item | Status | Effort | Scope | Depends |
|----|------|--------|--------|-------|---------|
| EV-1 | Widen `MealLoggedPayload`: optional food-group calorie breakdown (backward-compat constructor + golden samples) | ✅ done (#5) | – | MVP | SNAP-3 |
| EV-2 | Widen `WorkoutLoggedPayload`: project existing `totalVolumeKg` + per-muscle set counts (already in entities) | ⬜ todo | M | Full | – |
| EV-3 | user-service: emit `EnergyProfileComputed` (BMR/TDEE) — keeps age/sex PII inside user-service | ⬜ todo | M | Full | – |
| EV-4 | Confirm `GoalUpdated`/nutrition target carries usable calorie/macro target downstream (NutritionTarget already exists) | 🟡 partial | S | MVP | – |

## EPIC AN — Analytics evolution (descriptive→diagnostic facts; never prescriptive)

| ID | Item | Status | Effort | Scope | Depends |
|----|------|--------|--------|-------|---------|
| AN-0 | analytics fully consumes nutrition/workout/user main events into a derived read model | 🟡 partial | L | MVP | – |
| AN-1 | Analytics consumes `foodGroupCalories` → maintains a delta-safe per-day food-group calorie aggregate (read model) | ✅ done (#6) | – | MVP | EV-1 |
| AN-2 | `TrainingLoadComputed` fact (volume trend, **ACWR** over/under-training, muscle coverage) | ⬜ todo | M | Full | EV-2 |
| AN-3 | `EnergyBalanceComputed` fact (intake−expenditure vs goal direction) — cross-domain | ⬜ todo | M | Full | EV-3 |
| AN-4 | Goal pace (progress vs target/date) | 🟡 exists (`AnalyticsUserGoalProgress` / `GoalProgressEvaluator`) | – | MVP | – |
| AN-5 | "Ultra-processed share" diagnostic (from `nova_group`) | ⬜ todo | S | Full | FD-4 |

## EPIC RC — recommendation-service build

| ID | Item | Status | Effort | Scope | Depends |
|----|------|--------|--------|-------|---------|
| RC-0 | Module is an empty scaffold (`pom.xml` only) | ✅ superseded by RC-1 | – | – | – |
| RC-1 | Scaffold: main class, package skeleton, `recommendation_context` + `recommendation_result` tables | ✅ done (this branch) | – | MVP | – |
| RC-2 | Local rule engine R1–R5 (nutrition gap, weekly adherence, goal pace, streak nudge, cold-start) | ✅ done (this branch) | – | MVP | AN-1/AN-4 |
| RC-3 | Rules R6–R8 (PR celebration, focus-area balance, logging reminder) | ⬜ todo | M | Full | EV-2 |
| RC-4 | `RecommendationResolver` funnel: cache → rule → template → AI | ✅ done (this branch; AI seam reserved for RC-6) | – | MVP | RC-2 |
| RC-5 | Hand-written narrative template library (10–20 templates, rule numbers fill slots) | ✅ done (this branch; 13 templates in `NarrativeTemplates`) | – | MVP | RC-2 |
| RC-6 | One live AI feature — A1 weekly narrative **or** A4 coach Q&A (async, prompt-cached, model-tiered, rule fallback) | ⬜ todo | M | MVP | RC-4 |
| RC-7 | Read API: `GET /recommendations`, `GET /{type}`, `POST /refresh` | ✅ done (this branch) | – | MVP | RC-1 |
| RC-8 | Kafka consumers (group `recommendation-consumer-group`) → update context + mark stale (never trigger AI per-event) | ✅ done (this branch) | – | MVP | RC-1 |
| RC-9 | A2/A3/A5 AI features (workout plan, meal ideas, rationale) | ⬜ todo | L | Full | RC-6 |
| RC-10 | Advanced cost/perf: retrieval-instead-of-generation, model cascade, Batch API, design-time distillation | ⬜ todo | L | Full | RC-9 |

## EPIC X — Cross-cutting / ops

| ID | Item | Status | Effort | Scope | Depends |
|----|------|--------|--------|-------|---------|
| X-1 | Gateway: add `recommendation-service-base-url` + route `/api/v1/recommendations/**` (reserved, not wired) | ✅ done (this branch — gateway change stays branch-only with the rest) | – | MVP | RC-7 |
| X-2 | CI: add `recommendation-service` to the build (currently excluded as empty placeholder) | ✅ done with RC-1 (reactor `verify` builds + tests it; deliberately not packaged as an image — X-5) | – | MVP | RC-1 |
| X-3 | OFF attribution line (ODbL: "Data from Open Food Facts") in app/about + provenance | ⬜ todo | S | MVP | FD-0a |
| X-4 | `ANTHROPIC_API_KEY` config/secret wiring for the AI tier | ⬜ todo | S | MVP | RC-6 |
| X-5 | Decide monolith-first vs standalone deployment for recommendation (per kafka-arch §18) | ⬜ todo | S | – | – |

---

## Recommended sequence (dependency-ordered)

1. **FD-1** (validate data — cheap, de-risks everything) — *you run this; only you have the DB.*
2. **FD-2 → FD-3 → FD-4** (food-group mapping seed + USDA load + OFF signal capture).
3. **SNAP-1 → SNAP-2 → SNAP-3** (meal-item classification snapshot — the 🔴 fix).
4. **EV-1 → AN-1** (project food-group into the event → analytics computes the balance fact).
5. **RC-1 → RC-8 → RC-2 → RC-4/RC-5 → RC-7 → X-1/X-2** (recommendation scaffold → rules → funnel → API → wire-up).
6. **RC-6 + X-4 + X-3** (one live AI feature + key/attribution).
7. Then Full-scope items as time allows.

> This sequence delivers the MVP cut line as one continuous thread; each step is independently
> commit-able and testable.
