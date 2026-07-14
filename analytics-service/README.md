# Analytics Service

`analytics-service` owns derived, query-oriented fitness analytics for FitSync. It does not own workouts, meals, goals, or body metrics as source-of-truth writes. It consumes domain events and maintains read-optimized projections for dashboard and summary queries.

## API Surface

External user-scoped endpoints:

- `GET /api/v1/analytics/dashboard`
- `GET /api/v1/analytics/body/trends?from=YYYY-MM-DD&to=YYYY-MM-DD&bucket=DAY|WEEK|MONTH`
- `GET /api/v1/analytics/workouts/summary?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `GET /api/v1/analytics/nutrition/summary?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `GET /api/v1/analytics/goals/progress?status=ACTIVE`
- `GET /api/v1/analytics/streaks`
- `GET /api/v1/analytics/achievements`

Internal admin-only maintenance endpoints:

- `POST /api/v1/internal/analytics/users/{userId}/dashboard/rebuild`
- `POST /api/v1/internal/analytics/users/{userId}/nutrition/rebuild?date=YYYY-MM-DD`
- `POST /api/v1/internal/analytics/users/{userId}/workouts/rebuild?date=YYYY-MM-DD`

Security rules:

- external endpoints require JWT authentication
- authenticated `userId` is always derived from the token
- internal rebuild endpoints require `ROLE_ADMIN`

## Projection Inputs

Phase 1 projections are updated from these events:

- `GoalUpdated`
- `BodyMetricsRecorded`
- `MealLogged`
- `MealUpdated`
- `MealDeleted`
- `WorkoutLogged`
- `WorkoutUpdated`
- `WorkoutDeleted`
- `PersonalRecordAchieved`

Kafka topics:

- `user-events`
- `progress-events`
- `nutrition-events`
- `workout-events`

Projection tables:

- `analytics_user_dashboard_snapshot`
- `analytics_user_daily_body`
- `analytics_user_daily_nutrition`
- `analytics_user_daily_workout`
- `analytics_user_goal_progress`
- `analytics_meal_contributions`
- `analytics_workout_contributions`
- `analytics_user_streak_rollup`
- `analytics_user_workout_records`
- `analytics_processed_events`

## Cache Strategy

The service now uses Spring Cache for read-heavy analytics endpoints.

Cached endpoints:

- dashboard: 5 minutes
- goal progress: 5 minutes
- body trends: 10 minutes
- workout summary: 10 minutes
- nutrition summary: 10 minutes
- streaks: 5 minutes
- achievements: 5 minutes

Default local/test mode:

- `spring.cache.type=simple`
- no Redis dependency at runtime

Redis-backed mode:

```text
SPRING_CACHE_TYPE=redis
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=
```

Invalidation model:

- any successful projection mutation or internal rebuild clears analytics caches
- phase 1 uses coarse-grained invalidation to avoid stale cross-endpoint aggregates
- this is intentionally conservative until projection dependency tracking becomes more granular

Why Redis here:

- analytics reads are more repetitive than write-heavy domain services
- dashboard and summary payloads are deterministic and safe to cache
- Redis gives cross-instance cache sharing once the service scales horizontally

Why Redis is optional:

- local development and tests should not require extra infrastructure
- the service still behaves correctly with in-memory caching or with caching disabled by configuration

## OpenAPI

Swagger UI is exposed at:

- `GET /swagger-ui.html`

The controller layer now documents:

- user analytics endpoints
- admin rebuild endpoints
- security expectations
- date and bucket parameter semantics

## Run Notes

Typical local startup:

```powershell
mvn -pl analytics-service spring-boot:run
```

Enable Kafka-backed consumers explicitly when needed:

```powershell
mvn -pl analytics-service spring-boot:run "-Dspring-boot.run.profiles=local,kafka"
```

Enable Redis-backed caching:

```powershell
$env:SPRING_CACHE_TYPE="redis"
$env:SPRING_DATA_REDIS_HOST="localhost"
$env:SPRING_DATA_REDIS_PORT="6379"
mvn -pl analytics-service spring-boot:run
```

## Residual Risks

- cache invalidation is user-targeted for current analytics caches; any future cross-user cache must define its own invalidation rule
- body metrics still do not have a `BodyMetricDeleted` contract, so historical chart correction remains partial
- reads remain eventually consistent with upstream systems by design
