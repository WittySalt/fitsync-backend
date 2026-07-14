# Analytics Service Design

## 1. Responsibility Boundary

`analytics-service` should own derived, query-oriented fitness analytics.

Recommended ownership split:

- `user-service`
  - profile
  - preferences
  - body metrics
  - goals
- `workout-service`
  - workout plans
  - workout sessions
  - exercise logs
- `nutrition-service`
  - meal logs
  - food and nutrition source facts
- `analytics-service`
  - dashboard summaries
  - trend series
  - adherence metrics
  - streaks
  - consistency scores
  - read-optimized aggregations

Important rule:

- `analytics-service` is not a system-of-record write owner for workouts, meals, goals, or body metrics
- it consumes source facts and builds derived read models
- its APIs are read-only for product-facing traffic in the initial rollout

---

## 2. Design Goals

The first version of `analytics-service` should solve four problems:

- provide one place for cross-domain dashboard reads
- avoid synchronous fan-out from frontend to many backend services
- keep formulas and aggregation logic out of source-of-truth services
- support eventual-consistent recalculation from Kafka events

Primary non-goals for phase 1:

- natural-language analytics chat
- predictive recommendations
- real-time streaming dashboards
- user-authored analytics rules

---

## 3. Domain Slices

`analytics-service` should start with five read-model slices.

### 3.1 User Analytics Snapshot

Current dashboard-oriented summary for one user.

Fields:

- `userId`
- `snapshotDate`
- `timezone`
- `latestWeightKg`
- `latestBodyFatRate`
- `currentBmi`
- `todayCaloriesConsumed`
- `todayCaloriesBurned`
- `todayProteinGrams`
- `activeWorkoutStreakDays`
- `goalAdherenceScore`
- `lastRecomputedAt`

### 3.2 Body Progress Analytics

Trend-oriented derived body metrics.

Fields:

- `userId`
- `metricDate`
- `weightKg`
- `bodyFatRate`
- `waistCm`
- `bmi`
- `weightDeltaFromStart`
- `weightDeltaFromPrevious`

### 3.3 Workout Analytics

Read models derived from workout completion and correction events.

Fields:

- `userId`
- `activityDate`
- `completedWorkoutCount`
- `totalWorkoutMinutes`
- `totalCaloriesBurned`
- `totalExerciseCount`
- `workoutTypeBreakdown`
- `rolling7DayWorkoutCount`
- `rolling28DayWorkoutCount`

### 3.4 Nutrition Analytics

Read models derived from meal events and goal projections.

Fields:

- `userId`
- `activityDate`
- `mealCount`
- `totalCaloriesConsumed`
- `totalProteinGrams`
- `totalCarbsGrams`
- `totalFatGrams`
- `calorieTarget`
- `proteinTargetGrams`
- `calorieAdherenceRatio`
- `macroAdherenceScore`

### 3.5 Goal Progress Analytics

Cross-domain progress toward active goals.

Fields:

- `userId`
- `goalId`
- `goalType`
- `goalStatus`
- `targetValue`
- `targetUnit`
- `targetDate`
- `currentValue`
- `progressRatio`
- `daysRemaining`
- `onTrack`
- `lastEvaluatedAt`

---

## 4. What Should Not Be Core Analytics Write Models

The following should not be stored as irreversible truth:

- opaque AI-generated health labels
- explanation text that cannot be recomputed
- recommendation decisions
- notification delivery state
- copied source-of-truth meal, workout, or goal aggregates beyond what is needed for query performance

Rule:

- analytics rows must remain reproducible from source events plus deterministic formulas

---

## 5. API Surface

Base path:

- `/api/v1/analytics`

Authenticated requests must derive `userId` from JWT.

Phase 1 should expose read-only APIs.

### 5.1 Dashboard

#### GET `/api/v1/analytics/dashboard`

Purpose:

- return a unified dashboard summary for the current user

Suggested response shape:

```json
{
  "generatedAt": "2026-06-12T09:30:00Z",
  "timezone": "Asia/Singapore",
  "body": {
    "latestWeightKg": 78.4,
    "latestBodyFatRate": 19.8,
    "bmi": 24.7,
    "weightChange30d": -1.6
  },
  "workout": {
    "todayWorkoutCount": 1,
    "weekWorkoutCount": 4,
    "weekWorkoutMinutes": 205,
    "activeStreakDays": 3
  },
  "nutrition": {
    "todayCaloriesConsumed": 1860,
    "todayCaloriesTarget": 2100,
    "todayProteinGrams": 132.5,
    "todayProteinTargetGrams": 150.0
  },
  "goals": [
    {
      "goalId": "75f3fd13-4723-4e9d-a690-b3c4c4507419",
      "goalType": "WEIGHT",
      "progressRatio": 0.42,
      "onTrack": true
    }
  ]
}
```

### 5.2 Body Trends

#### GET `/api/v1/analytics/body/trends`

Purpose:

- return body metric trend series with derived BMI and deltas

Query params:

- `from`
- `to`
- `bucket`: `DAY`, `WEEK`, `MONTH`

### 5.3 Workout Summary

#### GET `/api/v1/analytics/workouts/summary`

Purpose:

- return workout summary aggregates for a selected period

Query params:

- `from`
- `to`

Suggested response fields:

- total workout count
- total minutes
- total calories burned
- average duration
- workout type distribution
- streak snapshot

### 5.4 Nutrition Summary

#### GET `/api/v1/analytics/nutrition/summary`

Purpose:

- return nutrition adherence aggregates for a selected period

Query params:

- `from`
- `to`

Suggested response fields:

- calories consumed
- protein/carbs/fat totals
- target totals
- adherence ratios
- days within target band

### 5.5 Goal Progress

#### GET `/api/v1/analytics/goals/progress`

Purpose:

- return derived progress against current and historical goals

Query params:

- `status`: optional

### 5.6 Streaks

#### GET `/api/v1/analytics/streaks`

Purpose:

- return the current and best workout streak for the profile/gamification views

Response shape:

```json
{
  "currentStreakDays": 6,
  "bestStreakDays": 14,
  "bestStreakFrom": "2026-03-02",
  "bestStreakTo": "2026-03-15",
  "lastActiveDate": "2026-07-07",
  "timezone": "Asia/Singapore"
}
```

Rules:

- `currentStreakDays` is read from the dashboard snapshot's `activeStreakDays`, so the streak
  definition is exactly the dashboard's: natural days in the user's timezone, with a workout today
  or yesterday keeping the streak alive
- `bestStreakDays` and its window come from `analytics_user_streak_rollup`, materialized by the
  nightly daily-rollup job (AN-1) instead of scanning workout history at request time; a live
  current streak that momentarily exceeds the materialized best is surfaced as the best
- users without data get a zeroed payload (`200`), never a `404`

---

## 6. API Design Rules

- all analytics APIs are user-scoped in phase 1
- no client-supplied `userId` is accepted on external endpoints
- reads should tolerate eventual consistency and expose `generatedAt` or `lastUpdatedAt`
- missing analytics data should return valid empty-state payloads, not server errors
- source-service outages should not break analytics reads if the read model already exists

Recommended behavior:

- `200` with empty arrays and nullable summary fields when a user is new
- `400` for invalid date range or bucket parameters
- `401/403` through shared security handling

---

## 7. Event Design

`analytics-service` is primarily an event consumer in phase 1.

### 7.1 Incoming Events

#### WorkoutLogged

Producer:

- `workout-service`

Consumers:

- `analytics-service`

Usage:

- update daily workout aggregates
- update streaks
- update rolling consistency metrics

Current payload is sufficient for phase 1.

#### WorkoutUpdated

Producer:

- `workout-service`

Consumers:

- `analytics-service`

Usage:

- compensate prior workout-derived analytics when completed session facts change

Current payload is sufficient for phase 1.

#### WorkoutDeleted

Producer:

- `workout-service`

Consumers:

- `analytics-service`

Usage:

- subtract or rebuild affected workout aggregates

Current payload is sufficient for phase 1, but replay logic must be idempotent.

#### PersonalRecordAchieved

Producer:

- `workout-service`

Consumers:

- `analytics-service`

Usage:

- accumulate the per-user personal-record tally in `analytics_user_workout_records`
- feeds the `PR_FIRST` achievement (and `personalRecordCount`-style aggregates) without opening a
  new public endpoint

The tally is append-only: workout deletions do not retract already-announced records, matching the
"ever achieved" semantics of achievements.

#### MealLogged

Producer:

- `nutrition-service`

Consumers:

- `analytics-service`

Usage:

- update daily calorie and macro aggregates
- update nutrition adherence metrics

Current payload is sufficient for phase 1.

#### MealUpdated

Producer:

- `nutrition-service`

Consumers:

- `analytics-service`

Usage:

- compensate nutrition aggregates when meal facts are corrected

Current payload is sufficient for phase 1.

#### MealDeleted

Producer:

- `nutrition-service`

Consumers:

- `analytics-service`

Usage:

- subtract or rebuild affected nutrition aggregates

Current payload is sufficient for phase 1.

#### GoalUpdated

Producer:

- `user-service`

Consumers:

- `analytics-service`

Usage:

- update target baselines
- recompute adherence and progress metrics

Current payload is sufficient for phase 1.

#### BodyMetricsRecorded

Producer:

- `user-service`

Consumers:

- `analytics-service`

Usage:

- update body trend series
- recompute BMI and body-progress views
- refresh weight-oriented goal progress

Current payload is sufficient for phase 1.

#### UserProfileUpdated

Producer:

- `user-service`

Consumers:

- `analytics-service`

Usage:

- refresh height-dependent BMI calculations
- refresh timezone-sensitive bucketing when profile timezone becomes authoritative later

Current payload is sufficient for BMI recalculation because it already includes `heightCm`.

#### UserPreferencesUpdated

Producer:

- `user-service`

Consumers:

- `analytics-service`

Usage:

- update display-oriented or heuristic analytics context such as preferred units

Current payload is optional for phase 1. Analytics can start without consuming it.

### 7.2 Missing or Future Event Contracts

The current event set is enough to start analytics, but these gaps matter:

- `BodyMetricDeleted` is missing
- no explicit timezone-change event exists beyond profile updates
- no explicit goal-deleted event exists; current model should treat non-active `GoalUpdated` statuses as state transitions

Recommendation:

- do not block phase 1 on new contracts
- add `BodyMetricDeleted` before exposing strict historical correction guarantees for body trend charts

### 7.3 Topic and Partitioning Rules

Consume from existing topics:

- `workout-events`
- `nutrition-events`
- `progress-events`
- `user-events`

Partitioning key:

- `userId`

Rules:

- handlers must be idempotent
- deletes and updates must support replay
- do not assume cross-topic total ordering

---

## 8. Internal Processing Model

Recommended processing pipeline:

1. consume domain event
2. validate envelope and payload
3. persist processed `eventId` for idempotency
4. update narrow daily fact tables or projections
5. recompute affected user summary rows

Rule:

- prefer incremental projection updates for day-level facts
- allow targeted recomputation per user and per date when update/delete events arrive

---

## 9. Persistence Direction

Recommended initial tables:

```text
analytics_user_daily_body
analytics_user_daily_workout
analytics_user_daily_nutrition
analytics_user_goal_progress
analytics_user_dashboard_snapshot
analytics_processed_events
```

Suggested meanings:

- `analytics_user_daily_body`
  - one row per user per date with latest derived body facts
- `analytics_user_daily_workout`
  - one row per user per date with workout totals
- `analytics_user_daily_nutrition`
  - one row per user per date with consumed totals and target baselines
- `analytics_user_goal_progress`
  - latest derived status per goal
- `analytics_user_dashboard_snapshot`
  - read-optimized summary row for the home dashboard
- `analytics_processed_events`
  - idempotency ledger keyed by `eventId`

Rule:

- keep schemas denormalized for fast reads
- keep enough source identifiers to support update/delete compensation

---

## 10. Security Notes

Mandatory direction:

- require JWT authentication for non-health endpoints
- scope reads to the authenticated user
- do not expose raw event payload history externally in phase 1
- do not log sensitive user nutrition/body details at full fidelity
- validate event payload shape before projection writes

Residual risk:

- analytics may temporarily lag behind source systems
- user-facing copy must not claim medical or diagnostic certainty

---

## 11. Read Caching Direction

Redis is not mandatory to make analytics correct, but it is a pragmatic optimization for this service once the API surface is in use.

Recommended phase 1 posture:

- keep analytics projections in PostgreSQL as the durable read model
- add Spring Cache in front of the hottest read endpoints
- default local and test environments to in-memory cache
- enable Redis only for shared, cross-instance cache when deployed beyond one service replica

Recommended first cached endpoints:

- `GET /api/v1/analytics/dashboard`
- `GET /api/v1/analytics/body/trends`
- `GET /api/v1/analytics/workouts/summary`
- `GET /api/v1/analytics/nutrition/summary`
- `GET /api/v1/analytics/goals/progress`

Recommended invalidation rule in phase 1:

- after any successful projection mutation or targeted rebuild, evict only cache entries for the affected `userId`
- Redis-backed deployments must scan each user-scoped analytics cache with
  `SCAN "<cacheName>::<userId>*"` and remove matches with `UNLINK`
- do not use Redis `KEYS` for invalidation, because it blocks the keyspace and can amplify write-triggered load spikes

Reason:

- these endpoints are deterministic derived reads
- projections already absorb eventual-consistent writes
- all current analytics caches are user-scoped; targeting the event `userId` prevents unrelated users from cold-cache reloads
- `SCAN` plus `UNLINK` keeps invalidation incremental and non-blocking while still clearing every cached variant for the changed user

Do not use Redis as:

- the source of truth for analytics projections
- the only copy of derived data
- a substitute for idempotent event processing

---

## 12. AI Tooling Decision

Short answer:

- no, AI tooling is not necessary for the first analytics-service foundation

Reason:

- your first milestone is deterministic aggregation, not inference
- dashboard totals, streaks, adherence, and goal progress are better implemented as explicit formulas
- adding LLMs or ML before stable event contracts will increase cost, latency, and debugging surface

AI becomes reasonable later in three narrow cases:

### 11.1 Insight Generation

Examples:

- turning computed metrics into readable coaching summaries
- generating weekly recap text from already-computed analytics

Rule:

- AI should consume analytics outputs, not raw transactional writes

### 11.2 Natural Language Query

Examples:

- "How consistent was I this month?"
- "Did my protein improve after I changed goals?"

Rule:

- keep the analytics API deterministic underneath
- AI only translates user language into safe query intents or renders explanations

### 11.3 Anomaly or Pattern Detection

Examples:

- spotting unusual calorie drops
- highlighting stalled weight progress

Rule:

- this should come after you have enough historical data and trusted baselines

Recommended current position:

- build analytics without AI first
- leave one extension seam such as `insight-service` or `analytics narrative generator` later
- do not put LLM calls in the synchronous dashboard request path

---

## 13. Recommended Rollout Order

### Phase 1

- finalize analytics API contracts
- consume existing domain events
- build daily projection tables
- expose dashboard, body trends, workout summary, nutrition summary, and goal progress reads

### Phase 2

- add idempotency ledger and replay tooling
- support update/delete compensation thoroughly
- add operational metrics and consumer lag visibility

### Phase 3

- add `BodyMetricDeleted` contract
- add targeted backfill or recompute endpoints for internal use
- optimize snapshot recomputation strategy

### Phase 4

- optionally add AI-generated narrative insights on top of deterministic analytics
- optionally add natural-language analytics query as a separate capability

---

## 14. Immediate Practical Recommendation

For this repository, the best next implementation step is:

1. scaffold `analytics-service` as a Spring Boot read service
2. consume `WorkoutLogged`, `WorkoutUpdated`, `WorkoutDeleted`
3. consume `MealLogged`, `MealUpdated`, `MealDeleted`
4. consume `GoalUpdated`, `BodyMetricsRecorded`, and `UserProfileUpdated`
5. expose one initial endpoint: `GET /api/v1/analytics/dashboard`

This gives the team one small but complete vertical slice before broadening the API surface.
