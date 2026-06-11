# Workout Service Design

## 1. Responsibility Boundary

`workout-service` should own workout-facing source facts that are created and updated by the user.

Recommended ownership split:

- `workout-service`
  - exercise catalog and user custom exercises
  - workout plans
  - plan exercises
  - workout sessions
  - exercise performance logs
- `user-service`
  - profile
  - preferences
  - body metrics
  - user goals
- `analytics-service`
  - workout streaks
  - weekly training volume
  - consistency scores
  - dashboard summaries
- `recommendation-service`
  - suggested plans
  - adaptive workout recommendations

Important rule:

- `workout-service` should store source workout facts
- derived outputs such as streak labels, readiness scores, and weekly summaries should be computed from source facts
- derived results should live in read models or analytics-facing responses, not as core write-model truth

---

## 2. Domain Slices

`workout-service` should not collapse plans and performed workouts into one table.

It should start with five write-model slices:

### 2.1 Exercises

The normalized exercise master record used by plans and performed sessions.

Fields:

- `exerciseId`
- `ownerUserId`
- `code`
- `name`
- `description`
- `exerciseType`
- `primaryMuscleGroup`
- `equipment`
- `difficulty`
- `status`
- `custom`

### 2.2 Workout Plans

Reusable plan definitions owned by a user.

Fields:

- `planId`
- `userId`
- `name`
- `description`
- `goalFocus`
- `workoutType`
- `difficulty`
- `estimatedDurationMinutes`
- `status`

### 2.3 Plan Exercises

The ordered exercise blueprint inside a plan.

Fields:

- `planExerciseId`
- `planId`
- `exerciseId`
- `exerciseNameSnapshot`
- `sequence`
- `targetSets`
- `targetReps`
- `targetWeightKg`
- `targetDurationSeconds`
- `targetDistanceKm`
- `restSeconds`
- `notes`
- `setTargets`

### 2.4 Workout Sessions

An actual workout instance created manually or from a plan.

Fields:

- `sessionId`
- `userId`
- `planId`
- `name`
- `workoutType`
- `status`
- `source`
- `startedAt`
- `completedAt`
- `durationMinutes`
- `caloriesBurned`
- `perceivedExertion`
- `notes`

### 2.5 Workout Exercise Logs

The performed exercise results within a session.

Fields:

- `exerciseLogId`
- `sessionId`
- `exerciseId`
- `exerciseNameSnapshot`
- `sequence`
- `performedSets`
- `performedReps`
- `totalVolumeKg`
- `distanceKm`
- `durationSeconds`
- `averageHeartRate`
- `maxHeartRate`
- `notes`
- `sets`

Pragmatic rule:

- first version should store exercise-level performed summaries
- set-by-set breakdown can be added later if the product truly needs live lifting detail

---

## 3. What Should Not Be Core Workout Write Models

The following should not be first-class write-model truth in `workout-service`:

- workout streak labels
- weekly consistency score
- training readiness score
- fatigue score
- achievement badges
- dashboard trend summaries
- recommendation ranking

Reason:

- these are derived
- formulas and product logic will change
- consumers should tolerate recalculation

Recommended placement:

- `analytics-service`
- `recommendation-service`
- dedicated read models

---

## 4. Recommended Aggregates

### 4.1 WorkoutPlan

```text
WorkoutPlan
- planId: UUID
- userId: UUID
- name: String
- description: String?
- goalFocus: GoalFocus
- workoutType: WorkoutType
- difficulty: WorkoutDifficulty
- estimatedDurationMinutes: Integer?
- status: WorkoutPlanStatus
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
- createdBy: String
- updatedBy: String
- version: Long
```

### 4.2 Exercise

```text
Exercise
- exerciseId: UUID
- ownerUserId: UUID?
- code: String
- name: String
- description: String?
- exerciseType: ExerciseType
- primaryMuscleGroup: MuscleGroup
- equipment: ExerciseEquipment
- difficulty: WorkoutDifficulty
- status: ExerciseStatus
- custom: Boolean
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
- createdBy: String
- updatedBy: String
- version: Long
```

### 4.3 WorkoutPlanExercise

```text
WorkoutPlanExercise
- planExerciseId: UUID
- planId: UUID
- exerciseId: UUID
- exerciseNameSnapshot: String
- sequence: Integer
- targetSets: Integer?
- targetReps: Integer?
- targetWeightKg: BigDecimal?
- targetDurationSeconds: Integer?
- targetDistanceKm: BigDecimal?
- restSeconds: Integer?
- notes: String?
- setTargets: WorkoutPlanExerciseSetTarget[]
```

### 4.4 WorkoutPlanExerciseSetTarget

```text
WorkoutPlanExerciseSetTarget
- setTargetId: UUID
- planExerciseId: UUID
- setNumber: Integer
- targetReps: Integer?
- targetWeightKg: BigDecimal?
- targetDurationSeconds: Integer?
- targetDistanceKm: BigDecimal?
- restSeconds: Integer?
```

### 4.5 WorkoutSession

```text
WorkoutSession
- sessionId: UUID
- userId: UUID
- planId: UUID?
- name: String
- workoutType: WorkoutType
- status: WorkoutSessionStatus
- source: WorkoutSource
- startedAt: Instant
- completedAt: Instant?
- durationMinutes: Integer?
- caloriesBurned: Integer?
- perceivedExertion: Integer?
- notes: String?
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
- createdBy: String
- updatedBy: String
- version: Long
```

### 4.6 WorkoutExerciseLog

```text
WorkoutExerciseLog
- exerciseLogId: UUID
- sessionId: UUID
- exerciseId: UUID
- exerciseNameSnapshot: String
- sequence: Integer
- performedSets: Integer?
- performedReps: Integer?
- totalVolumeKg: BigDecimal?
- distanceKm: BigDecimal?
- durationSeconds: Integer?
- averageHeartRate: Integer?
- maxHeartRate: Integer?
- notes: String?
- sets: WorkoutExerciseSetLog[]
```

### 4.7 WorkoutExerciseSetLog

```text
WorkoutExerciseSetLog
- setLogId: UUID
- exerciseLogId: UUID
- setNumber: Integer
- reps: Integer?
- weightKg: BigDecimal?
- durationSeconds: Integer?
- distanceKm: BigDecimal?
- restSeconds: Integer?
- perceivedExertion: Integer?
- notes: String?
```

---

## 5. Enums

```text
GoalFocus
- GENERAL_FITNESS
- WEIGHT_LOSS
- MUSCLE_GAIN
- STRENGTH
- ENDURANCE
- MOBILITY

WorkoutType
- STRENGTH
- CARDIO
- HIIT
- YOGA
- PILATES
- MOBILITY
- SPORTS
- CUSTOM

WorkoutDifficulty
- BEGINNER
- INTERMEDIATE
- ADVANCED

WorkoutPlanStatus
- DRAFT
- ACTIVE
- ARCHIVED

WorkoutSessionStatus
- PLANNED
- IN_PROGRESS
- COMPLETED
- CANCELLED

WorkoutSource
- MANUAL
- PLAN
- DEVICE
- IMPORTED

ExerciseType
- STRENGTH
- CARDIO
- MOBILITY
- BALANCE
- SPORT

ExerciseEquipment
- NONE
- BODYWEIGHT
- BARBELL
- DUMBBELL
- KETTLEBELL
- MACHINE
- CABLE
- BAND
- CARDIO_MACHINE
- OTHER

ExerciseStatus
- ACTIVE
- ARCHIVED
```

---

## 6. API Surface

Base path:

- `/api/v1/workouts`

Authenticated requests must derive `userId` from JWT.

### 6.1 Exercises

#### POST `/api/v1/workouts/exercises`

Purpose:

- create a custom exercise owned by the current user

#### GET `/api/v1/workouts/exercises`

Purpose:

- list active global exercises and active custom exercises owned by the current user

Response:

- `PageResponse<ExerciseView>`

Supported filters:

- `q`
- `exerciseType`
- `primaryMuscleGroup`
- `equipment`
- `difficulty`
- `page`
- `size`

### 6.2 Workout Plans

#### POST `/api/v1/workouts/plans`

Purpose:

- create a reusable workout plan for the current user

#### GET `/api/v1/workouts/plans`

Purpose:

- list the current user's plans

Response:

- `PageResponse<WorkoutPlanView>`

Supported filters:

- `status`
- `page`
- `size`

#### GET `/api/v1/workouts/plans/{planId}`

Purpose:

- fetch one plan with its ordered exercises

#### PUT `/api/v1/workouts/plans/{planId}`

Purpose:

- replace editable plan fields and its exercise blueprint

#### DELETE `/api/v1/workouts/plans/{planId}`

Purpose:

- archive a plan so it no longer appears as active

### 6.3 Workout Sessions

#### POST `/api/v1/workouts/sessions`

Purpose:

- create a session directly as `PLANNED`, `IN_PROGRESS`, or `COMPLETED`

#### GET `/api/v1/workouts/sessions`

Purpose:

- list the current user's sessions by date range and status

Response:

- `PageResponse<WorkoutSessionView>`

Supported filters:

- `from`
- `to`
- `status`
- `planId`
- `page`
- `size`

Paged list endpoints use zero-based `page`; `size` defaults to `20` and is capped at `100`.

#### GET `/api/v1/workouts/sessions/{sessionId}`

Purpose:

- fetch a session and its performed exercise logs

#### PUT `/api/v1/workouts/sessions/{sessionId}`

Purpose:

- update an existing session, including completion details and exercise results

#### DELETE `/api/v1/workouts/sessions/{sessionId}`

Purpose:

- delete a mistaken or duplicate workout log

#### GET `/api/v1/workouts/sessions/latest`

Purpose:

- return the newest workout session snapshot for dashboard use

---

## 7. Request and Response Models

### 7.1 Requests

```text
CreateWorkoutPlanRequest
- name
- description
- goalFocus
- workoutType
- difficulty
- estimatedDurationMinutes
- exercises[]

WorkoutPlanExerciseInput
- exerciseId
- sequence
- targetSets
- targetReps
- targetWeightKg
- targetDurationSeconds
- targetDistanceKm
- restSeconds
- notes
- setTargets[]

WorkoutPlanExerciseSetTargetInput
- setNumber
- targetReps
- targetWeightKg
- targetDurationSeconds
- targetDistanceKm
- restSeconds

UpsertWorkoutSessionRequest
- planId
- name
- workoutType
- status
- source
- startedAt
- completedAt
- durationMinutes
- caloriesBurned
- perceivedExertion
- notes
- exercises[]

WorkoutExerciseLogInput
- exerciseId
- sequence
- performedSets
- performedReps
- totalVolumeKg
- distanceKm
- durationSeconds
- averageHeartRate
- maxHeartRate
- notes
- sets[]

WorkoutExerciseSetLogInput
- setNumber
- reps
- weightKg
- durationSeconds
- distanceKm
- restSeconds
- perceivedExertion
- notes
```

Validation rules:

- `name` required and bounded
- `sequence` must be positive
- `performed` and `target` numeric fields must be non-negative
- `completedAt` must not be before `startedAt`
- session updates must reject user ownership mismatches

### 7.2 Responses

```text
WorkoutPlanView
- planId
- name
- description
- goalFocus
- workoutType
- difficulty
- estimatedDurationMinutes
- status
- exercises[]
- createdAt
- updatedAt

WorkoutPlanExerciseView
- planExerciseId
- exerciseId
- exerciseNameSnapshot
- sequence
- targetSets
- targetReps
- targetWeightKg
- targetDurationSeconds
- targetDistanceKm
- restSeconds
- notes
- setTargets[]

WorkoutSessionView
- sessionId
- planId
- name
- workoutType
- status
- source
- startedAt
- completedAt
- durationMinutes
- caloriesBurned
- perceivedExertion
- notes
- exerciseCount
- exercises[]
- createdAt
- updatedAt

WorkoutExerciseLogView
- exerciseLogId
- exerciseId
- exerciseNameSnapshot
- sequence
- performedSets
- performedReps
- totalVolumeKg
- distanceKm
- durationSeconds
- averageHeartRate
- maxHeartRate
- notes
- sets[]
```

---

## 8. Event Design

### 8.1 Incoming Events

Phase 1 recommendation:

- no required incoming events for the core write path

Reason:

- `workout-service` should remain independently writable through synchronous APIs
- plan and session writes should not wait on any upstream event choreography

Optional later consumers:

- `GoalUpdated` if the team wants goal-driven starter plan suggestions
- `UserPreferencesUpdated` if default plan generation later depends on user preferences

### 8.2 Outgoing Events

#### WorkoutLogged

Topic:

- `workout-events`

Purpose:

- notify downstream services that a completed workout session exists

When to publish:

- a session is created as `COMPLETED`
- or an existing session transitions into `COMPLETED`

#### WorkoutUpdated

Topic:

- `workout-events`

Purpose:

- notify downstream services that a previously published workout changed in a way that affects analytics, recommendations, or notifications

When to publish:

- duration changes
- calories change
- completion time changes
- exercise summary changes

#### WorkoutDeleted

Topic:

- `workout-events`

Purpose:

- allow downstream services to compensate derived state after a workout log is removed

Event rules:

- publish only after successful write or delete
- partition by `userId`
- keep payloads minimal
- do not publish free-form notes if they are not needed by consumers
- do not publish internal persistence details

### 8.3 Event Payload Direction

Recommended payload shape for initial contracts:

```json
{
  "sessionId": "1c90f1d8-a8e0-4777-a03f-5acb0f0d1f64",
  "planId": "7c064d0a-e2c6-4cd6-b8b7-e839aa5ce0b3",
  "workoutType": "STRENGTH",
  "status": "COMPLETED",
  "startedAt": "2026-06-11T18:30:00Z",
  "completedAt": "2026-06-11T19:20:00Z",
  "durationMinutes": 50,
  "caloriesBurned": 420,
  "exerciseCount": 6,
  "source": "PLAN"
}
```

Consumers:

- `analytics-service`
- `recommendation-service`
- `notification-service`

Pragmatic rule:

- event payloads should carry session-level summaries
- consumers should not require every set, rep, or note unless a proven use case appears

---

## 9. Persistence Direction

Recommended future tables:

```text
exercises
workout_plans
workout_plan_exercises
workout_plan_exercise_set_targets
workout_sessions
workout_exercise_logs
workout_exercise_set_logs
```

Recommended rules:

- `WorkoutPlan` is user-owned
- `Exercise` is either global or user-owned custom catalog data
- `WorkoutPlanExercise` is ordered by `sequence`
- `WorkoutPlanExercise` and `WorkoutExerciseLog` reference `exerciseId` and store `exerciseNameSnapshot` for historical display stability
- `WorkoutPlanExerciseSetTarget` and `WorkoutExerciseSetLog` are child rows with unique `(parent_id, set_number)` constraints
- `WorkoutSession` is the event source of truth for logged workouts
- `WorkoutExerciseLog` is deleted or replaced together with session updates when the service uses full replacement semantics
- a completed session should be immutable enough for auditability, but still allow explicit user correction flows

---

## 10. Rollout Order

### Phase 1

- define plan and session API contracts
- define `WorkoutLogged`, `WorkoutUpdated`, and `WorkoutDeleted`
- add shared event payload classes in `event-contracts`

### Phase 2

- implement workout plan CRUD
- implement workout session CRUD
- publish outbox-backed workout events after successful writes

### Phase 3

- add pagination and richer filtering
- add exercise catalog normalization if needed
- harden broker-backed delivery with retries, dead-letter handling, and operational monitoring
