# User Service Design

## 1. Responsibility Boundary

`user-service` should own user-facing profile and progress data that is not part of authentication.

Recommended ownership split:

- `auth-service`
  - email
  - password hash
  - role
  - account status
  - token issuance
- `user-service`
  - profile
  - preferences
  - body metrics
  - user goals

Important rule:

- `user-service` should store source facts
- derived metrics such as BMI, trends, readiness scores, and weekly summaries should be calculated from source facts
- derived results should live in read models or analytics-facing responses, not as core write-model truth

---

## 2. Domain Slices

`user-service` should not be one large `users` table.

It should be split into four write-model slices:

### 2.1 Profile

Low-frequency identity-facing profile data.

Fields:

- `userId`
- `displayName`
- `firstName`
- `lastName`
- `avatarUrl`
- `birthDate`
- `gender`
- `heightCm`
- `activityLevel`
- `timezone`
- `bio`
- `onboardingCompleted`

### 2.2 Preferences

Configuration-like user choices. This should not be a single wide table; preferences are split by product concern.

Slices:

- `AppPreferences`: units, language, timezone, privacy, data sharing consent
- `TrainingPreferences`: fitness level, training location, weekly frequency, duration, workout time, intensity
- `NutritionPreferences`: dietary pattern, meal frequency, calorie tracking mode, macro preference, water goal
- `NotificationPreferences`: channels, reminder toggles, marketing opt-in, quiet hours

Code rule:

- preference enums are code-stable
- database stores enum `code`, not Java enum name
- enum values use numeric string codes such as `100001`
- Java enum remains the compile-time allowed value set
- `code_sets` and `code_values` store display name, description, sort order, active flag, and effective dates
- API accepts numeric code, Java enum name, and temporary legacy aliases, but responses return numeric code

### 2.3 Body Metrics

Time-series measurements.

Facts to store:

- `weightKg`
- `bodyFatRate`
- `waistCm`
- `hipCm`
- `chestCm`
- `armCm`
- `thighCm`
- `calfCm`
- `measuredAt`
- `source`
- `note`

Derived values:

- `bmi`
- change trend
- body score

Rule:

- derived values may be returned by APIs
- but they should be computed from stored facts, not be the only stored representation

### 2.4 Goals

Desired target state.

Examples:

- target weight
- calorie target
- protein target
- workout frequency target
- step target

Rule:

- goals are not measurements
- they should be modeled separately from body metrics

---

## 3. What Should Not Be Core User Write Models

The following should not be first-class write-model truth in `user-service`:

- BMI as the only stored body indicator
- weight trend labels
- weekly calorie gap
- fitness score
- muscle score
- running ability score
- body posture score

Reason:

- these are derived
- formulas and product logic will change
- consumers should tolerate recalculation

Recommended placement:

- `analytics-service`
- dedicated read models
- dashboard aggregation APIs

---

## 4. Recommended Aggregates

### 4.1 UserProfile

```text
UserProfile
- userId: UUID
- displayName: String
- firstName: String?
- lastName: String?
- avatarUrl: String?
- birthDate: LocalDate?
- gender: Gender?
- heightCm: BigDecimal?
- activityLevel: ActivityLevel?
- timezone: String?
- bio: String?
- onboardingCompleted: boolean
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.2 UserPreferences

```text
UserAppPreferences
- userId: UUID
- preferredUnits: UnitSystem
- language: LanguagePreference
- timezone: String?
- privacyLevel: PrivacyLevel
- dataSharingConsent: boolean
- dateTimeCreated: Instant
- dateTimeUpdated: Instant

UserTrainingPreferences
- userId: UUID
- fitnessLevel: FitnessLevel
- trainingLocation: TrainingLocation
- weeklyWorkoutDays: Integer?
- preferredDurationMinutes: Integer?
- preferredWorkoutTime: LocalTime?
- intensityPreference: IntensityPreference
- dateTimeCreated: Instant
- dateTimeUpdated: Instant

UserNutritionPreferences
- userId: UUID
- dietaryPattern: DietaryPreference
- mealFrequency: Integer?
- calorieTrackingMode: CalorieTrackingMode
- macroPreference: MacroPreference
- waterGoalMl: Integer?
- dateTimeCreated: Instant
- dateTimeUpdated: Instant

UserNotificationPreferences
- userId: UUID
- pushEnabled: boolean
- emailEnabled: boolean
- workoutReminderEnabled: boolean
- mealReminderEnabled: boolean
- weighInReminderEnabled: boolean
- goalProgressReminderEnabled: boolean
- marketingEmailsEnabled: boolean
- quietHoursStart: LocalTime?
- quietHoursEnd: LocalTime?
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.3 BodyMetricEntry

```text
BodyMetricEntry
- metricId: UUID
- userId: UUID
- weightKg: BigDecimal?
- bodyFatRate: BigDecimal?
- waistCm: BigDecimal?
- hipCm: BigDecimal?
- chestCm: BigDecimal?
- armCm: BigDecimal?
- thighCm: BigDecimal?
- calfCm: BigDecimal?
- measuredAt: Instant
- source: MetricSource
- note: String?
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.4 UserGoal

```text
UserGoal
- goalId: UUID
- userId: UUID
- goalType: GoalType
- targetValue: BigDecimal?
- targetUnit: String?
- targetDate: LocalDate?
- status: GoalStatus
- notes: String?
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

---

## 5. Enums

Preference-related enums are code-stable:

- API responses serialize numeric enum `code`
- API requests accept numeric code, Java enum name, and temporary legacy aliases
- DB columns store code through JPA converters
- Java enum keeps fallback display and description only
- `code_values.display_name` and `code_values.description` are the UI-managed labels
- `code_values.date_effective_from` and `code_values.date_effective_to` control business validity

Current user preference code ranges:

```text
100xxx app measurement units
110xxx app language
120xxx privacy
200xxx fitness level
210xxx training location
220xxx intensity
300xxx dietary preference
310xxx calorie tracking
320xxx macro preference
```

```text
Gender
- MALE
- FEMALE
- NON_BINARY
- PREFER_NOT_TO_SAY

ActivityLevel
- SEDENTARY
- LIGHTLY_ACTIVE
- MODERATELY_ACTIVE
- VERY_ACTIVE
- ATHLETE

UnitSystem
- METRIC
- IMPERIAL

DietaryPreference
- NONE
- HALAL
- VEGETARIAN
- VEGAN
- KETO
- LOW_CARB

LanguagePreference
- EN
- ZH_CN

PrivacyLevel
- PRIVATE
- FRIENDS
- PUBLIC

FitnessLevel
- BEGINNER
- INTERMEDIATE
- ADVANCED

TrainingLocation
- HOME
- GYM
- OUTDOOR
- MIXED

IntensityPreference
- LOW
- MODERATE
- HIGH

CalorieTrackingMode
- OFF
- FLEXIBLE
- STRICT

MacroPreference
- BALANCED
- HIGH_PROTEIN
- LOW_CARB

MetricSource
- MANUAL
- DEVICE
- IMPORTED

GoalType
- WEIGHT
- CALORIES
- PROTEIN
- WORKOUT_FREQUENCY
- STEPS

GoalStatus
- ACTIVE
- PAUSED
- COMPLETED
- CANCELLED
```

---

## 6. API Surface

Base path:

- `/api/v1/users`

Authenticated requests must derive `userId` from JWT.

### 6.1 Current User

#### GET `/api/v1/users/me`

Purpose:

- return the current user-facing aggregate snapshot

Response shape:

```json
{
  "userId": "0f6a0f3d-6df0-4f32-bf63-c72d8e8e3d85",
  "email": "alice@example.com",
  "role": "USER",
  "profile": {
    "displayName": "Alice",
    "firstName": "Alice",
    "lastName": "Tan",
    "avatarUrl": "https://cdn.example.com/avatar/a1.png",
    "birthDate": "1998-08-14",
    "gender": "FEMALE",
    "heightCm": 165.0,
    "activityLevel": "MODERATELY_ACTIVE",
    "timezone": "Asia/Singapore",
    "bio": "Trying to stay consistent.",
    "onboardingCompleted": true
  },
  "preferences": {
    "app": {
      "preferredUnits": "100001",
      "language": "110001",
      "timezone": "Asia/Singapore",
      "privacyLevel": "120001",
      "dataSharingConsent": false
    },
    "training": {
      "fitnessLevel": "200001",
      "trainingLocation": "210004",
      "weeklyWorkoutDays": 4,
      "preferredDurationMinutes": 45,
      "preferredWorkoutTime": "19:00:00",
      "intensityPreference": "220002"
    },
    "nutrition": {
      "dietaryPattern": "300002",
      "mealFrequency": 4,
      "calorieTrackingMode": "310002",
      "macroPreference": "320001",
      "waterGoalMl": 2500
    },
    "notifications": {
      "pushEnabled": true,
      "emailEnabled": false,
      "workoutReminderEnabled": true,
      "mealReminderEnabled": false,
      "weighInReminderEnabled": false,
      "goalProgressReminderEnabled": true,
      "marketingEmailsEnabled": false,
      "quietHoursStart": "22:00:00",
      "quietHoursEnd": "07:00:00"
    }
  },
  "activeGoals": [],
  "latestBodyMetrics": {
    "weightKg": 72.4,
    "bodyFatRate": 21.5,
    "bmi": 26.59,
    "measuredAt": "2026-06-11T20:15:00Z"
  }
}
```

### 6.2 Profile

#### PUT `/api/v1/users/me/profile`

Purpose:

- create or replace editable profile fields

#### GET `/api/v1/users/me/profile`

Purpose:

- read profile-only data

### 6.3 Preferences

#### PUT `/api/v1/users/me/preferences`

Purpose:

- create or replace preference state

#### GET `/api/v1/users/me/preferences`

Purpose:

- read preference-only data

### 6.4 Body Metrics

#### POST `/api/v1/users/me/body-metrics`

Purpose:

- append a new body measurement

#### GET `/api/v1/users/me/body-metrics/latest`

Purpose:

- fetch the newest measurement plus derived BMI

#### GET `/api/v1/users/me/body-metrics/{metricId}`

Purpose:

- fetch one owned body metric record by id

Rules:

- lookup is scoped by JWT `userId`
- missing records and records owned by another user both return `404`

#### GET `/api/v1/users/me/body-metrics`

Purpose:

- fetch chronological measurement history

Query params:

- `from`: optional inclusive lower bound by `measuredAt`
- `to`: optional inclusive upper bound by `measuredAt`
- `limit`: optional maximum number of rows, default `100`, maximum `500`

Current behavior:

- returns newest entries first
- preserves the original list response shape for existing clients

#### GET `/api/v1/users/me/body-metrics/trends`

Purpose:

- return derived trend summaries from stored body metric facts

Query params:

- `from`: optional inclusive lower bound by `measuredAt`
- `to`: optional inclusive upper bound by `measuredAt`

Response shape:

```json
{
  "from": "2026-01-01T00:00:00Z",
  "to": "2026-02-01T00:00:00Z",
  "sampleCount": 4,
  "weightKg": {
    "first": { "value": 90.0, "measuredAt": "2026-01-01T08:00:00Z" },
    "latest": { "value": 85.0, "measuredAt": "2026-01-28T08:00:00Z" },
    "change": -5.0
  },
  "bodyFatRate": {
    "first": { "value": 22.0, "measuredAt": "2026-01-01T08:00:00Z" },
    "latest": { "value": 20.0, "measuredAt": "2026-01-28T08:00:00Z" },
    "change": -2.0
  },
  "waistCm": {
    "first": null,
    "latest": null,
    "change": null
  },
  "bmi": {
    "first": { "value": 27.78, "measuredAt": "2026-01-01T08:00:00Z" },
    "latest": { "value": 26.23, "measuredAt": "2026-01-28T08:00:00Z" },
    "change": -1.55
  }
}
```

Rule:

- trends are calculated at read time
- BMI trend depends on profile height and stored weight facts
- no trend values are stored as write-model truth

#### PUT `/api/v1/users/me/body-metrics/{metricId}`

Purpose:

- replace one owned body metric record when the user corrects a mistaken entry

Request:

- same shape and validation rules as `CreateBodyMetricRequest`

Rules:

- lookup is scoped by JWT `userId`
- at least one body measurement must be present
- replacement keeps the same `metricId`
- emits `BodyMetricsRecorded` again with the corrected facts
- missing records and records owned by another user both return `404`

#### DELETE `/api/v1/users/me/body-metrics/{metricId}`

Purpose:

- delete one owned body metric record when the user removes an incorrect entry

Rules:

- lookup is scoped by JWT `userId`
- successful delete returns `204`
- missing records and records owned by another user both return `404`
- no delete event is currently emitted; add a `BodyMetricDeleted` contract first if downstream services need delete propagation

### 6.5 Goals

#### PUT `/api/v1/users/me/goals/{goalType}`

Purpose:

- upsert a goal by type

#### GET `/api/v1/users/me/goals`

Purpose:

- fetch current goals

Query params:

- `status`: optional `GoalStatus` filter, for example `ACTIVE`

---

## 7. Request and Response Models

### 7.1 Requests

```text
UpsertUserProfileRequest
- displayName
- firstName
- lastName
- avatarUrl
- birthDate
- gender
- heightCm
- activityLevel
- timezone
- bio
- onboardingCompleted

UpsertUserPreferencesRequest
- app
- training
- nutrition
- notifications

UpsertAppPreferencesRequest
- preferredUnits
- language
- timezone
- privacyLevel
- dataSharingConsent

UpsertTrainingPreferencesRequest
- fitnessLevel
- trainingLocation
- weeklyWorkoutDays
- preferredDurationMinutes
- preferredWorkoutTime
- intensityPreference

UpsertNutritionPreferencesRequest
- dietaryPattern
- mealFrequency
- calorieTrackingMode
- macroPreference
- waterGoalMl

UpsertNotificationPreferencesRequest
- pushEnabled
- emailEnabled
- workoutReminderEnabled
- mealReminderEnabled
- weighInReminderEnabled
- goalProgressReminderEnabled
- marketingEmailsEnabled
- quietHoursStart
- quietHoursEnd

CreateBodyMetricRequest
- weightKg
- bodyFatRate
- waistCm
- hipCm
- chestCm
- armCm
- thighCm
- calfCm
- measuredAt
- source
- note

UpsertUserGoalRequest
- targetValue
- targetUnit
- targetDate
- status
- notes
```

### 7.2 Responses

```text
CurrentUserResponse
- userId
- email
- role
- profile
- preferences
- activeGoals
- latestBodyMetrics

UserProfileView
- displayName
- firstName
- lastName
- avatarUrl
- birthDate
- gender
- heightCm
- activityLevel
- timezone
- bio
- onboardingCompleted

UserPreferencesView
- app
- training
- nutrition
- notifications

AppPreferencesView
- preferredUnits
- language
- timezone
- privacyLevel
- dataSharingConsent

TrainingPreferencesView
- fitnessLevel
- trainingLocation
- weeklyWorkoutDays
- preferredDurationMinutes
- preferredWorkoutTime
- intensityPreference

NutritionPreferencesView
- dietaryPattern
- mealFrequency
- calorieTrackingMode
- macroPreference
- waterGoalMl

NotificationPreferencesView
- pushEnabled
- emailEnabled
- workoutReminderEnabled
- mealReminderEnabled
- weighInReminderEnabled
- goalProgressReminderEnabled
- marketingEmailsEnabled
- quietHoursStart
- quietHoursEnd

BodyMetricView
- metricId
- weightKg
- bodyFatRate
- waistCm
- hipCm
- chestCm
- armCm
- thighCm
- calfCm
- bmi
- measuredAt
- source
- note

BodyMetricTrendResponse
- from
- to
- sampleCount
- weightKg
- bodyFatRate
- waistCm
- bmi

BodyMetricTrend
- first
- latest
- change

BodyMetricTrendPoint
- value
- measuredAt

UserGoalView
- goalId
- goalType
- targetValue
- targetUnit
- targetDate
- status
- notes
```

---

## 8. Event Design

### 8.1 Incoming Event

#### UserRegistered

Producer:

- `auth-service`

Consumer:

- `user-service`

Purpose:

- initialize user profile and default preferences scaffold

Current implementation note:

- `user-service` now accepts this envelope through a Kafka listener on the `user-events` topic
- the transport-facing listener stays thin and delegates to `UserRegisteredEventConsumer`
- listener startup is configuration-gated so local environments without Kafka do not fail at boot
- local broker integration uses the `kafka` Spring profile and `KAFKA_BOOTSTRAP_SERVERS`

### 8.2 Outgoing Events

#### UserProfileUpdated

Topic:

- `user-events`

Purpose:

- notify downstream services that recommendation-relevant or onboarding-relevant profile facts changed

#### UserPreferencesUpdated

Topic:

- `user-events`

Purpose:

- notify downstream services that user preference inputs changed

#### BodyMetricsRecorded

Topic:

- `progress-events`

Purpose:

- notify analytics and recommendation services that a new body measurement exists

#### GoalUpdated

Topic:

- `progress-events`

Purpose:

- notify downstream services that target state changed

Event rules:

- publish only after successful write
- partition by `userId`
- keep payloads minimal
- do not publish secrets, tokens, or password-derived data

Current implementation note:

- write operations persist events into an outbox table first
- `date_time_produced` stores the domain event occurrence time
- `date_time_published` stores the successful broker publish time
- `date_time_created` stores the outbox row creation time
- relay is triggered through `POST /api/v1/internal/outbox/publish`
- relay scheduling support exists behind `fitsync.outbox.relay.*` properties
- real Kafka producer support now exists behind `fitsync.kafka.enabled=true`
- fallback logging producer remains the default when Kafka is disabled
- local compose provides Kafka on `localhost:9092`
- broker-backed verification is available through Failsafe-managed `UserRegisteredKafkaIT`

Delete caveat:

- `DELETE /me/body-metrics/{metricId}` does not currently publish a delete event
- this is intentional until `event-contracts` defines a `BodyMetricDeleted` payload and downstream services agree on delete semantics

---

## 9. Persistence Direction

Recommended future tables:

```text
user_profiles
user_app_preferences
user_training_preferences
user_nutrition_preferences
user_notification_preferences
body_metric_entries
user_goals
code_sets
code_values
outbox_events
```

Recommended rules:

- `UserProfile` and each preference slice are one-to-one by `userId`
- `BodyMetricEntry` is append-only
- `UserGoal` should allow at most one active goal per user and goal type

Current implementation direction:

- JPA entities for write models
- Flyway-managed schema
- `V1__create_user_service_tables.sql` now creates the final split user schema directly
- there is no legacy `user_preferences` write table in the current schema
- preference enum columns store stable numeric `code` values through JPA converters
- `code_sets` groups enum families and maps them to Java enum class names
- `code_values` binds numeric codes to enum names, display names, descriptions, sort order, active flag, and effective dates
- service interface plus repository interfaces
- shared persistence primitives live in `persistence-support`
- ordinary write models inherit `BaseAuditableEntity`
- code catalog entities inherit `BaseCodeSetEntity` / `BaseCodeValueEntity`
- outbox events inherit `BaseOutboxEventEntity`
- event publisher abstraction kept separate from persistence
- outbox relay kept behind a transport abstraction
- real Kafka transport is available behind the `kafka` profile while fallback logging remains safe for no-broker local runs

---

## 10. Rollout Order

### Phase 1

- implement profile
- implement preferences
- implement body metrics with BMI derived in responses
- implement goals
- define event contract types

### Phase 2

- consume `UserRegistered`
- move from in-memory store to database persistence
- add body metric history queries and filters
- add body metric trend read endpoint
- add body metric single-record read, correction, and delete APIs
- add goal status filter
- add internal transport-facing relay endpoint as a Kafka transition step

### Production Security Notes

- `application-prod.yml` requires `JWT_SECRET` to be injected by the deployment environment
- local and test profiles may keep safe development defaults, but production must not inherit the default `change-me` secret

### Phase 3

- add analytics-facing trend endpoints or read models
- keep trend and score logic out of the user write model
- harden broker-backed delivery with retries, dead-letter handling, and operational monitoring
