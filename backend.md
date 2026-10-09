# FitSync Backend Architecture

> **Status:** planning document written in June 2026, before most services were built. Its
> "current state" and phase sections are historical; for what exists today see
> [README.md](README.md) and the code.

## 1. Purpose

This document is the top-level backend architecture document for FitSync.

It defines:

- the current backend reality
- the recommended target architecture
- the domain and service boundaries
- the target root structure
- data ownership rules
- the phased evolution path

Detailed communication design is split into two companion documents:

- [backend-api-communication.md](backend-api-communication.md)
- [backend-kafka-event-architecture.md](backend-kafka-event-architecture.md)

---

## 2. Current Repository Reality

The current backend in this repository is:

- one Spring Boot application
- one Maven module
- one deployable artifact
- PostgreSQL-backed
- Redis-enabled
- currently centered around authentication and shared backend foundation

This means the repository is currently a **layered monolith**, not yet a true multi-service backend.

That is the current implementation state, not the final target state.

---

## 3. Recommended Target Direction

The recommended target direction is:

- small number of backend services
- clear domain ownership
- synchronous API calls for primary transactional workflows
- Kafka for post-commit async fan-out and derived processing
- minimal shared contracts
- no over-fragmented service map

In short:

- **current implementation**: modularizing monolith
- **target architecture**: small, pragmatic microservice set
- **communication model**: API-first for writes and reads, Kafka for async side effects

---

## 4. Architecture Principles

The backend should follow these principles:

- one domain should own one business responsibility
- controllers should stay thin
- business logic belongs in services
- validation must happen at the boundary
- security must be explicit
- shared code must remain small and generic
- cross-service and cross-domain dependencies should be minimized
- primary success paths should not depend on Kafka consumers
- eventual consistency is acceptable only for derived or non-blocking workflows

Practical meaning:

- register and login should stay synchronous
- workout and meal creation should stay synchronous
- analytics refresh can be asynchronous
- recommendation refresh can be asynchronous
- notification dispatch can be asynchronous

---

## 5. Recommended Service Set

The recommended backend service split is:

- `api-gateway`
- `auth-service`
- `user-service`
- `workout-service`
- `nutrition-service`
- `analytics-service`
- `recommendation-service`
- `notification-service`

Reason:

- this set is small enough to remain operable
- it preserves clear domain ownership
- analytics, recommendation, and notification naturally fit async event consumption
- it avoids splitting CRUD-heavy domains too far

---

## 6. Domain and Service Responsibilities

### 6.1 api-gateway

Responsibilities:

- single frontend entry point
- route requests to backend services
- apply CORS policy
- centralize request logging
- optionally perform edge JWT validation

Should not own domain business logic.

### 6.2 auth-service

Responsibilities:

- registration
- login
- password hashing
- token issuance
- multi-device sessions with refresh-token rotation and revocation

Own data:

- auth credentials
- auth-related metadata
- sessions (`auth.auth_sessions`)

#### Sessions and tokens

Every sign-in (the Cognito exchange at `POST /api/v1/auth/bootstrap`, and local register and login when they are
switched on) opens a session, which is one row in `auth.auth_sessions` per signed-in device. The response carries
a short-lived access token and an opaque refresh token.

The access token is the HS384 JWT the gateway and services already verify. Its lifetime is `JWT_EXPIRATION`, which
now defaults to `PT15M`, and it carries the claim `sid` with the session id.

The refresh token is 256 random bits encoded as base64url. auth-service stores only its SHA-256 hash, and it
compares hashes in constant time. Every call to `POST /api/v1/auth/refresh` replaces the refresh token with a new
one and moves the old hash into `previous_refresh_token_hash`. If a client presents that previous token again, two
parties hold the same token, so the whole session is revoked and the call answers 401 `SESSION_REVOKED`. The one
exception is a duplicate that arrives within `AUTH_REFRESH_REUSE_GRACE` (default 10 seconds) of the rotation: it is
refused with 401 `SESSION_EXPIRED` and the session stays alive, because that is almost always a second tab or a
retried request from the same device, and the client still holds the newer token. A session
expires when it has not been refreshed for `AUTH_REFRESH_TOKEN_TTL` (default `P30D`), and each refresh starts that
period again.

Revoking a session sets `revoked_at` and `revoked_reason`; the row is never deleted. The reasons are `LOGOUT`,
`REVOKED_BY_USER`, `REVOKED_OTHERS`, `REFRESH_TOKEN_REUSE` and `ACCOUNT_DELETION`. After the revoking transaction
commits, auth-service writes `fitsync:revoked-sid:<sid>` to Redis with a TTL of the access-token lifetime plus 60
seconds. The gateway refuses any token whose `sid` is on that list with 401 `SESSION_REVOKED`, so a revoked device
loses access at once instead of when its access token runs out.

A request for account deletion revokes all of that account's sessions. There is no password-change endpoint, so
nothing else revokes all sessions today. auth-service has no AWS credentials or Cognito admin permission, so it does
not call Cognito `AdminUserGlobalSignOut`; the Cognito refresh token held by the browser stays valid, but a pending
deletion already refuses a new bootstrap.

Two failure modes are accepted on purpose. When Redis is unreachable the gateway lets tokens through (fail open) and
logs a warning at most once a minute, because failing closed would take the whole API down with Redis. Revocation is
then still enforced within one access-token lifetime, since the revoked session's next refresh is refused by the
database. A token issued before sessions existed has no `sid` and is accepted until it expires, so a deploy does not
sign everyone out.

Session management is refused (401 `SESSION_REVOKED`) to a token whose own session is revoked, which auth-service
checks against the database. A signed-out device therefore cannot revoke the owner's other sessions even while the
gateway cannot see Redis. Revoking a session does not publish an event yet. A `SessionRevoked` event on
`user-events` could later let notification-service send a "signed out of a device" email.

### 6.3 user-service

Responsibilities:

- user profile
- preferences
- body metrics
- goal management

Own data:

- profile records
- user settings
- goal/progress tables

### 6.4 workout-service

Responsibilities:

- workout plans
- workout logs
- exercise tracking

Own data:

- workout tables
- exercise log tables

### 6.5 nutrition-service

Responsibilities:

- meals
- calorie tracking
- macro tracking
- nutrition logs

Own data:

- meal tables
- nutrition tracking tables

### 6.6 analytics-service

Responsibilities:

- user-level aggregates
- dashboard read models
- trends and summary metrics

Own data:

- derived analytics tables
- precomputed dashboard views

This service should be read-model oriented.

### 6.7 recommendation-service

Responsibilities:

- recommendation generation
- recommendation refresh
- AI or rule-engine orchestration
- cached personalized suggestions

Own data:

- recommendation outputs
- recommendation cache or result tables

### 6.8 notification-service

Responsibilities:

- event-driven notification dispatch
- reminder scheduling
- milestone and streak alerts
- notification preference-aware delivery

Own data:

- notification records
- delivery status
- reminder schedule metadata

---

## 7. Root Directory Structure

Recommended target structure:

```text
backend/
|-- docker-compose.yml
|-- pom.xml
|-- README.md
|-- api-gateway/
|   |-- pom.xml
|   `-- src/
|-- auth-service/
|   |-- pom.xml
|   `-- src/
|-- user-service/
|   |-- pom.xml
|   `-- src/
|-- workout-service/
|   |-- pom.xml
|   `-- src/
|-- nutrition-service/
|   |-- pom.xml
|   `-- src/
|-- analytics-service/
|   |-- pom.xml
|   `-- src/
|-- recommendation-service/
|   |-- pom.xml
|   `-- src/
|-- notification-service/
|   |-- pom.xml
|   `-- src/
`-- event-contracts/
    |-- pom.xml
    `-- src/
```

### 7.1 About `event-contracts`

Use `event-contracts` very carefully.

Allowed contents:

- event envelope base model
- shared event contract classes if needed
- common error contract primitives
- tiny transport-neutral DTO primitives

Do not place here:

- business entities
- repositories
- service implementations
- domain-heavy DTOs

If `event-contracts` grows too much, service boundaries are already degrading.

---

## 8. Maven Multi-Module Build Design

The backend should use a parent Maven project with child modules.

### 8.1 Parent Project Role

The root `pom.xml` should act as:

- packaging aggregator
- shared dependency version owner
- shared plugin version owner
- build entry point for all backend modules

The root project should not contain business source code once the service split is complete.

Recommended parent characteristics:

- `packaging` = `pom`
- declares all child modules
- centralizes Spring Boot version
- centralizes Java version
- centralizes test dependency versions
- centralizes plugin versions

### 8.2 Recommended Module List

The parent `pom.xml` should declare these modules:

```text
<modules>
  <module>api-gateway</module>
  <module>auth-service</module>
  <module>user-service</module>
  <module>workout-service</module>
  <module>nutrition-service</module>
  <module>analytics-service</module>
  <module>recommendation-service</module>
  <module>notification-service</module>
  <module>event-contracts</module>
</modules>
```

### 8.3 Module Dependency Rules

Recommended dependency direction:

- every service module may depend on `event-contracts`
- service modules must not depend on each other through implementation packages
- service-to-service communication should happen through HTTP or Kafka, not direct Java module imports
- `api-gateway` should not import business implementation code from domain services

Allowed:

- `auth-service -> event-contracts`
- `workout-service -> event-contracts`
- `notification-service -> event-contracts`

Avoid:

- `workout-service -> user-service`
- `analytics-service -> nutrition-service`
- `recommendation-service -> workout-service` as code dependency

### 8.4 Shared Dependency Management

The parent `pom.xml` should manage:

- Spring Boot BOM or Spring Boot parent usage
- Java version
- plugin versions
- test dependency versions

Each child module should contain only:

- service-specific dependencies
- its own artifact identity
- its own build overrides only when necessary

### 8.5 Plugin Baseline

Recommended parent plugin baseline:

- `spring-boot-maven-plugin`
- `maven-compiler-plugin`
- `maven-surefire-plugin`
- `maven-failsafe-plugin` if integration tests are separated later
- `jacoco-maven-plugin` if coverage reporting is needed

---

## 9. Per-Service Package Structure

Each service should keep a familiar but local package structure.

### 9.1 api-gateway

```text
src/main/java/com/fitsync/gateway/
|-- GatewayApplication.java
|-- config/
|-- filter/
|-- security/
|-- routing/
`-- exception/
```

### 9.2 auth-service

```text
src/main/java/com/fitsync/auth/
|-- AuthServiceApplication.java
|-- controller/
|-- service/
|-- repository/
|-- entity/
|-- dto/
|-- security/
|-- config/
`-- exception/
```

### 9.3 user-service

```text
src/main/java/com/fitsync/user/
|-- UserServiceApplication.java
|-- controller/
|-- service/
|-- repository/
|-- entity/
|-- dto/
|-- mapper/
|-- config/
`-- exception/
```

### 9.4 workout-service

```text
src/main/java/com/fitsync/workout/
|-- WorkoutServiceApplication.java
|-- controller/
|-- service/
|-- repository/
|-- entity/
|-- dto/
|-- mapper/
|-- messaging/
|-- config/
`-- exception/
```

### 9.5 nutrition-service

```text
src/main/java/com/fitsync/nutrition/
|-- NutritionServiceApplication.java
|-- controller/
|-- service/
|-- repository/
|-- entity/
|-- dto/
|-- mapper/
|-- messaging/
|-- config/
`-- exception/
```

### 9.6 analytics-service

```text
src/main/java/com/fitsync/analytics/
|-- AnalyticsServiceApplication.java
|-- controller/
|-- service/
|-- dto/
|-- readmodel/
|-- consumer/
|-- handler/
|-- config/
`-- exception/
```

### 9.7 recommendation-service

```text
src/main/java/com/fitsync/recommendation/
|-- RecommendationServiceApplication.java
|-- controller/
|-- service/
|-- dto/
|-- client/
|-- consumer/
|-- handler/
|-- config/
`-- exception/
```

### 9.8 notification-service

```text
src/main/java/com/fitsync/notification/
|-- NotificationServiceApplication.java
|-- controller/
|-- service/
|-- dto/
|-- consumer/
|-- handler/
|-- scheduler/
|-- config/
`-- exception/
```

---

## 10. Service Configuration Baseline

Each service should have:

- its own `pom.xml`
- its own `src/main/resources/application.yml`
- its own `application-local.yml`
- its own `application-test.yml`
- its own migration scripts
- its own test suite

Recommended shared infrastructure in local development:

- one PostgreSQL instance with separate schemas initially
- one Redis instance if needed
- one Kafka broker cluster for async integration
- one shared Docker Compose file at backend root

Recommended environment variables per service:

- `SERVER_PORT`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET` where applicable
- `REDIS_HOST` and `REDIS_PORT` where needed
- Kafka bootstrap server settings for producer/consumer services

---

## 11. Communication Model

The backend should intentionally use two communication styles.

### 11.1 Synchronous

Use synchronous API calls for:

- authentication
- profile updates
- workout CRUD
- meal CRUD
- goal management
- dashboard and recommendation reads
- operations that need immediate success/failure feedback

### 11.2 Asynchronous

Use Kafka-driven event processing for:

- analytics recomputation
- recommendation refresh
- notifications
- audit/activity side effects

This separation is deliberate:

- **API calls** handle primary transactional workflows
- **events** handle secondary, derived, or fan-out workflows

See:

- [backend-api-communication.md](backend-api-communication.md)
- [backend-kafka-event-architecture.md](backend-kafka-event-architecture.md)

---

## 12. Data Ownership Rules

Each service should own its own tables and write paths.

Recommended ownership:

- `auth-service`: auth tables
- `user-service`: profile, goals, body metrics
- `workout-service`: workouts, exercises, workout logs
- `nutrition-service`: meals, nutrition logs
- `analytics-service`: derived aggregates, dashboard views
- `recommendation-service`: recommendation results, cached suggestions
- `notification-service`: notification records, reminder jobs, delivery status

Rules:

- do not let services write into another service's transactional tables
- analytics and recommendation should consume data, not own core CRUD state
- notification should consume events, not become a primary transactional dependency
- shared PostgreSQL instance is acceptable initially, but use separate schemas per service

Recommended initial schemas:

- `auth`
- `user_domain`
- `workout`
- `nutrition`
- `analytics`
- `recommendation`
- `notification`

---

## 13. Dependency Boundary Rules

To keep the multi-module backend clean, use these rules:

- no service module should import another service module's `entity`, `repository`, or `service` package
- shared contracts must stay transport-oriented and minimal
- persistence models are local to each service
- API DTOs are local to each service unless they are explicitly cross-service transport contracts
- event DTOs may live in `event-contracts` only if they are truly shared and versioned

Strong recommendation:

- keep `event-contracts` as small as possible
- if a class only serves one service, it should remain inside that service

---

## 14. Tooling Baseline

The current backend stack is:

- Java 17
- Spring Boot 3.3.x
- Maven
- Spring Web
- Spring Security
- Spring Validation
- Spring Data JPA
- Spring Actuator
- PostgreSQL
- Redis
- springdoc-openapi
- JUnit 5
- Mockito
- Spring Boot Test
- Testcontainers

Expected service-split additions:

- Spring for Apache Kafka
- Kafka broker in local development
- Kafka integration tests via Testcontainers
- per-service migration scripts
- per-service configuration files

---

## 15. High-Level Flow Patterns

### 15.1 Register Flow

Recommended pattern:

1. frontend calls `api-gateway`
2. `api-gateway` routes to `auth-service`
3. `auth-service` creates auth account
4. `auth-service` returns success
5. `auth-service` publishes `UserRegistered`
6. `user-service` creates profile scaffold if needed
7. downstream services initialize derived state and optional onboarding notifications

### 15.2 Workout Logging Flow

Recommended pattern:

1. frontend calls `api-gateway`
2. `api-gateway` routes to `workout-service`
3. `workout-service` validates and writes workout log
4. `workout-service` returns success
5. `workout-service` publishes `WorkoutLogged`
6. `analytics-service` updates metrics
7. `recommendation-service` refreshes suggestions asynchronously
8. `notification-service` sends streak or completion notifications asynchronously

### 15.3 Meal Logging Flow

Recommended pattern:

1. frontend calls `api-gateway`
2. `api-gateway` routes to `nutrition-service`
3. `nutrition-service` writes meal log
4. `nutrition-service` returns success
5. `nutrition-service` publishes `MealLogged`
6. downstream consumers refresh derived outputs
7. `notification-service` sends reminder-related follow-up if configured

### 15.4 Goal Update Flow

Recommended pattern:

1. frontend calls `api-gateway`
2. `api-gateway` routes to `user-service`
3. `user-service` updates goal
4. `user-service` returns success
5. `user-service` publishes `GoalUpdated`
6. `analytics-service`, `recommendation-service`, and `notification-service` react asynchronously

---

## 16. Migration Sequence

The safest implementation order is:

1. convert the root `pom.xml` into a parent aggregator
2. create `event-contracts`
3. create `auth-service` and move current auth code there
4. create `user-service`
5. create `workout-service`
6. create `nutrition-service`
7. create `analytics-service`
8. create `recommendation-service`
9. create `notification-service`
10. add `api-gateway`
11. add Kafka producer/consumer integration

Why this order works:

- it moves the current codebase first into the most mature domain
- it keeps early migration centered around auth and user flows
- it avoids introducing gateway and Kafka too early in the directory migration

---

## 17. Evolution Path

The recommended evolution path is:

1. stabilize domain boundaries in the current codebase
2. define service ownership and shared contracts
3. formalize synchronous API rules in [backend-api-communication.md](backend-api-communication.md)
4. formalize Kafka event rules in [backend-kafka-event-architecture.md](backend-kafka-event-architecture.md)
5. split services only along these defined ownership lines

This keeps the target architecture clear without forcing every concern into one document.

---

## 18. Common Pitfalls To Avoid

Avoid these patterns:

- synchronous cross-service orchestration for every user action
- shared business entities in `event-contracts`
- direct cross-service table access
- one service calling several others just to finish a CRUD request
- Kafka being required for primary request success
- analytics service reading transactional tables from other services directly
- notification service becoming a synchronous dependency of core write requests

If these patterns appear, the system is turning into a distributed monolith.

---

## 19. Immediate Next Steps

The most practical next steps are:

1. convert the current backend `pom.xml` into a parent multi-module design
2. define `event-contracts` boundaries before creating service code
3. move existing auth code into `auth-service`
4. scaffold `user-service`, `workout-service`, and `nutrition-service`
5. keep notification event-driven
6. treat API and Kafka as separate design layers, not competing patterns

---

## 20. Final Recommendation

The most practical backend target for FitSync is:

- small number of services
- clear domain ownership
- API-first for user-facing transactions
- Kafka for post-commit domain events
- analytics, recommendation, and notification centered around event consumption

This is the cleanest way to split early without over-splitting.
