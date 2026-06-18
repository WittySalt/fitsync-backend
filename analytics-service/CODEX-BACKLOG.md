# Analytics-Service Implementation Backlog (for Codex)

<!-- ────────────────────────────────────────────────────────────────────────
     KICKOFF PROMPT — copy the block below verbatim to hand this backlog to Codex.
     ──────────────────────────────────────────────────────────────────────── -->
> **Kickoff prompt for Codex:**
>
> You implemented the FitSync `backend/analytics-service`. I've written a ratified
> implementation backlog at `backend/analytics-service/CODEX-BACKLOG.md` (tasks AN-1 through
> AN-12, with conventions, acceptance criteria, and execution order). Read it in full before
> starting.
>
> Start with the P0 foundation tasks in order: AN-1, then AN-2, then AN-3. Do not jump ahead to
> P1+ until all three P0 tasks are done and green.
>
> Working rules:
> - Follow the conventions listed at the top of the backlog (DTOs as records,
>   `BaseAuditableEntity`, event contracts in `event-contracts`, cache names in `CacheConfig` +
>   eviction in `AnalyticsCacheInvalidationService`, Flyway `V{n}__` migrations, mirrored tests).
> - Reuse existing patterns — for anything event-producing, replicate the outbox+relay already in
>   `auth-service`/`nutrition-service`; do not hand-roll a producer.
> - One task = one focused commit (or PR). After each task: write/extend unit tests, run
>   `mvn -pl analytics-service test` and confirm green before moving on.
> - For AN-7 and AN-8, first verify whether the contribution entities / upstream event payloads
>   carry the required fields. If they don't, stop and report it as a blocker rather than
>   guessing — degrade as the backlog describes.
> - If any acceptance criterion is ambiguous or conflicts with existing code, pause and ask
>   before implementing.
>
> Begin with AN-1. Show me your plan for it (files to add/change, the cron wiring, and the test
> cases) before writing code.

---

> **Context.** `backend/analytics-service` is a CQRS read-model service. It consumes Kafka
> events (Workout/Meal Logged·Updated·Deleted, BodyMetricsRecorded, GoalUpdated), maintains
> projection tables (daily body / daily workout / daily nutrition / goal progress / dashboard
> snapshot + meal/workout contribution detail tables), and exposes read-only analytics APIs.
>
> **Conventions to follow (do not reinvent):**
> - DTOs are `record` types in `com.fitsync.analytics.dto`.
> - Entities extend `BaseAuditableEntity` (from `persistence-support`).
> - Outbound event contracts live in the `event-contracts` module under
>   `com.fitsync.contracts.events.*`.
> - An **outbox + relay** pattern already exists in `auth-service`, `nutrition-service`,
>   `user-service`, `workout-service` (`OutboxEventEntity`, `OutboxDomainEventPublisher`,
>   `OutboxRelayScheduler`, `SpringKafkaEventProducer`, `FallbackKafkaEventProducer`).
>   **Replicate it** for analytics — do not hand-roll a new producer.
> - Cache names are centralized in `config/CacheConfig`. Every new cached endpoint needs a new
>   cache name there **and** a matching eviction in `service/AnalyticsCacheInvalidationService`.
> - Flyway migrations live in `src/main/resources/db/migration` (next is `V4__...`).
> - Tests mirror `src/main` into `src/test`. Each task must keep
>   `mvn -pl analytics-service test` green.
> - Read endpoints are user-scoped via JWT (`CurrentUserPrincipal`); admin/internal endpoints
>   live under `/api/v1/internal/analytics` and require the admin role.

**Recommended execution order:** AN-1 → AN-2 → AN-3 (foundation) → AN-4/5/6 (charts) →
AN-10 (wires up notification-service) → AN-9/7/8/11 (insights) → AN-12 (hardening).
AN-7/AN-8 depend on upstream event payload fields — verify the fields first; if missing, mark
the task blocked and degrade gracefully as noted.

---

## P0 — Foundation fixes (do first; new features depend on correct data)

### AN-1 · Nightly rollup job (fix streak / day-rollover not recomputing)
**Problem.** There is no `@Scheduled` anywhere in the service. `activeStreakDays`,
`todayWorkoutCount`, and goal `daysRemaining` are only recomputed when an event arrives. A user
*ceasing* activity produces no event, so a broken streak stays stale, today's counters never
reset, and goal countdowns never expire.

**Changes.**
- Add `service/AnalyticsDailyRollupService` running daily at UTC 00:05 via
  `@Scheduled(cron = "${fitsync.analytics.rollup.cron}")` (cron value in `application.yml`).
- Iterate over all users that have a dashboard snapshot, in batches (do not load the whole
  table at once), and recompute streak / today / goal countdown by reusing the existing
  `AnalyticsRebuildServiceImpl.rebuildDashboard(userId)` logic. Mark goals whose `targetDate`
  has passed as expired.
- Enable scheduling on `AnalyticsServiceApplication` with `@EnableScheduling` (see how the
  other services enable `OutboxRelayScheduler`).
- Emit structured logs: users processed + elapsed time.

**Implementation constraints (ratified after plan review — read before coding):**

1. **Goal expiry = Option B: write `"EXPIRED"` into the projection, behind a single shared
   predicate.** Rationale: the dashboard snapshot already materializes `todayCaloriesTarget`
   from the *stored* goal status (`applyTargetsFromGoals` filters `ACTIVE`), so writing the
   status lets the existing target logic drop expired targets with zero changes, in one
   coherent `rebuildDashboard` pass. Read-time derivation would scatter the predicate across
   `getGoalProgress`, the dashboard goal summaries, **and** `applyTargetsFromGoals`, and would
   still depend on the nightly rollup anyway.
   - Extract one helper, e.g. `GoalExpiryPolicy.isExpired(targetDate, todayUtc)` (or a
     package-private method), and call it from **both** the rollup **and**
     `GoalUpdatedEventConsumerImpl` so the two write paths can never diverge. Adding the check
     to the consumer (mirror) is recommended hardening — without it the rollup self-heals the
     status within ≤24h anyway; with it, the ≤24h "flicker back to ACTIVE after an unrelated
     edit to an already-past goal" window is closed.
   - **Expired-goal field convention** (must match the existing non-`ACTIVE` convention in
     `GoalUpdatedEventConsumerImpl` lines 66-70 — do NOT invent a third convention):
     `goalStatus = "EXPIRED"`, `onTrack = false`, `daysRemaining = null` (**not 0**),
     `lastEvaluatedAt = now(clock)`.
   - For *future* active goals the rollup should populate `daysRemaining` (UTC today →
     `targetDate`). Note this field is currently never set for active goals, so this fills a
     real gap.

2. **Respect the existing streak grace-day semantics — do not "fix" them.**
   `calculateActiveStreak` (`AnalyticsRebuildServiceImpl.java:254-274`) intentionally grants a
   one-day grace for *today*: if yesterday was active and the run was continuous, the streak is
   **preserved** while today is still unlogged; it only decays to 0 once the last activity is
   `today-2` or earlier. Document this in Javadoc. Streak assertions must target this real
   behavior, not the literal "none today → decays" wording above.

3. **Batch must isolate failures and own its transaction boundary.** Wrap each user in
   try/catch (log the failing `userId`, continue; summarize processed/failed counts). Each
   user's "expire goals + rebuildDashboard" must run in **one** transaction — but beware the
   self-invocation pitfall: a `@Transactional` method called from within the same bean's
   `runDailyRollup()` is not proxied. Put the per-user unit in a **separate bean** (or reuse
   `AnalyticsRebuildService`'s transaction) rather than self-invoking.

4. **Minor (acceptable as-is, just be aware):** `AnalyticsCacheInvalidationService.evictAllForUser`
   is `allEntries = true` (global, not per-user), so the per-user `rebuildDashboard` calls flush
   all caches N times during the nightly run. Fine for a nightly job; optionally evict once after
   the batch instead.

**Acceptance.**
- "Last activity `today-2` or earlier → streak = 0"; "yesterday active, none today → streak
  preserved (grace day)"; "no row for today → `todayWorkoutCount` recomputed to 0".
- "Goal `targetDate` passed → `goalStatus = "EXPIRED"`, `daysRemaining = null`,
  `onTrack = false`, saved"; "future active goal → `daysRemaining` recomputed, status stays
  `ACTIVE`".
- Rollup processes snapshot users in batches without loading the whole table; one failing user
  does not abort the batch.
- Job is idempotent (re-running yields identical results).

### AN-2 · Fix WEEK/MONTH bucketing in body/trends
**Problem.** `AnalyticsServiceImpl.java:106-122` groups by `bucketStart` into a `LinkedHashMap`
(later row overwrites earlier in the same bucket) and then returns `row.getMetricDate()` (the
raw date, not the bucket start) with no in-bucket aggregation. WEEK/MONTH results are wrong.

**Changes.** Aggregate per bucket — the point date returned is the **bucket start**
(`bucketStart`); within a bucket take the **last valid value** (or mean) for weight / body-fat /
waist (pick one and document the semantics in Javadoc); recompute `weightDeltaFromPrevious`
across adjacent buckets after aggregation. DAY bucket behavior must not change.

**Acceptance.** Unit tests cover cross-week / cross-month ranges and multiple days per bucket;
DAY bucket regression test stays green.

### AN-3 · Range cap on trend/series endpoints
**Problem.** `getBodyTrends` (and the series endpoints added later) have no upper bound on
`from..to`, so a large range returns an unbounded result set.

**Changes.** Extend `validateDateRange` with a maximum-span check (e.g. DAY ≤ 370 days; throw
`BadRequestException` when exceeded). Limit value lives in config.

**Acceptance.** Over-limit returns 400 with a clear message; boundary-value unit tests.

---

## P1 — High-value read features (unlock frontend charts)

### AN-4 · Per-day workout / nutrition series endpoints
**Problem.** Workout/nutrition currently expose only range **totals**; the frontend cannot draw
per-day lines.

**Changes.** Add `GET /api/v1/analytics/workouts/series` and `/nutrition/series` (params
`from,to,bucket`), reading per-day points from `daily_workout` / `daily_nutrition` and reusing
the AN-2 bucketing logic. Add DTOs + new cache names (wire `CacheConfig` and
`AnalyticsCacheInvalidationService`).

**Acceptance.** Points returned ascending by date; empty range returns empty `points` rather
than erroring; cache hit/evict tests.

### AN-5 · Period-over-period comparison
**Changes.** Add `GET /api/v1/analytics/summary/compare?period=WEEK|MONTH` returning current vs
previous period for the key workout/nutrition metrics, with both absolute Δ and Δ%. Reuse the
existing summary aggregation rather than duplicating SQL.

**Acceptance.** When the previous period has no data, Δ% returns null (no divide-by-zero);
cross-month / cross-year boundary unit tests.

### AN-6 · Activity calendar / heatmap data
**Changes.** Add `GET /api/v1/analytics/activity/calendar?from&to` returning per-day activity
intensity (workout count / minutes) for a GitHub-style heatmap.

**Acceptance.** Days with no activity return 0; range cap reuses AN-3.

### AN-7 · Workout structure breakdown (by muscle group / type)
**Problem.** `AnalyticsWorkoutContributionEntity` holds detail but there is no breakdown query.

**Changes.** Check whether the contribution entity carries muscle-group / type fields. **If
not, add the fields to the projection first** (requires the workout event payload to supply
them — if it does not, mark this task blocked on the upstream contract and degrade to
"breakdown by exercise count"). Add `GET /api/v1/analytics/workouts/breakdown`.

**Acceptance.** Distribution sums match `totalExerciseCount` from the range summary.

### AN-8 · Nutrition structure breakdown (macro split / by meal type)
**Changes.** Add `GET /api/v1/analytics/nutrition/breakdown` outputting P/C/F calorie share
and (if meal contributions carry meal type) distribution by meal. Base the split on calorie
conversion, not raw grams; document the conversion factors.

**Acceptance.** Macro shares sum to 100% (handle rounding); empty data returns zeros.

---

## P2 — Insight layer + outbound events

### AN-9 · Goal ETA forecast
**Problem.** Goal progress only exposes the `onTrack` boolean.

**Changes.** Fit a simple linear regression over the relevant daily metric to extrapolate a
**projected completion date**, compare it to `targetDate`, and output "ahead / behind by X
days". Add fields to `AnalyticsGoalProgressResponse` (additive, backward compatible).

**Acceptance.** With < 2 data points the forecast returns null; flat / reverse trends do not
emit a misleading ETA.

### AN-10 · Outbound derived events (feed notification-service)
**Problem.** Analytics has no producer; `notification-service` is an empty shell.

**Changes.** **Replicate** the auth/nutrition outbox pattern (`OutboxEventEntity`,
`OutboxDomainEventPublisher`, `OutboxRelayScheduler`, `SpringKafkaEventProducer`,
`FallbackKafkaEventProducer`, plus the relay table migration). Add `GoalAchievedPayload`,
`StreakMilestoneReachedPayload`, `WeeklyRecapGeneratedPayload` to `event-contracts`. Publish
via the outbox when rebuild/rollup detects an achievement or milestone.

**Acceptance.** Events are written to the outbox in the same transaction then relayed
(transactional consistency); idempotent (no duplicate publishes); contract tests modeled on the
existing `*ContractsTests` in `event-contracts`.

### AN-11 · Achievements / PR endpoint
**Changes.** Add `GET /api/v1/analytics/achievements`: longest streak, max single-session
duration / calories, cumulative workout-count milestones, computed from the contribution detail
(decide materialized table vs on-the-fly by data volume and note the choice).

**Acceptance.** Milestone thresholds are configurable; empty data returns an empty list.

---

## P3 — Engineering quality

### AN-12 · Consumer DLQ + projection observability + range backfill
**Changes.**
- Configure dead-letter handling on every `*KafkaListener` (Spring Kafka `DefaultErrorHandler`
  + DLT) so a poison event does not block its partition.
- Expose `projectionUpdatedAt` staleness and consumer lag as Micrometer/actuator metrics.
- Extend the internal rebuild: it currently supports a **single date** only — add a
  `from..to` range / full backfill endpoint for re-deriving after a bug fix.

**Acceptance.** A poison event lands in the DLT and the offset advances; backfill is idempotent;
new metrics visible under `/actuator/metrics`.

---

## Definition of done (every task)
- `mvn -pl analytics-service test` green; new logic has unit tests.
- New endpoints documented with OpenAPI annotations matching the existing controller style.
- New caches wired into both `CacheConfig` and `AnalyticsCacheInvalidationService`.
- Schema changes ship as a new `V{n}__...sql` Flyway migration (never edit an applied one).
- Backward-compatible DTO changes only (add fields, don't remove/rename).
