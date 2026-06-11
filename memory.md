# Memory

## Build and Environment Notes

- Before running build or test commands, check this file for environment-specific constraints and known workarounds.
- On 2026-06-12, the active local Cognito SPA setup for FitSync was confirmed as:
  - Region `ap-southeast-2`
  - User pool ID `ap-southeast-2_8lgadn`
  - SPA app client name `FitSync`
  - SPA app client ID `4mvse0cgmkplgocfg827uolvad`
  - Managed login domain `https://ap-southeast-2kjyn2vzun.auth.ap-southeast-2.amazoncognito.com`
  - Callback URL `http://localhost:3000/auth/callback`
  - Sign-out URL `http://localhost:3000/logout/callback`
  - OAuth grant type `Authorization code grant`
  - OIDC scopes `openid`, `email`, `profile`
- For local auth-service Cognito runs, use:

```powershell
COGNITO_ENABLED=true
COGNITO_ISSUER_URI=https://cognito-idp.ap-southeast-2.amazonaws.com/ap-southeast-2_8lgadn
COGNITO_AUDIENCE=4mvse0cgmkplgocfg827uolvad
COGNITO_PRINCIPAL_CLAIM=username
```

- Do not reuse the earlier Cognito app client with a generated secret for browser-based SPA login. The current agreed integration path is the public SPA app client above.
- Do not mix the earlier temporary Cognito user pool/client values with the current `8lgadn` / `4mvse0cgmkplgocfg827uolvad` pair when updating frontend or backend configuration.
- This Codex environment cannot currently open outbound TCP connections to `repo.maven.apache.org:443`.
- The default Maven user for tool execution is `CodexSandboxOffline`, so Maven normally uses `C:\Users\CodexSandboxOffline\.m2\repository`.
- The usable pre-populated local Maven cache in this workspace is under `C:\Users\WRX\.m2\repository`.
- For stable offline builds, prefer `C:\Users\CodexSandboxOffline\.m2\repository` after syncing dependencies into it. Using `WRX`'s repository directly can trigger Windows `AccessDeniedException` on some test-scope JARs during `testCompile`.
- After introducing `spring-kafka`, the most reliable local repository for this workspace is now `E:\Code\NUS-ISS\Capstone\backend\.m2\repository`, because the missing Kafka artifacts were downloaded there successfully.
- For offline Maven runs in this environment, use:

```powershell
mvn -o "-Dmaven.repo.local=C:/Users/CodexSandboxOffline/.m2/repository" ...
```

- For this backend specifically, prefer:

```powershell
mvn -o "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" ...
```

- Do not assume `-Dmaven.repo.local=C:\...` will be honored correctly from PowerShell; prefer forward slashes.
- If Maven reports that `spring-boot-starter-parent` cannot be resolved, first verify the command is using the explicit repo path above.
- Git may report dubious ownership for `E:/Code/NUS-ISS/Capstone/backend` because the repo is owned by `WRX` while commands run as `CodexSandboxOffline`. Prefer per-command safe directory reads, for example `git -c safe.directory=E:/Code/NUS-ISS/Capstone/backend -C backend status --short`, instead of modifying global git config.

## Test Profile Notes

- `user-service` context tests must activate the `test` profile, otherwise Spring Boot will try to connect to `localhost:5432`.
- `auth-service` test setup relies on `src/test/resources/application.yml` for H2 and does not currently require a separate active profile annotation.
- `MockMvc` integration tests here are not automatically rolled back across requests; if assertions depend on exact outbox counts or statuses, clear the affected repositories in `@BeforeEach`.
- When removing or renaming Spring beans, prefer `mvn clean test` instead of `mvn test` so stale compiled classes under `target/` do not keep old bean definitions alive.
- When removing or renaming Flyway migrations, definitely use `mvn clean test`; stale deleted SQL files can remain under `target/classes/db/migration` and still be executed by Flyway.

## Common Persistence Notes

- On 2026-06-12, the old `common-*` module names were replaced with responsibility-based names: `event-contracts`, `persistence-support`, and `web-support`. Java package names stayed stable as `com.fitsync.contracts`, `com.fitsync.persistence`, and `com.fitsync.web`.
- `persistence-support` is the shared internal module for persistence implementation primitives. It intentionally contains JPA/Spring Data concepts and should be used by service write models that need consistent audit, effective dating, code catalog, or outbox infrastructure.
- Keep `event-contracts` and `persistence-support` separate. `event-contracts` is for shared event DTO contracts and should not depend on JPA; `persistence-support` may depend on Spring Data JPA and should not define cross-service event payloads.
- Use `BaseAuditableEntity` for ordinary mutable business rows that need `date_time_created`, `date_time_updated`, `created_by`, `updated_by`, and optimistic `version`.
- Use `BaseEffectiveEntity` only when a row has business validity windows, such as code catalog records. Do not add effective dates to every entity by default.
- Use the code catalog primitives when Java enum values must stay compile-time controlled but UI labels/descriptions/order/effective dates need DB configurability. Java enum code remains the stable stored value; DB rows enrich that code.
- Use `BaseOutboxEventEntity` for outbox rows, not `BaseAuditableEntity`. Event time semantics are different from ordinary row audit semantics: produced time, published time, and row-created time must remain separate.
- Do not put service-specific fields, repositories, controllers, DTOs, security logic, or business rules in `persistence-support`. The module should stay small and reusable; service-specific concrete entities can extend the base classes in their own service package.
- Adding a new base class to `persistence-support` should require at least two current or clearly imminent service consumers. Avoid speculative abstractions that only one service uses.

## Common Web Notes

- `web-support` is the shared internal module for HTTP/API-facing web primitives. It owns the common error response contract, expected API exception hierarchy, global API exception handler, REST authentication entry point, and REST access-denied handler.
- Keep `web-support` separate from `event-contracts` and `persistence-support`. `event-contracts` is for transport/event/API DTO contracts; `persistence-support` is for JPA primitives; `web-support` may depend on Spring MVC, validation, and security.
- Services should explicitly import `CommonWebErrorConfiguration`; do not rely on broad component scanning across service package boundaries.
- The shared `ApiError` response shape is `timestamp`, `status`, `error`, `code`, `message`, `path`, `details`, and `correlationId`. Do not leak stack traces or internal exception details to clients.
- Use `ApiException` subclasses for expected API failures such as bad request, not found, conflict, unauthorized, and forbidden. Raw `IllegalArgumentException` is still translated to `400 BAD_REQUEST`, but business paths should prefer explicit typed exceptions over time.
- `RestAuthenticationEntryPoint` returns a JSON `401 UNAUTHORIZED` response and `RestAccessDeniedHandler` returns a JSON `403 FORBIDDEN` response. Protected APIs should wire both so auth failures do not fall back to framework-default HTML or empty responses.
- Do not add static service-level `HealthController` classes for internal services. Use Spring Boot Actuator `/actuator/health` for service health checks; keep `/api/v1/health` only at `api-gateway` as the edge health endpoint.
- Add OpenAPI `@Schema(example = ...)` samples to write-oriented request DTOs when adding API endpoints. For user-service code-stable enums, samples must use numeric stable codes such as `100001`, not Java enum names, unless the field is still a plain enum.

## Nutrition Service Notes

- `nutrition-service` owns meal logs, meal item nutrition snapshots, meal item nutrient detail snapshots, custom foods, nutrition-local goal/preference projections, and nutrition-facing food master data.
- `nutrition-service` write-model alignment with `persistence-support` is now in place: ordinary mutable rows use `BaseAuditableEntity`, food catalog/effective-dated rows use `BaseEffectiveEntity`, and outbox rows use `BaseOutboxEventEntity`.
- Food master data is currently modeled with `food_brands`, `food_catalog_items`, `food_servings`, `food_barcodes`, `food_nutrient_profiles`, and `food_nutrient_details`.
- `nutrition-service` now exposes food catalog read APIs at `GET /api/v1/nutrition/foods/search`, `GET /api/v1/nutrition/foods/{foodId}`, `GET /api/v1/nutrition/foods/{foodId}/servings`, and `GET /api/v1/nutrition/barcodes/{barcode}`.
- Meal create/update payloads can now carry fine-grained `nutrientDetails[]` per item, and those details are snapshotted into `meal_item_nutrient_details` so historical meal truth is independent from later catalog edits.
- Keep food ownership simple for now: `FoodCatalogItem.scope` remains `GLOBAL` or `USER`; if user-cloned template behavior is added, prefer optional lineage fields such as `baseFoodId` or `creationMode` on `USER` foods instead of introducing a third primary scope.
- `nutrition-service` now implements that lineage approach: `food_catalog_items` has `base_food_id` and `creation_mode`, and `POST /api/v1/nutrition/foods/{foodId}/clone` clones a `GLOBAL` food into a user-owned `USER` food while copying servings, nutrient profile, and nutrient details.
- Meal write flows now support template-derived snapshots: when a meal item supplies `foodCatalogItemId` plus `foodServingId` and `quantity`, the service derives calories/macros/nutrient details from the referenced food master data instead of requiring the client to send all computed nutrition values.
- After the `web-support` migration, `nutrition-service` imports `CommonWebErrorConfiguration`, removed its local `ApiError`, `GlobalExceptionHandler`, `RestAuthenticationEntryPoint`, and `ResourceNotFoundException`, and now uses `web-support` for shared JSON error, 401, and 403 handling.
- `nutrition-service` `SecurityConfig` must wire both `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler` from `web-support`; `/api/v1/internal/**` remains `ROLE_ADMIN` only and now returns the shared `FORBIDDEN` API error for authenticated non-admin callers.
- After the food-catalog expansion plus `web-support` migration, the focused offline reactor passed with 14 tests:
- After adding food clone lineage plus template-derived meal snapshots, the focused offline reactor passed with 16 tests:

```powershell
mvn -o "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl nutrition-service -am clean test
```

## User Service Notes

- `user-service` owns profile, preferences, body metrics, goals, and its own outbox table. Auth-owned facts such as password hash, role source of truth, and token issuance stay in `auth-service`.
- The user write model is intentionally split into `user_profiles`, `user_app_preferences`, `user_training_preferences`, `user_nutrition_preferences`, `user_notification_preferences`, `body_metric_entries`, and `user_goals`; do not collapse these into a single users table.
- `persistence-support` now owns shared JPA primitives: `BaseAuditableEntity`, `BaseEffectiveEntity`, code catalog bases, `CodeEnum`, a generic code enum converter, and `BaseOutboxEventEntity`.
- Preferences are split by product concern: app settings, training preferences, nutrition preferences, and notification preferences. Because no migration had been run yet, `V1__create_user_service_tables.sql` was rewritten to create the final split schema directly; there is no legacy `user_preferences` table and `V2__split_user_preferences.sql` was removed.
- Preference enums are code-stable numeric strings, for example `100001` for metric units. API responses serialize enum `code`; requests accept numeric code, Java enum name, and temporary legacy aliases; DB columns store numeric code through JPA converters. Do not use `@Enumerated(EnumType.STRING)` for new preference code enums.
- User preference code metadata is stored in `code_sets` and `code_values`; Java enum remains the compile-time allowed value set, while DB rows own UI display name, description, sort order, active flag, and effective date range.
- Outbox event timestamps are deliberately separate from ordinary audit timestamps: `date_time_produced` is the domain event occurrence time, `date_time_published` is the broker publish time, and `date_time_created` is the outbox row creation time.
- BMI and trends are derived at read time from source facts. Do not persist BMI, trend labels, scores, or weekly summaries as write-model truth unless a dedicated read model is introduced.
- `GET /api/v1/users/me` returns active goals only, matching the `activeGoals` response semantics.
- `GET /api/v1/users/me/body-metrics` supports optional `from`, `to`, and `limit` query params. It returns newest records first, defaults `limit` to `100`, and caps it at `500`.
- `GET /api/v1/users/me/body-metrics/trends` returns read-time trend summaries for `weightKg`, `bodyFatRate`, `waistCm`, and `bmi`. Empty metric trends return an object with null `first`, `latest`, and `change`.
- `GET /api/v1/users/me/body-metrics/{metricId}` reads one owned body metric. `PUT /api/v1/users/me/body-metrics/{metricId}` fully replaces one owned metric using `CreateBodyMetricRequest` validation. `DELETE /api/v1/users/me/body-metrics/{metricId}` deletes one owned metric and returns `204`.
- Body metric single-record operations must query by both `metricId` and JWT `userId`; missing records and records owned by another user intentionally both return `404`.
- Body metric replacement reuses the `BodyMetricsRecorded` event with corrected facts. Delete currently emits no event; define a `BodyMetricDeleted` contract before adding downstream delete propagation.
- `GET /api/v1/users/me/goals` supports optional `status`, for example `?status=ACTIVE`.
- These query/trend additions did not require a DB migration or new dependency.
- `application-prod.yml` requires `JWT_SECRET` instead of inheriting the local `change-me` default.
- After the persistence-support/code catalog refactor, `user-service` has 18 normal tests and `mvn -o "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl user-service -am clean test` passes.
- After the web-support migration, `user-service` imports `CommonWebErrorConfiguration`, removed its local `ApiError`, `GlobalExceptionHandler`, `RestAuthenticationEntryPoint`, and `ResourceNotFoundException`, and now uses web-support for JSON 401/403/error handling.
- `user-service` `SecurityConfig` must wire both `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler` from `web-support`; `/api/v1/internal/**` remains `ROLE_ADMIN` only and now returns the shared `FORBIDDEN` API error for non-admin authenticated users.
- After the web-support migration, `user-service` has 19 normal tests and passed in the root clean build segment with:

```powershell
mvn -o "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" clean test
```

- The focused user-service/web-support offline reactor also passes with:

```powershell
mvn -o "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl user-service -am test
```

- On 2026-06-12, static `/api/v1/health` controllers were removed from `auth-service`, `user-service`, `workout-service`, and `nutrition-service`; their security configs now expose only `/actuator/health` anonymously for health checks.
- On 2026-06-12, the earlier root clean build blocker in `workout-service` caused by DTO constructors missing newer set-list fields was fixed during the workout set-level expansion.
- On 2026-06-12 after health endpoint cleanup, the full root offline unit-test chain passed:

```powershell
mvn -o "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" test
```

- On 2026-06-12, OpenAPI sample JSON was added to write-oriented request DTOs across auth/user/workout/nutrition. Root offline `mvn test` still passed afterward.
- On 2026-06-12, the root offline unit-test chain also passed after adding `persistence-support`, rewriting user-service V1, deleting V2, and switching preference API responses to numeric codes:

```powershell
mvn -o "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" test
```

## Workout Service Notes

- `workout-service` now uses an Exercise-first model. `Exercise` is the normalized catalog/source entity; plan exercises and workout exercise logs reference `exercise_id` and store `exercise_name_snapshot` for historical display stability.
- `WorkoutPlanEntity`, `WorkoutSessionEntity`, and `ExerciseEntity` extend `BaseAuditableEntity`; do not add service-local `created_at` or `updated_at` columns to ordinary workout business rows.
- `OutboxEventEntity` extends `BaseOutboxEventEntity`; workout outbox columns use `event_version`, `date_time_produced`, `date_time_published`, and `date_time_created`.
- Workout plan/session exercise API inputs use `exerciseId`, not free-form `exerciseCode` plus `displayName`.
- `POST /api/v1/workouts/exercises` creates custom user-owned exercises; `GET /api/v1/workouts/exercises` lists active global exercises plus active custom exercises owned by the current user.
- Workout list endpoints now return `PageResponse<T>` with zero-based `page`, default `size=20`, and max `size=100`. Exercise listing supports `q`, `exerciseType`, `primaryMuscleGroup`, `equipment`, and `difficulty`; plan listing supports optional `status`; session listing supports `from`, `to`, `status`, and `planId`.
- Set-level detail is modeled independently under existing exercise rows: `WorkoutPlanExerciseSetTarget` stores per-set plan targets, and `WorkoutExerciseSetLog` stores per-set performed facts. Keep aggregate-level summary fields because they are convenient for list views and event payload summaries.
- Avoid fetching two nested `List` collections in one Hibernate entity graph; `workout-service` fetches plan/session exercises plus their Exercise, then reads setTargets/sets lazily inside the service transaction to avoid `MultipleBagFetchException`.
- Avoid collection `EntityGraph` on paged or `findTop` list queries in `workout-service`; Hibernate otherwise applies pagination in memory. Keep collection graphs for single-record detail fetches only.
- On 2026-06-12, after adding Exercise-first persistence and persistence-support alignment, the workout normal test chain passed:

```powershell
mvn "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl workout-service -am test
```

- On 2026-06-12, after independently adding set-level plan targets and set-level exercise logs, the workout normal test chain passed with 16 tests:

```powershell
mvn "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl workout-service -am test
```

- After the web-support migration, `workout-service` imports `CommonWebErrorConfiguration`, depends on `web-support`, removed its local `ApiError`, `GlobalExceptionHandler`, `ResourceNotFoundException`, and `RestAuthenticationEntryPoint`, and now uses web-support for JSON 401/403/error handling.
- `workout-service` `SecurityConfig` must wire both `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler` from `web-support`; `/api/v1/internal/**` remains `ROLE_ADMIN` only and returns the shared `FORBIDDEN` API error for non-admin authenticated users.
- `web-support` owns shared HTTP response DTOs. `PageResponse<T>` lives in `com.fitsync.web.response`; service-local page response DTOs should not be reintroduced.
- Keep `PageResponse<T>` independent from Spring Data `Page<T>` so `web-support` does not need a Spring Data dependency. Services that use Spring Data should convert their `Page<T>` to `PageResponse<T>` at the service boundary.
- On 2026-06-12, after migrating workout error handling to `web-support`, the focused offline reactor passed with 16 workout tests:

```powershell
mvn "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl workout-service -am test
```

- On 2026-06-12, after adding paginated and filtered workout list APIs, the focused offline reactor passed with 19 workout tests:

```powershell
mvn "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl workout-service -am test
```

- On 2026-06-12, after moving `PageResponse<T>` from `workout-service` to `web-support`, the same focused offline reactor still passed with 19 workout tests:

```powershell
mvn "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl workout-service -am test
```

- On 2026-06-12, after renaming shared modules to `event-contracts`, `persistence-support`, and `web-support`, the full backend reactor passed:

```powershell
mvn "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" test
```

## Messaging Notes

- `auth-service` and `user-service` now expose internal relay endpoints at `/api/v1/internal/outbox/publish`; they require `ROLE_ADMIN`.
- `user-service` now consumes `UserRegistered` through a real Kafka listener class; the old HTTP shim ingress has been removed.
- `fitsync.outbox.relay.enabled` is intentionally `false` by default, but the `kafka` Spring profile enables the real Kafka producer and relay for local broker-backed runs.
- `fitsync.kafka.enabled=false` keeps the fallback producer active by default. Turn it on only when a reachable broker exists and `KAFKA_BOOTSTRAP_SERVERS` is set correctly.
- `fitsync.kafka.listeners.user-registered.enabled=false` keeps the Kafka consumer from auto-starting in local/test environments without a broker.
- `docker-compose.yml` includes a local Kafka broker on `localhost:9092`.
- Use the `kafka` Spring profile to enable real Kafka publishing and `UserRegistered` consumption for local service runs.
- Broker-backed integration tests are opt-in with `KAFKA_INTEGRATION_TESTS=true`; otherwise `*IT` classes are skipped or excluded from normal `mvn test`.
- The parent POM now registers Surefire and Failsafe as inherited build plugins. `mvn test` excludes `*IT`, while `mvn verify` runs Failsafe-managed `*IT` classes.
- On 2026-06-11, Docker Desktop was initially unavailable. After starting Docker Desktop, Docker commands still required elevated execution because the Windows Docker engine pipe is outside the sandbox.
- On 2026-06-11, `bitnami/kafka:3.7` was not pullable (`not found`), so the local compose broker was switched to `apache/kafka:3.7.0`.
- After Docker Desktop started, `docker compose up -d kafka` started `fitsync-kafka` successfully and `docker compose ps kafka` reported it healthy on `localhost:9092`.
- The broker-backed `UserRegisteredKafkaIT` passed with:

```powershell
$env:KAFKA_INTEGRATION_TESTS='true'; mvn -o "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl user-service -am "-Dtest=UserRegisteredKafkaIT" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- PowerShell treats unquoted Maven `-D` values containing dots poorly in some cases; quote properties such as `"-Dtest=UserRegisteredKafkaIT"` and `"-Dsurefire.failIfNoSpecifiedTests=false"`.
- On 2026-06-11, the workspace Maven repo was missing `maven-failsafe-plugin:3.2.5`; one online Maven run downloaded it into `E:/Code/NUS-ISS/Capstone/backend/.m2/repository`.
- After that, the broker-backed verification passed fully offline with:

```powershell
$env:KAFKA_INTEGRATION_TESTS='true'; mvn -o "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl user-service -am verify
```

- The default root offline unit-test chain also passed without running broker-backed ITs:

```powershell
mvn -o "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" test
```
