# recommendation-service — session context (read me first)

You are continuing the **recommendation-service** build. Everything you need is in this directory's
docs; the conversation that produced them is gone, so treat these files as the source of truth.

## ⛔ Iron rule

- **recommendation-service work lives ONLY on branch `claude/recommendation-interface-review-0x4pzv`
  and NEVER merges to `main`.** (Planning docs + the RC module code stay here by design.)
- **nutrition-service / analytics-service / event-contracts** changes DO go to `main` — but via a
  **separate branch cut from `main`** (one clean commit, PR, poll-merge). Never PR this branch to `main`.

## What this is

A personalization layer: consume analytics/domain events → keep a per-user context → produce
recommendations, mostly with a **local rule engine + templates**, selectively with **one AI feature**.
Full design + rationale: `README.md`. Cost/perf strategy is in `README.md` §5 (rule-first funnel;
AI is async, cached, model-tiered, with graceful fallback — never on the read path, never per-event).

## Design docs (this directory)

| File | What it is |
|---|---|
| `README.md` | Functional plan: feature catalog (R1–R8 local, A1–A5 AI), cost/perf strategy, API, package layout |
| `BACKLOG.md` | **The task tracker — start here.** Prioritized, with statuses + "Current state" + MVP cut line |
| `HANDOFF.md` | **Delivery checkpoint** — what shipped (commits), invariants, gaps/limitations, how to run, next moves |
| `analytics-collaboration.md` | analytics↔recommendation division of labor + derived-fact contracts |
| `nutrition-data-plan.md` | open-data food DB build plan (USDA + OFF) — mostly delivered on `main` |
| `data-validation-runbook.md` | how to validate the OFF import (FD-1, user's task) |

## Current state (already on `main`)

The **food-data → snapshot → event → analytics** thread is done (PRs #3–#6): open-data food
classification → meal-item category snapshot at log time → `MealLogged.foodGroupCalories` →
analytics per-day food-group aggregate. So **"meat-heavy / veg-light" is a live analytics fact**.

**Upstream data you can consume** (from analytics-service + event-contracts):
- Events: `MealLogged` (now with `foodGroupCalories`), `WorkoutLogged`, `GoalUpdated`,
  `BodyMetricsRecorded`, `StreakMilestoneReached`, `WeeklyRecapGenerated`, `UserPreferencesUpdated`, …
- Analytics read model / facts: daily nutrition (+ food-group aggregate), goal progress, dashboard,
  weekly recap, streaks.

## What to build next — EPIC RC (see BACKLOG)

**Done on this branch:** `RC-1 scaffold` (main class, packages, `recommendation_context` +
`recommendation_result` tables, CI); `RC-8 thin Kafka consumers` (group
`recommendation-consumer-group`; 11 listeners in `consumer/` → handlers in `handler/` update the
context + mark stale, idempotent via `recommendation_processed_events`); `RC-2 local rules R1–R5`
(`service/rules`: Rule/RuleOutcome/RuleEngine + NutritionGap (TDEE est.), WeeklyAdherence, GoalPace
(baseline captured at goal-set), StreakNudge, ColdStart — pure functions over the context, facts only);
`RC-4/RC-5` (`service/RecommendationResolver`: cache(fresh)→rule→template funnel, payload =
{facts, narrative}, context-hash change detection + `fitsync.recommendation.rule-result-ttl`, prunes
unfired rule results; `service/rules/NarrativeTemplates`: 13 hand-written templates); `RC-7 read API`
(`controller/RecommendationController`: GET /api/v1/recommendations, GET /{type} (case-insensitive,
404 via web-support), POST /refresh → 202); `X-1` (api-gateway: `recommendation-service-base-url`
(:8086) + `/api/v1/recommendations/**` proxy — the gateway change is branch-only like everything
else here); `RC-6 + X-4` (A1 weekly narrative in `service/ai`: `ClaudeClient` seam →
`AnthropicClaudeClient` (`anthropic-java`, key via `fromEnv()`, prompt-cached system prompt,
model/`max-tokens`/TTL under `fitsync.recommendation.ai.*`, default `claude-sonnet-4-6`, enabled=false
by default) + `WeeklyNarrativeService` (enqueued by the recap handler + resolver refresh, hash-gated,
P7D TTL, silent fallback to rule/template output — the read path never touches AI)). Flyway at V3.

**MVP cut line: everything done except X-3** (OFF attribution) — X-3 is main-bound
(nutrition/frontend surface), so it needs the separate-branch → PR-to-`main` flow below, NOT this
branch. Remaining RC work is Full-scope (RC-3, RC-9, RC-10).

The AI tier uses the **Claude API via the `anthropic-java` SDK**; default model `claude-opus-4-8`,
tier down to `claude-sonnet-4-6` / `claude-haiku-4-5` for cheaper tasks (README §4.1). Keep AI async +
cached + rule-fallback.

## House conventions (learned — follow them)

- **Flyway + Hibernate `ddl-auto: validate`**: every new entity column needs a matching Flyway migration
  or the app won't start. Migrations are `Vn__*.sql`; nutrition is at V16 (each service has its own).
- **Backward-compatible event contracts**: add optional fields with an overloaded constructor keeping the
  old signature; update the golden-sample JSON + `EventPayloadGoldenSamplesTests` (it hand-rolls type
  decoding — Map/List need explicit support).
- Logger field is `log` (house style). Case-insensitive compare: `String.CASE_INSENSITIVE_ORDER` (not
  `toLowerCase`/`equalsIgnoreCase` — SpotBugs flags those). Avoid unbox-then-rebox.
- SpotBugs false positives get a **per-class exclusion** in root `spotbugs-exclude.xml` with a
  justification comment.

## Verify before every PR

```
mvn -pl <modules> -am verify -Psast -Dspring.profiles.active=test
```
Runs tests + JaCoCo + **SpotBugs** on H2 — the same gate CI runs (`verify-backend`). Sonar only runs in
CI. For a new `recommendation-service` module, add it to the reactor and CI, mirroring another service's
`pom.xml` + `application.yml` (H2 test profile, Flyway, outbox pattern like nutrition/analytics).

## Merge flow (for the main-bound helper PRs)

Repo has **no native auto-merge**. Pattern used all session: push branch → open PR → `subscribe_pr_activity`
+ a `*/4 * * * *` cron that polls `get_check_runs`; when `verify-backend` = success → `merge_pull_request`
(repo default method) → delete cron + unsubscribe. recommendation-service code itself is NOT PR'd to main.
