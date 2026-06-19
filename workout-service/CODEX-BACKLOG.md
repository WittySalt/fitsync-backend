# Workout-Service Implementation Backlog (for Codex)

<!-- ────────────────────────────────────────────────────────────────────────
     KICKOFF PROMPT — copy the block below verbatim to hand this backlog to Codex.
     ──────────────────────────────────────────────────────────────────────── -->
> **Kickoff prompt for Codex:**
>
> You are working on the FitSync `backend/workout-service`. I've written a ratified
> implementation backlog at `backend/workout-service/CODEX-BACKLOG.md` (tasks WO-1 through
> WO-11, with conventions, acceptance criteria, and execution order). Read it in full before
> starting.
>
> Start with the P0 domain-gap tasks in order: WO-1, then WO-2, then WO-3. Do not jump ahead to
> P1+ until all three P0 tasks are done and green.
>
> Working rules:
> - Follow the conventions listed at the top of the backlog (DTOs as records, entities extend
>   `BaseAuditableEntity`, event contracts in `event-contracts`, outbox+relay already present in
>   this service, Flyway `V{n}__` migrations, mirrored tests, validation via
>   `IllegalArgumentException` / `ResourceNotFoundException`).
> - Reuse existing patterns — for anything event-producing, use the outbox already wired in this
>   service (`OutboxDomainEventPublisher` / `DomainEventPublisher`); do not hand-roll a producer.
> - One task = one focused commit (or PR). After each task: write/extend unit + slice tests, run
>   `mvn -pl workout-service test` and confirm green before moving on.
> - For WO-9 (program structure) and WO-8 (import), if a schema/contract decision is ambiguous,
>   stop and report it as a blocker rather than guessing.
> - If any acceptance criterion is ambiguous or conflicts with existing code, pause and ask
>   before implementing.
>
> Begin with WO-1. Show me your plan for it (files to add/change, the endpoint contract, and the
> test cases) before writing code.

---

> **Context.** `backend/workout-service` owns three aggregates, all user-scoped via JWT
> (`CurrentUserPrincipal`):
> - **Exercise catalog** (`ExerciseEntity`) — global (`ownerUserId is null`) + per-user custom
>   exercises. Today: create + paged search only.
> - **Workout plans** (`WorkoutPlanEntity` → `WorkoutPlanExerciseEntity` →
>   `WorkoutPlanExerciseSetTargetEntity`) — full CRUD + archive.
> - **Workout sessions** (`WorkoutSessionEntity` → `WorkoutExerciseLogEntity` →
>   `WorkoutExerciseSetLogEntity`) — upsert / history / latest / get / delete.
>
> On completed sessions it emits `WorkoutLogged` / `WorkoutUpdated` / `WorkoutDeleted` through an
> **outbox** to the `workout-events` Kafka topic, consumed by `analytics-service`. That makes
> workout-service the upstream source of truth for analytics — data-integrity tasks here
> (WO-4) directly affect analytics correctness.
>
> **Conventions to follow (do not reinvent):**
> - DTOs are `record` types in `com.fitsync.workout.dto`, often with a `@Schema(example=...)`
>   block and Bean Validation annotations (see `UpsertWorkoutSessionRequest`).
> - Entities extend `BaseAuditableEntity` (from `persistence-support`); ids are app-assigned
>   `UUID.randomUUID()`.
> - Outbound event contracts live in `event-contracts` under
>   `com.fitsync.contracts.events.workout`, with a matching test in
>   `WorkoutEventContractsTests`.
> - An **outbox + relay** already exists in THIS service (`messaging/OutboxDomainEventPublisher`,
>   `DomainEventPublisher`, `OutboxRelayScheduler`, `SpringKafkaEventProducer`,
>   `FallbackKafkaEventProducer`). Publish new events via `domainEventPublisher.publish(...)`
>   **inside the same transaction** as the state change — never add a second producer.
> - Validation: throw `IllegalArgumentException` for 400-class input errors and
>   `com.fitsync.web.error.ResourceNotFoundException` for 404s (the shared web layer maps both).
> - Flyway migrations live in `src/main/resources/db/migration` (next is `V3__...`). Never edit
>   an applied migration.
> - Tests mirror `src/main` into `src/test`. Each task must keep `mvn -pl workout-service test`
>   green. Controller slice tests exist (`WorkoutControllerTests`), as do persistence tests
>   (`WorkoutServicePersistenceTests`) — extend the matching layer.
> - All read/write paths are scoped by `currentUser.userId()` — every new query MUST filter by
>   user. Reuse `findAccessibleExercise` for any exercise lookup so global + owned visibility
>   stays consistent.

**Recommended execution order:** WO-1 → WO-2 → WO-3 (close the domain gaps) →
WO-4 (data integrity, unblocks trustworthy analytics) → WO-5 → WO-6 → WO-7 (core UX) →
WO-8 → WO-9 → WO-10 (integration/scale) → WO-11 (hardening). WO-9 is the heaviest; do not start
it until P0/P1 are green.

---

## P0 — Domain gaps (the model is unusable without these)

### WO-1 · Instantiate a session from a plan (`POST /plans/{planId}/start`)
**Problem.** `createSession` requires the client to re-send every exercise and set. Even "do the
plan as written" forces the frontend to copy all `setTargets` into `performedSets`. There is no
server path from a plan to a session, so the plan aggregate delivers almost no value at log time.

**Changes.**
- Add `POST /api/v1/workouts/plans/{planId}/start` → returns a `WorkoutSessionView`.
- Resolve the plan via the existing `findPlan(userId, planId)` (404 if not owned); reject if the
  plan is `ARCHIVED` (mirror the existing `validateSessionRequest` archived-plan rule).
- Create a persisted session with `status = IN_PROGRESS`, `source = PLAN`, `startedAt = now`,
  `planId` set, `workoutType`/`name` seeded from the plan, and one `WorkoutExerciseLogEntity`
  per `WorkoutPlanExerciseEntity` (carry `sequence`, `exerciseNameSnapshot`, and seed `sets`
  from `setTargets` — `setNumber` preserved, target reps/weight copied as the planned values,
  performed metrics left null for the user to fill in).
- Do **not** emit `WorkoutLogged` here (only `COMPLETED` sessions emit). Completion happens later
  via the existing `updateSession` path (or WO-6).

**Acceptance.**
- Starting from a plan yields a session whose exercise/set skeleton matches the plan; archived
  plan → 400; non-owned/missing plan → 404; no domain event emitted for the `IN_PROGRESS` result.
- Persistence test asserts the seeded set count equals the plan's total `setTargets`.

### WO-2 · Plan activation lifecycle (DRAFT → ACTIVE)
**Problem.** `WorkoutPlanStatus.ACTIVE` exists but is unreachable: `createPlan` hard-codes
`DRAFT` and the only transition is `archivePlan`. "The user's active plan" is a dead concept.

**Changes.**
- Add `POST /api/v1/workouts/plans/{planId}/activate` → sets `status = ACTIVE`, returns the view.
- **Decide and document the cardinality** (ratify before coding): recommended = **single active
  plan per user** — activating one plan demotes any other `ACTIVE` plan owned by the user back to
  `DRAFT` in the same transaction. (If multiple-active is preferred, say so and skip the demotion;
  do not leave it implicit.)
- Reactivating an `ARCHIVED` plan is allowed (ARCHIVED → ACTIVE); a `DRAFT`/`ACTIVE` plan
  activating is idempotent.
- Add an optional `GET /api/v1/workouts/plans/active` convenience read returning the active plan
  (404 if none), since the frontend will want "what am I currently following".
- Keep `listPlans(status=ACTIVE)` working (already supported by the repo filter).

**Acceptance.**
- Activating plan B while plan A is active leaves exactly one `ACTIVE` plan (single-active mode);
  activate is idempotent; archived→active works; all transitions are user-scoped.

### WO-3 · Complete exercise management + per-user code uniqueness
**Problem.** `ExerciseService` exposes only create + search. A custom exercise, once created,
can't be viewed by id, edited, or removed. Worse, `code` uniqueness is **global**
(`existsByCode`), so two different users cannot both create a `"pushup"` custom code.

**Changes.**
- Add `GET /api/v1/workouts/exercises/{exerciseId}` (uses `findAccessibleById`; 404 if not
  global and not owned).
- Add `PUT /api/v1/workouts/exercises/{exerciseId}` — only the **owner** may edit a custom
  exercise; editing a global (`ownerUserId is null`) exercise → 403 (chosen because the catalog exercise is visible but not user-editable).
  Editable fields mirror `CreateExerciseRequest` minus `code` (code is immutable once set).
- Add `DELETE /api/v1/workouts/exercises/{exerciseId}` — **soft delete** to
  `ExerciseStatus.ARCHIVED` (do NOT hard-delete; plans/sessions reference it and snapshot the
  name, but the FK must not dangle). Owner-only. Archived exercises drop out of `searchAccessible`
  (already filters `status = ACTIVE`) and out of `findAccessibleById` for new plan/session
  references.
- Change custom-code uniqueness to **per-owner**: replace `existsByCode(code)` with
  `existsByCodeAndOwnerUserId(code, userId)`. Add a Flyway `V3__` migration adjusting the unique
  constraint (drop the global unique on `code`; add a composite unique on
  `(owner_user_id, code)` — confirm global rows with `owner_user_id IS NULL` behave under your DB's
  null-in-unique semantics; if Postgres, a partial unique index per case may be cleaner —
  document the choice).

**Acceptance.**
- Two users create the same `code` successfully; a single user cannot duplicate their own code;
  owner can edit/archive, non-owner gets 404/403; archived exercise no longer appears in search
  and cannot be attached to a new plan/session, but existing plans/sessions still render via the
  stored `exerciseNameSnapshot`.

---

## P1 — Core experience + trustworthy data

### WO-4 · Server-side aggregation & integrity (volume / duration / counts)
**Problem.** `totalVolumeKg`, `durationMinutes`, and (per-exercise) volume are taken verbatim
from the client and stored (`applySessionRequest` / `replaceSessionExercises`). Different clients
compute these differently, and nothing validates them — yet these exact numbers flow to
analytics via `WorkoutLoggedPayload`. Garbage in → wrong dashboards.

**Changes.**
- Derive, don't trust, the aggregates that are computable from child rows:
  - per-exercise `totalVolumeKg = Σ(set.reps × set.weightKg)` over its `sets` (when both present);
  - per-exercise `performedSets = count(sets)` and `performedReps = Σ(set.reps)` when sets are
    provided (fall back to the client-supplied scalar only when no set rows exist — e.g. cardio).
  - session `durationMinutes`: if both `startedAt` and `completedAt` are present, derive from the
    interval; reject (400) a client value that disagrees by more than a small tolerance, OR
    overwrite and document which (recommended: derive-and-overwrite, treat client value as a hint).
- Add the missing validations: `startedAt` not in the future; `completedAt > startedAt` already
  checked — also ensure `durationMinutes`/`caloriesBurned` non-negative (DTO `@Min(0)` exists;
  add the cross-field checks in the service).
- Keep the existing `validateUniqueNumbers` invariants.
- **Centralize** the derivation in one helper used by both `createSession` and `updateSession`
  so create/update can never diverge.

**Acceptance.**
- A session posted with inconsistent `totalVolumeKg` is stored with the **derived** value;
  `WorkoutLoggedPayload.durationMinutes` reflects the server-derived duration; future `startedAt`
  → 400; cardio session with no set rows keeps the client scalars. Unit tests cover each rule.

### WO-5 · Personal records + per-exercise history
**Problem.** No way to answer "show my bench-press history" or "what's my best set". A fitness
app's core progression view is missing, and `analytics-service` AN-11 wants PR data it cannot
derive without the raw set rows that live here.

**Changes.**
- Add `GET /api/v1/workouts/exercises/{exerciseId}/history?from&to&page&size` → paged, time-
  descending list of every set/exercise-log the user performed for that exercise across sessions
  (join `WorkoutExerciseLogEntity` by `exercise_id` + session `user_id`). New repository query +
  DTO. Reuse a sane range cap (mirror analytics AN-3: reject oversized ranges with 400).
- Add `GET /api/v1/workouts/exercises/{exerciseId}/records` → current PRs for that exercise:
  max weight, max reps, max single-set volume, estimated 1RM (Epley:
  `weight × (1 + reps/30)` — document the formula), best total session volume. Compute from set
  logs of `COMPLETED` sessions only.
- Emit a `PersonalRecordAchieved` event (new payload in `event-contracts` +
  `WorkoutEventContractsTests`) **inside the session-completion transaction** in WO-4's helper
  when a completed session beats a prior PR for an exercise. Publish via the existing outbox.
  This gives `notification-service` something real to consume and feeds AN-11.

**Acceptance.**
- History returns only the caller's data, ordered by session `startedAt` desc, range-capped;
  records reflect the true max across completed sessions; logging a new max emits exactly one
  `PersonalRecordAchieved` (idempotent — re-completing the same session does not re-fire); ties do
  not fire.

### WO-6 · Incremental / live logging (status transitions + set append)
**Problem.** Mid-workout logging today means PUTting the whole session every set, which
`replaceSessionExercises` fully clears and re-inserts — bad for concurrency, "save as you go", and
performance. There are also no explicit start/complete transitions; everything is upsert.

**Changes.**
- Add lifecycle transition endpoints that are cheaper and clearer than full upsert:
  - `POST /api/v1/workouts/sessions/{id}/complete` — sets `status = COMPLETED`, requires
    `completedAt` (default to now), runs the WO-4 derivation, emits `WorkoutLogged` once.
  - `POST /api/v1/workouts/sessions/{id}/cancel` — sets `status = CANCELLED`.
- Add append-style logging so the client can record one exercise/set without resending the rest:
  - `POST /api/v1/workouts/sessions/{id}/exercises` (append/replace a single exercise log)
  - `PUT /api/v1/workouts/sessions/{id}/exercises/{exerciseLogId}/sets/{setNumber}` (upsert one
    set). Keep `validateUniqueNumbers` invariants per the existing rules.
- A completed/cancelled session should reject further set appends (400) — only `PLANNED` /
  `IN_PROGRESS` sessions are editable live. Document the allowed transition matrix.

**Acceptance.**
- Appending a single set does not touch sibling exercises/sets; completing emits exactly one
  `WorkoutLogged`; appending to a `COMPLETED` session → 400; transition matrix enforced and tested.

### WO-7 · Plan duplication & "repeat last workout"
**Changes.**
- `POST /api/v1/workouts/plans/{planId}/duplicate` → deep-copies a plan (new ids for plan /
  exercises / setTargets), name suffixed (e.g. `" (Copy)"`), status `DRAFT`, owned by the caller.
  Allow duplicating any **accessible** plan (own plans; later this enables template libraries).
- `POST /api/v1/workouts/sessions/repeat-latest` (or `/sessions/{id}/repeat`) → seeds a new
  `IN_PROGRESS` session from a previous session's exercise/set skeleton (reuse the WO-1 seeding
  helper), performed metrics cleared.

**Acceptance.**
- Duplicate produces an independent plan (editing the copy doesn't touch the original); repeat
  produces an editable `IN_PROGRESS` session with no carried-over performed values and emits no
  event until completed.

---

## P2 — Integration & scale

### WO-8 · External / device workout ingestion
**Problem.** `WorkoutSource.DEVICE` and `IMPORTED` exist but nothing can produce them — there's
no ingestion path for Apple Health / Google Fit / Strava-style data.

**Changes.** Add an import endpoint (e.g. `POST /api/v1/workouts/sessions/import`) accepting a
batch of externally-sourced sessions with `source = DEVICE|IMPORTED` and an `externalId` for
**idempotency** (add `external_id` + `source` columns via `V{n}__` migration; unique on
`(user_id, source, external_id)` so re-imports don't duplicate). Map to the existing session
model; reuse WO-4 derivation. Skip-or-update on duplicate `externalId` (document which).

**Acceptance.** Re-importing the same `externalId` does not create a duplicate; imported
completed sessions emit `WorkoutLogged` like any other; malformed rows are rejected per-row with a
clear error without aborting the whole batch (decide all-or-nothing vs partial and document it).

### WO-9 · Multi-day program structure (heaviest task — ratify the model first)
**Problem.** A plan is a flat exercise list, so it cannot express a weekly split
(Push/Pull/Legs, multi-day/multi-week programs). This is a real schema extension — do **not**
start until P0/P1 are green and the model is ratified.

**Changes (proposal — confirm before coding).** Introduce a `program → day → exercise` layering,
e.g. a `WorkoutPlanDayEntity` between plan and plan-exercise (`dayNumber`, `label`,
`weekNumber?`), with `WorkoutPlanExerciseEntity.day` replacing the direct plan link. Provide a
**backward-compatible migration**: existing flat plans become a single implicit "Day 1". Extend
WO-1 (`/start`) to take an optional `dayNumber`. This touches `WorkoutPlanView` and its
serialization — keep changes additive where possible.

**Acceptance.** Existing flat plans still load and start (as Day 1); a multi-day plan round-trips
through create/get/update; `/plans/{id}/start?dayNumber=N` seeds only that day. **Blocker rule:**
if the view/DTO change can't stay backward-compatible, stop and report before migrating.

### WO-10 · User workout data export
**Changes.** Add `GET /api/v1/workouts/export` returning the caller's full workout data
(custom exercises + plans + sessions) as a single structured JSON document, for portability /
account-data requests. User-scoped; stream or cap to avoid unbounded memory on heavy accounts.

**Acceptance.** Export contains only the caller's data and round-trips conceptually with the
read models; a user with no data gets a well-formed empty document.

---

## P3 — Engineering quality

### WO-11 · Fix session-history N+1, expose catalog metadata, harden validation
**Changes.**
- **N+1 fix (do this first in the task).** `WorkoutSessionRepository.findHistory` has no
  `@EntityGraph`, but `listSessions` maps each row through `toSessionView`, which walks the lazy
  `exercises` (and each exercise's `exercise`) — one extra query per session on the most-used
  endpoint. Add `@EntityGraph(attributePaths = {"exercises", "exercises.exercise"})` to
  `findHistory` (matching `findBySessionIdAndUserId`), and verify pagination still works with the
  fetch join (use a count-query-safe approach; watch for the in-memory-pagination warning).
- **Catalog metadata endpoint.** Add `GET /api/v1/workouts/exercises/metadata` returning the enum
  vocabularies (`ExerciseType`, `MuscleGroup`, `ExerciseEquipment`, `WorkoutDifficulty`,
  `WorkoutType`, `GoalFocus`) so the frontend stops hard-coding dropdowns.
- **Validation hardening.** Audit cross-field rules not covered by DTO annotations and centralize
  the session-timing/aggregate checks introduced in WO-4.

**Acceptance.** A session-list response of N sessions issues a bounded number of queries
(asserted via a query-count or `@DataJpaTest` log assertion); metadata endpoint returns every enum
constant; validation regression tests stay green.

---

## Definition of done (every task)
- `mvn -pl workout-service test` green; new logic has unit + slice/persistence tests in the
  mirrored package.
- New endpoints carry OpenAPI annotations matching the existing controller/DTO style
  (`@Schema` examples like `UpsertWorkoutSessionRequest`).
- New events ship a payload in `event-contracts` **plus** an assertion in
  `WorkoutEventContractsTests`, and are published via the existing outbox inside the state-change
  transaction.
- Schema changes ship as a new `V{n}__...sql` Flyway migration (next free is `V3`); never edit an
  applied one.
- Every new query/endpoint is user-scoped via `CurrentUserPrincipal`.
- Backward-compatible DTO changes only (add fields; don't remove/rename).
