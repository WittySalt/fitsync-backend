# analytics ↔ recommendation — Division of Labor & Source-Event Evolution

> **Status:** Design note (theory + proposals). Lives on the
> `claude/recommendation-interface-review-*` branch; **not for `main`**. It describes how
> analytics-service should evolve to carry more of the derived-computation load, and what the three
> base services (nutrition / workout / user) should additionally emit so analytics can compute deeper
> insights. No code yet.

Companion to `recommendation-service/README.md`.

---

## 1. The boundary — analytics maturity ladder

Two services stay clean only if they sit on different rungs of the classic analytics ladder:

| Level | Question | Tense | Owner |
|---|---|---|---|
| 1. Descriptive | "what happened" — aggregates, trends, distributions | past/present | **analytics** |
| 2. Diagnostic | "why" — deviations, anomalies, correlations | present | **analytics** |
| 3. Predictive | "what's next" — extrapolation, risk flags | future | **analytics** |
| 4. Prescriptive | "what should I do" — advice, actions | imperative | **recommendation** |

> **analytics states facts; recommendation issues judgments.**
> "Vegetable intake is low this week" = diagnostic fact → analytics.
> "Add a serving of vegetables tomorrow" = prescription → recommendation.

The further analytics climbs (1→3), the thinner and cheaper recommendation becomes — it degrades to a
`fact → threshold → template` mapping that needs little computation and little AI.

**Boundary test (prevents either side bloating):** *Does this computed value have a consumer other than
one specific piece of advice (dashboard, weekly recap, notification)?*
- Yes → reusable **fact** → analytics.
- No, it's a threshold/wording that only serves an advice line → recommendation.

Example: "this week's training volume" (dashboard + recap + recommendation all use it) → analytics.
"volume below 80% of target ⇒ nudge" (the 80% + wording only serve the nudge) → recommendation.

---

## 2. The dependency chain (and where it breaks)

> **recommendation depth ← analytics depth ← source-event richness**

The chain breaks at the top. Validation against today's real payloads:

| Desired insight | Computable today? | Blocker |
|---|---|---|
| "trained too much / too little" | ✅ yes | `WorkoutLogged` has duration/frequency → volume trend, ACWR |
| "ate too much meat / too few vegetables" | ❌ no | `MealLogged` has macros only — **no food-group breakdown** |
| "protein below target split" | ⚠️ macro-level only | macros present, but no per-food detail |
| "weight off goal pace" | ✅ yes | `BodyMetricsRecorded` series + `GoalUpdated.targetValue/targetDate` |
| "exact daily calorie target" | ❌ no | no calorie target in events; TDEE needs age/sex (not emitted) |

**So "richer analytics" sometimes requires "richer source events first."** §4 proposes exactly those
source-event expansions.

---

## 3. Target topology — analytics as the hub

**Today's implied topology:** recommendation subscribes to raw events from all three services *plus*
analytics → it couples to four upstreams and recomputes facts.

**Target:**

```
nutrition / workout / user  ──(all main events)──▶  analytics
                                                       │  computes derived FACTS
                                                       ▼
                                          emits *Computed / *Flagged events
                                                       │
                                                       ▼
                                              recommendation
                                  (mostly consumes analytics facts;
                                   + a few direct signals: preferences,
                                     equipment, instant PR celebration)
                                  → only fact → threshold → template
```

Benefits: recommendation drops from "4 upstreams + recompute" to "1 main upstream + lookup"; the
derived facts are **reused** by dashboard, weekly recap, and notification too.

**Contract rule:** analytics emits **facts with values + labels, never advice.** e.g.
`NutritionBalanceComputed{ proteinVsTargetPct, foodGroupCalorieShare{...}, fiberAdequacyPct,
mealTimingRegularity }`. recommendation reads that, applies a threshold, fills a template.

### 3.1 Proposed analytics → recommendation fact events

Additive to `event-contracts` (sketch, not final):

```java
// analytics emits; recommendation + dashboard + notification consume
record EnergyBalanceComputed(LocalDate date, int intake, int expenditure,
                             int netVsTarget, String direction /*DEFICIT|MAINTENANCE|SURPLUS*/) {}

record NutritionBalanceComputed(LocalDate weekStart, BigDecimal proteinVsTargetPct,
                                Map<String,BigDecimal> foodGroupCalorieShare, // 🔴 needs §4.1
                                BigDecimal fiberAdequacyPct, String headline /*label, not advice*/) {}

record TrainingLoadComputed(LocalDate weekStart, int volumeLoad,            // 🔴 needs §4.2
                            BigDecimal acwr /*acute:chronic ratio*/,
                            Map<String,Integer> setsByMuscleGroup,
                            String loadStatus /*UNDER|OPTIMAL|OVERREACHING*/) {}

record GoalPaceComputed(UUID goalId, BigDecimal currentRatePerWeek,
                        BigDecimal requiredRatePerWeek, String status /*AHEAD|ON_TRACK|BEHIND*/) {}
```

`🔴` marks fields that can only be populated once the corresponding source event is enriched (§4).

---

## 4. Source-service enrichment proposals

These strengthen analytics' **information intake**. Principles first, then per-service.

### 4.0 Contract-evolution principles

1. **Additive & backward-compatible only.** Add new *optional* fields or new event types; never remove
   or repurpose an existing field. Existing consumers must keep working unchanged. (The payloads already
   use overloaded record constructors for exactly this — follow that pattern.)
2. **The owner emits the detail it already owns; analytics aggregates.** nutrition owns food data,
   workout owns exercise data, user owns profile. Don't push aggregation upstream or detail downstream.
3. **Owner computes personal/PII-derived numbers and emits the number, not the PII.** Keeps sensitive
   data contained *and* saves downstream services from needing profile internals (see 4.3).
4. **Mind payload size.** Prefer compact rollups when the detail isn't needed downstream; offer full
   detail only where analytics genuinely needs per-item granularity.

### 4.1 nutrition-service — `MealLogged`

Current:
```java
record MealLoggedPayload(UUID mealId, String mealType, Instant consumedAt,
        Integer totalCalories, BigDecimal totalProteinGrams, BigDecimal totalCarbsGrams,
        BigDecimal totalFatGrams, Integer itemCount, String source) {}
```
Proposed additive fields:
```java
// Minimal (MVP) — unlocks the headline "meat-heavy / veg-light" insight cheaply:
Map<String,Integer> foodGroupCalories;   // {PROTEIN, VEGETABLE, FRUIT, GRAIN, DAIRY, FAT, OTHER} → kcal
BigDecimal fiberGrams; BigDecimal sugarGrams; Integer sodiumMg;

// Full — per-item detail for variety / micro analysis:
List<MealItem> items; // (foodId, name, foodGroup, quantity, unit, calories, protein, carbs, fat,
                      //  fiberGrams, sugarGrams, sodiumMg)
```
**Unlocks (analytics):** food-group balance ("肉多菜少"), fiber/sugar/sodium adequacy, dietary variety,
micronutrient gaps. **Prereq:** nutrition's food catalog must tag each food with a `foodGroup`.
**Recommendation:** ship the `foodGroupCalories` map first — one map unlocks the whole food-balance story
without per-item bloat.

### 4.2 workout-service — `WorkoutLogged`

Current:
```java
record WorkoutLoggedPayload(UUID sessionId, UUID planId, String workoutType, String status,
        Instant startedAt, Instant completedAt, Integer durationMinutes, Integer caloriesBurned,
        Integer exerciseCount, String source) {}
```
Proposed additive fields:
```java
// Minimal (MVP) — unlocks volume trend, ACWR, muscle coverage without per-set bloat:
Integer totalVolumeLoad;                 // Σ(reps × weightKg) across the session
Map<String,Integer> setsByMuscleGroup;   // {CHEST, BACK, LEGS, ...} → set count

// Full — per-exercise/set detail for progressive overload & intensity:
List<ExerciseEntry> exercises; // (exerciseId, name, muscleGroups[], sets:[(reps, weightKg, rpe)])
```
**Unlocks (analytics):** training-volume trend, **ACWR (acute:chronic workload ratio → over/under-training
risk)**, per-muscle coverage & balance, progressive-overload tracking, intensity (RPE/%1RM).
**Prereq:** workout-service must persist sets/weights (it may only track plan-level today — check).
**Recommendation:** `totalVolumeLoad` + `setsByMuscleGroup` give the highest analytics yield per effort.

### 4.3 user-service — profile & goal (PII-clean derived numbers)

**Profile** — exact TDEE (Mifflin–St Jeor) needs age + biological sex, which `UserProfileUpdated`
doesn't carry and which are PII. **Don't leak the PII downstream.** Instead have user-service compute and
emit the derived numbers:
```java
// New event (or fields on profile update) — user-service computes internally from DOB/sex/height/weight:
record EnergyProfileComputed(BigDecimal bmr, BigDecimal tdeeEstimate, String activityLevel) {}
```
**Goal** — generic today; give recommendation a concrete target without deriving it:
```java
// Optional additive on GoalUpdated, or a dedicated event, when goalType is nutrition/weight related:
Integer dailyCalorieTarget;
MacroSplit macroSplit; // (proteinPct, carbPct, fatPct)
```
**Unlocks:** proper R1 daily-calorie/macro gap, cross-domain energy-balance-vs-goal — **with PII staying
inside user-service.** This is the single highest-leverage, lowest-risk first move.

### 4.4 Future domains (only if/when integrated)

`SleepRecorded`, `RestingHeartRateRecorded` (wearables) → recovery & overtraining cross-checks
(combine with §4.2 ACWR). Out of current scope; listed so the contract leaves room.

---

## 5. Prioritized roadmap — what to enrich first

Ranked by **(analytics+recommendation value) ÷ effort**:

| Rank | Enrichment | Unlocks | Effort | Scope |
|---|---|---|---|---|
| 1 | **user-service emits `EnergyProfileComputed` + goal `dailyCalorieTarget`** | proper nutrition gap (R1) + energy balance, PII-clean | low | **MVP** |
| 2 | **nutrition `foodGroupCalories` map on `MealLogged`** | "meat-heavy/veg-light", food balance | medium (needs food-group tags) | **MVP/Full** |
| 3 | **workout `totalVolumeLoad` + `setsByMuscleGroup`** | volume trend, ACWR, muscle coverage | medium (needs set tracking) | **Full** |
| 4 | nutrition `items[]` + fiber/sugar/sodium | variety, micro adequacy | high | Full |
| 5 | workout `exercises[]/sets[]` | progressive overload, intensity | high | Full |
| 6 | sleep / HR domains | recovery cross-checks | new integration | Future |

**For the capstone:** do rank 1 (small, unlocks the whole energy/nutrition-gap story, privacy-clean) and
optionally rank 2's minimal map. Treat 3–6 as the documented growth path.

---

## 6. Answers to the driving questions

1. **Does richer analytics ⇒ more comprehensive recommendation?** Yes — but capped by source-event
   richness. Every prescription needs a fact beneath it; analytics supplies the facts, recommendation
   stays a thin, cheap mapping layer.
2. **What is analytics for?** The reusable derived-fact layer (ladder levels 1–3), especially
   **cross-domain** insight (e.g. "surplus intake under a fat-loss goal") that no single service can see.
   It never issues prescriptions.
3. **Can we analyze "too much meat / too little movement"?** Movement: **yes today** (volume/ACWR).
   Food groups: **only after** nutrition-service emits `foodGroupCalories` (§4.1) — macros alone can't
   express it.
