# recommendation-service — Functional Plan

> **Status.** This service is on `main` as of 2026-09-26. It was developed on an isolated branch
> while its shape was still being worked out; that constraint is retired. Contract and planning
> documents live in `docs/`. The coach platform design that extends this service is in
> `Documents/architecture/coach-platform-design.md`.

> This document defines what the service does, grounded in the data
> signals FitSync actually emits, and splits the work between **locally-computed (rule-engine)**
> recommendations and **externally-computed (AI API)** recommendations.

---

## 1. Mission

recommendation-service is FitSync's **personalization layer**. It owns no primary write path. It:

1. **Consumes** domain events + analytics outputs and maintains a per-user read model.
2. **Generates & refreshes** personalized suggestions — mostly with deterministic rules, selectively with AI.
3. **Caches** results in its own tables and serves them through a read-only API.

Constraints inherited from the repo design docs (`backend.md` §6.7/§8.3/§12,
`backend-api-communication.md` §4, `backend-kafka-event-architecture.md` §8/§11):

- **Never** on the critical write path — core requests must not block on recommendation work.
- Communicates only via **HTTP service contracts or Kafka** + `event-contracts`; no cross-service Java imports.
- **Owns its own data**; does not write other services' tables. Consumers stay thin; logic in handlers/services.

---

## 2. What data we actually have

The feature design is constrained by the real `event-contracts` payloads. This is the signal inventory:

| Source event | Fields we can use | Notes / gaps |
|---|---|---|
| `UserRegistered` | email, displayName, role, registeredAt | cold-start trigger |
| `UserProfileUpdated` | displayName, **heightCm**, **activityLevel**, onboardingCompleted, timezone | no age/sex in the event → exact TDEE needs a profile read |
| `UserPreferencesUpdated` | **workoutDaysPerWeek**, **dietaryPreference**, **equipmentInventory[]**, **focusAreas[]**, notificationsEnabled, units, timezone | rich inputs for workout/meal recs |
| `GoalUpdated` | goalId, **goalType**, **targetValue**, targetUnit, **targetDate**, status | generic goal — **no calorie target** carried |
| `BodyMetricsRecorded` | **weightKg**, bodyFatRate, waistCm, measuredAt | time series → trend |
| `WorkoutLogged` | **workoutType**, durationMinutes, caloriesBurned, exerciseCount, planId, completedAt | **no per-set weight/reps** → no weight-based progression |
| `PersonalRecordAchieved` | exerciseNameSnapshot, records[{recordType, value}], achievedAt | celebration / progress signal |
| `MealLogged` | **mealType**, **totalCalories**, **protein/carbs/fat grams**, consumedAt, itemCount | daily nutrition summation |
| `WeeklyRecapGenerated` *(analytics)* | weekStart/End, **workoutCount, workoutMinutes, caloriesBurned, caloriesConsumed, activeDays** | **already computed by analytics** — consume, don't recompute |
| `StreakMilestoneReached` *(analytics)* | streakDays, milestone, achievedDate | consistency nudges |

**Three engineering facts that shape everything below:**

1. **Reuse analytics, don't duplicate it.** Weekly stats and streaks arrive pre-computed via
   `WeeklyRecapGenerated` / `StreakMilestoneReached`. The weekly narrative is then almost free —
   the numbers already exist; we only phrase them.
2. **No strength-progression data.** `WorkoutLogged` has type/duration/calories/exerciseCount only.
   Workout recommendations are about **type / focus-area / frequency / equipment**, not "add 2.5 kg".
3. **No calorie target in events.** Derive a daily energy target locally (TDEE estimate from height +
   latest weight + activityLevel, adjusted by goalType), or use a user-set target / a profile read.
   Don't assume a `targetCalories` field exists.

---

## 3. The local vs AI split — the central decision

The product backbone is local; AI is selective. Rule of thumb: **if a spreadsheet formula could
produce it, keep it local. If it reads like a coach *talking*, it's AI.**

| Decide **LOCAL (rule engine)** when… | Decide **AI API** when… |
|---|---|
| Numeric / structured / bounded output | Natural-language or open-ended output |
| Derivable from thresholds & math | Synthesizes many weak signals into nuanced advice |
| Must be fast, free, always available | Latency/cost acceptable (async or on-demand) |
| Runs on every relevant event | Runs on a cadence or explicit user request |

**Core inversion (drives both cost and performance):** *compute in code, let the model only phrase.*
Feed the model the rule-computed facts, never raw data — it stops doing arithmetic (no number
hallucinations), the context shrinks, and a small/cheap model suffices.

---

## 4. Feature catalog (grounded in §2)

`MVP` = capstone scope (build now). `Full` = future extension. Source column proves each is buildable.

### 4.1 Tier 1 — Local / rule-engine (the backbone, zero per-request AI cost)

| # | Feature | Built from | Scope |
|---|---|---|---|
| R1 | **Daily nutrition gap** — "remaining kcal / protein today" | local energy target (TDEE est. from `heightCm`+latest `weightKg`+`activityLevel`, adjusted by `goalType`) − Σ today's `MealLogged` calories/macros | **MVP** |
| R2 | **Weekly adherence** — "targeted N days, done M, K to go" | `UserPreferencesUpdated.workoutDaysPerWeek` vs `WeeklyRecapGenerated.activeDays`/`workoutCount` | **MVP** |
| R3 | **Goal-pace tracker** — ahead / on-track / behind + nudge | linear trend on `BodyMetricsRecorded.weightKg` vs (`GoalUpdated.targetValue`, `targetDate`) | **MVP** |
| R4 | **Consistency / streak nudge** | `StreakMilestoneReached` (+ streak length) | **MVP** |
| R5 | **Cold-start defaults** | `GoalUpdated` + preferences (`focusAreas`, `equipment`, `workoutDaysPerWeek`, `dietaryPreference`) | **MVP** |
| R6 | **PR celebration** | `PersonalRecordAchieved` | MVP-optional |
| R7 | **Focus-area balance hint** — "you haven't trained `legs` this week" | `WorkoutLogged.workoutType` frequency vs `focusAreas` | Full |
| R8 | **Logging reminder** — timezone-aware | `timezone` + `notificationsEnabled` + activity gaps | Full |

Engine shape: a registry of `Rule` impls, each `(RecommendationContext) -> Optional<Recommendation>`,
run on the relevant event and on `POST /refresh`. Output written to cache with `source = RULE`.

### 4.2 Tier 2 — External AI (Claude API), selective

| # | Feature | Trigger | Why it needs AI |
|---|---|---|---|
| A1 | **Weekly coaching narrative** | `WeeklyRecapGenerated` (weekly) | Motivational prose over the recap numbers + goal. **Cheap** — numbers already exist; AI only phrases. |
| A2 | **Workout plan / next-session idea** | on-demand | Concrete plan from `focusAreas`+`equipment`+`workoutDaysPerWeek`+recent types — open-ended reasoning. |
| A3 | **Meal ideas within macro budget** | on-demand | Generative, respecting `dietaryPreference` + remaining macros (from R1). |
| A4 | **Conversational coach Q&A** | explicit request | Free-form reasoning + dialogue. |
| A5 | **Rationale** for a rule output | optional | Plain-language wrapper around a numeric rule. |

**For a capstone, ship exactly one of {A1, A4} live** to demonstrate AI integration; treat A2/A3/A5 as Full.

---

## 5. Cost & performance strategy

Both the **capstone-MVP** approach and the **advanced** techniques are documented here. Build the MVP
column now; the advanced column is the roadmap, not a day-1 requirement.

### 5.0 Honest framing on "agents"

A full autonomous **agent** (model decides its own trajectory, loops over tools) *raises* cost and
latency for this use case — the recommendation workflow is pre-decidable. What fits is the **Workflow
tier**: code orchestrates the control flow, the model is a stateless leaf that only does NL tasks. Keep
AI **off the read path** entirely (precomputed + cached) — that is simultaneously the biggest
performance win (read = one DB hit, ms-level) and the biggest cost win.

### 5.1 Levers that apply at every scale (do these regardless)

| Lever | Implementation |
|---|---|
| **Never trigger AI per event** | Event handlers only update the read model + mark `stale`. AI runs on cadence or on-demand, never inside `WorkoutLogged`/`MealLogged`. |
| **Decision funnel on read** | `cache (fresh) → rule → template → (last resort) AI`. ~95% of reads never reach AI. |
| **Compute-in-code, model-phrases** | Pass rule-computed facts into the prompt; never raw data. Enables a small model + small context. |
| **TTL cache + change-detection** | `recommendation_result` carries `expires_at` + `context_hash`; `/refresh` re-invokes AI only when the underlying context materially changed. |
| **Graceful degradation** | AI is async + cached; on error/rate-limit/`refusal` → fall back to the rule/template output. Read path never errors. |

### 5.2 MVP (capstone) — keep it cheap *and* simple

The dollar cost of AI here is trivial; the cost to avoid is **engineering complexity**.

- **Hand-write 10–20 narrative templates** (rule numbers fill the slots). This *is* a "distilled
  template library" — but you are the distiller. Zero AI, zero pipeline, an afternoon of work, covers
  most demo surface. e.g. *"You trained {n} times this week (+{delta} vs last) — {focus} is on track,
  protein is {gap}g short; add a {suggestion} tomorrow."*
- **Keep one genuine live AI feature** (A1 weekly narrative *or* A4 coach Q&A), async + cached one
  week. Whole-semester Claude bill realistically lands in **single-digit dollars**.
- **Model tiering:** `claude-haiku-4-5` ($1/$5 per 1M) for short text, `claude-sonnet-4-6` ($3/$15)
  for the weekly narrative; reserve `claude-opus-4-8` ($5/$25) for the hard generative tasks only.
- **Prompt caching:** stable system prompt / coaching guidelines first with a `cache_control`
  breakpoint; volatile per-user context after it (cached prefix bills ~0.1×). Verify via
  `usage.cacheReadInputTokens()`.

### 5.3 Advanced (Full / future) — only if scale justifies it

Documented for completeness; **explicitly out of capstone scope.**

| Technique | What it buys | Why deferred |
|---|---|---|
| **Design-time distillation** — Opus generates a large template/decision library offline, served as data at runtime | One paid call → unlimited free serves | Hand-written templates already cover MVP; pipeline complexity not worth it small-scale |
| **Retrieval instead of generation** — embed a meal/exercise catalog offline; serve A2/A3 by vector similarity to the user's macro gap / preferences | Turns generation into a ms-level lookup, ~0 cost | Needs a catalog + vector store; overkill for a demo |
| **Programmatic tool calling (PTC)** — model writes one script that calls tools in a sandbox; intermediate results never hit context, only final output returns | Collapses N round-trips into 1; cuts tokens + latency | Only pays off when the model must orchestrate multiple steps (e.g. complex A4) |
| **Model cascade** — template/Haiku first, escalate to Sonnet/Opus only when a self-score/validation fails | Cheap model handles the long tail of easy cases | Adds a scoring/escalation layer |
| **Batch API** — submit weekly-narrative generation for all users in one batch | ~50% cheaper for non-latency-sensitive cadence work | Only relevant at user volume |

### 5.4 Target latency profile

```
Read path  (sync, ZERO AI, ms):   GET → cache → rule → template            ← 95%+ of requests
Gen  path  (async/low-freq):      weekly cadence → (Batch+cache+Sonnet)
                                   user Q&A       → (single-shot or PTC, capped round-trips)
                                   fallback       → rule/template on any AI failure
```

---

## 6. Event consumption map

Kafka consumer, group `recommendation-consumer-group`, over existing `event-contracts` topics:

| Topic / event | Drives |
|---|---|
| `WorkoutLogged` / `WorkoutUpdated` / `WorkoutDeleted` / `PersonalRecordAchieved` | R6, R7 (training read model) |
| `MealLogged` / `MealUpdated` / `MealDeleted` | R1 (nutrition read model) |
| `GoalUpdated` / `BodyMetricsRecorded` | R1, R3, A2 |
| `UserRegistered` / `UserProfileUpdated` / `UserPreferencesUpdated` | R5, inputs for A2/A3 |
| `WeeklyRecapGenerated` *(analytics)* | R2, A1 — **reuse, don't recompute** |
| `StreakMilestoneReached` *(analytics)* | R4 |
| `AccountDeleted` | purge the user's recommendation data |

Point-in-time gaps (e.g. age/sex for an exact TDEE) → synchronous reads of the **user-service
contract**, not its repository (`backend-api-communication.md` §4). Never a distributed transaction.

---

## 7. Data ownership

- `recommendation_context` — per-user read model assembled from events (current goal, derived energy
  target, recent training summary, recent nutrition summary, latest body metrics, preferences). Shared
  input for both tiers.
- `recommendation_result` — `(user_id, type, source[RULE|AI], payload JSON, generated_at, expires_at,
  context_hash, version)`. The read API serves from here.

## 8. API surface (read model)

Behind the reserved gateway route `/api/v1/recommendations/**` (in `api-gateway/README.md`; not yet wired):

| Method & path | Purpose |
|---|---|
| `GET /api/v1/recommendations` | Current cached recommendations (Tier 1 + Tier 2) for the user. |
| `GET /api/v1/recommendations/{type}` | One recommendation type. |
| `POST /api/v1/recommendations/refresh` | Recompute (rules synchronously; AI enqueued async, gated by change-detection). |
| `POST /api/v1/recommendations/coach` | (Full) Conversational coach Q&A (A4). |

All reads hit the cache; generation is never inline on the read path.

## 9. Package structure (per `backend.md` §9.7)

```
com.fitsync.recommendation/
├── RecommendationServiceApplication.java
├── controller/        # read API
├── service/
│   ├── RecommendationResolver.java   # the §5.1 funnel: cache→rule→template→AI
│   ├── rules/                        # Tier-1 Rule impls + registry + NarrativeTemplates
│   └── ai/                           # Tier-2 Claude client, prompt builders, DTOs, fallback
├── consumer/          # thin Kafka consumers
├── handler/           # event handlers (update context, mark stale)
├── client/            # service-contract clients (user/workout/nutrition reads)
├── dto/  ├── config/  └── exception/
```

## 10. Roadmap

| Phase | Deliverable |
|---|---|
| **0 — Scaffold** | Main class, package skeleton, `recommendation_context` + `recommendation_result` tables, context-assembly consumers. |
| **1 — Local engine (MVP)** | Rules R1–R5, `RecommendationResolver` funnel, hand-written templates, read API + `/refresh`, gateway route + `recommendation-service-base-url` wired, add module to CI. |
| **2 — One live AI feature (MVP)** | A1 weekly narrative **or** A4 coach Q&A — async, prompt-cached, model-tiered, with rule/template fallback. |
| **3 — Extensions (Full)** | A2/A3/A5, R6–R8, and the §5.3 advanced techniques (distillation, retrieval, PTC, cascade, Batch) as scale warrants. |

Per `backend-kafka-event-architecture.md` §18, Phases 0–2 can run as in-process handlers inside the
monolith first; extract to a standalone deployment only when scale justifies it.

---

### Appendix — environment

The AI tier reads `ANTHROPIC_API_KEY` from the environment (Java SDK `AnthropicOkHttpClient.fromEnv()`).
Keep it out of source and out of `recommendation_*` tables. Model IDs are configuration, not hard-coded.
