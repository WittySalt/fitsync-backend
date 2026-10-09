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
- `GET /api/v1/analytics/reports/weekly?weekStart=YYYY-MM-DD`

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

The meal events on `nutrition-events` are read by one listener in one consumer group, `<KAFKA_ANALYTICS_SERVICE_GROUP>-nutrition`, which replaces the former `-meal-logged`, `-meal-updated` and `-meal-deleted` groups so a delete can no longer be applied before the log it removes. The `KAFKA_MEAL_*_LISTENER_ENABLED` flags keep their names: the listener runs when any of them is true and handles a type only when its own flag is true, and a delete that finds no meal leaves a marker in `analytics_processed_events` so a later log of that meal is ignored.

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
- weekly report: 10 minutes

Default local/test mode:

- `spring.cache.type=simple`
- no Redis dependency at runtime

Redis-backed mode (the default of `docker-compose.prod.yml`, so the cloud stack uses the `redis`
container once its rendered `.env` no longer forces `simple`):

```text
SPRING_CACHE_TYPE=redis
SPRING_DATA_REDIS_HOST=localhost        # redis in the compose stack
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=
SPRING_DATA_REDIS_TIMEOUT=500ms         # command timeout (Lettuce's default is 60 s)
SPRING_DATA_REDIS_CONNECT_TIMEOUT=500ms
```

- Keys are Spring's default `<cacheName>::<key>`, and every key starts with the user id, for
  example `analyticsDashboard::<userId>:<today>` or `analyticsStreaks::<userId>`. Redis holds nothing else in this stack, so the cache names
  (all `analytics*`) are the namespace.
- Values are JSON with their type (`GenericJackson2JsonRedisSerializer`), so a cached record comes
  back as its own class. The type validator allows only this service's types and JDK value types
  (collections and maps, `java.time`, numbers, strings, UUIDs); any other type named in a Redis value
  is refused rather than instantiated. `CacheValueSerializerRoundTripTests` round-trips the return type
  of every `@Cacheable` method through this serializer, with `List.of`/`Set.of`/`Map.of`, nested
  records, enums and `java.time` values filled in, both full and empty.
- In the compose stack Redis is a pure cache: `maxmemory 160mb` inside a 256m container,
  `allkeys-lru` eviction, and no persistence, so a restart starts empty.

When Redis is down or slow:

- `CacheConfig` registers a `LoggingCacheErrorHandler`: a failed cache read, write or evict is logged
  at WARN and the call goes to the read model as on a miss (`CacheConfigErrorHandlingTests`). A
  request costs at most the 500 ms timeout extra, it does not fail.
- `management.health.redis.enabled=false`: Redis does not count towards `/actuator/health`, so an
  outage neither restarts the container nor rolls back a deploy.
- A failed per-user eviction is logged and the stale entry lives until its TTL (at most 10 minutes).

Invalidation model:

- a successful projection change evicts only that user's entries, in every analytics cache:
  `AnalyticsCacheInvalidationService` finds them with `SCAN MATCH <cacheName>::<userId>*` and
  removes them with `UNLINK` (never `KEYS`); other users' entries stay (tested in
  `AnalyticsCacheInvalidationServiceTests`)
- with the in-process cache the same eviction filters the map by key prefix

Checking it on a cloud host (ssh over SSM, see `infra/terraform/RUNBOOK.md`):

```bash
sudo docker exec fitsync-redis redis-cli INFO keyspace                       # db0:keys=...
sudo docker exec fitsync-redis redis-cli --scan --pattern 'analytics*' | head
sudo docker exec fitsync-redis redis-cli TTL 'analyticsStreaks::<userId>'    # <= 300 (seconds)
sudo docker exec fitsync-redis redis-cli INFO stats | grep -E 'keyspace_(hits|misses)|evicted_keys'
```

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
