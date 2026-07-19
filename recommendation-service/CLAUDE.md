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

## Status — MVP delivered on this branch (checkpoint: `2286060`)

Full detail (commits, invariants, gaps, run/verify): **`HANDOFF.md`**. Summary:

| Item | State | Where |
|---|---|---|
| RC-1 scaffold (app, security, tables V1) + X-2 CI | ✅ `7565272` | module root, `config/`, `security/` |
| RC-8 Kafka consumers → context + stale (idempotent, V2) | ✅ `679ed0b` | `consumer/`, `handler/` |
| RC-2 rules R1–R5 (baseline capture V3) | ✅ `21eb7cf` | `service/rules/` |
| RC-4/RC-5 resolver funnel + 13 templates | ✅ `3cc61ac` | `service/RecommendationResolver`, `service/rules/NarrativeTemplates` |
| RC-7 read API + X-1 gateway route | ✅ `741df94` | `controller/`, api-gateway (branch-only) |
| RC-6 A1 weekly narrative + X-4 key wiring | ✅ `74db57e` | `service/ai/` (off by default) |
| **X-3 OFF attribution** | ⬜ **main-bound** — separate branch → PR to `main`, NOT here | — |
| FD-1 OFF data validation | ⬜ owner's task (needs the real DB) | `data-validation-runbook.md` |
| RC-3 / RC-9 / RC-10 + FD/EV/AN Full items | ⬜ Full-scope growth path | `BACKLOG.md` |

Flyway at **V3** · 75 + 27 tests green · SpotBugs clean · AI tier + all Kafka listeners **off by
default** (env flags in `application.yml`). The AI tier is provider-switchable
(`RECOMMENDATION_AI_PROVIDER`): `anthropic` (default) or `ollama` for a local open model
(no key; see `HANDOFF.md` §3).

## What to build next (value order — details in `HANDOFF.md` §5)

1. **X-3** OFF attribution — small main-bound PR; completes the MVP list.
2. **EV-3** `EnergyProfileComputed` (user-service, main-bound) — unlocks exact R1 targets, PII-clean.
3. Feed R1/R3 facts into the A1 prompt; optionally A5 rationale as a cheap second AI surface.
4. **RC-3** rules R6–R8 once EV-2 lands.

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
