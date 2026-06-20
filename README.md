# FitSync AI Backend

Multi-module Spring Boot backend for FitSync — a microservices fitness platform (auth, user,
workout, nutrition, analytics) communicating over REST through an edge gateway and asynchronously
via Kafka domain events. Most domain services are fully implemented with JPA + Flyway persistence;
`recommendation-service` and `notification-service` are still scaffolds.

## Stack

- Java 17 (ready to move to Java 21), Spring Boot 3.3, Maven multi-module
- Spring Web, Security, Validation, Data JPA, Actuator
- PostgreSQL (per-service schemas, Flyway migrations), Redis (caching), Kafka (domain events)
- MinIO / S3-compatible object storage for media (exercise demo images)
- OpenAPI 3 + Swagger UI; JWT auth (local issuance + optional AWS Cognito)

## Modules

**Domain services**
- `auth-service` — auth, credentials, JWT issuance; optional AWS Cognito validation + bootstrap.
- `user-service` — profile, preferences, body metrics, goals.
- `workout-service` — exercises (catalog + custom), plans, sessions with **live set-by-set logging**,
  per-exercise history + personal records, import/export; outbox-backed workout events.
- `nutrition-service` — meals, custom foods, food catalog + categories, recent/frequent/favorites,
  meal templates, quick-add/copy, daily summaries; outbox-backed nutrition events.
- `analytics-service` — CQRS read model: consumes domain events, maintains projections, serves
  dashboard / trends / summaries / goal progress; nightly rollup; optional Redis cache.
- `api-gateway` — edge gateway (routing, edge JWT, rate limiting); see `api-gateway/README.md`.
- `recommendation-service`, `notification-service` — scaffolds.

**Shared modules**
- `event-contracts` — shared Kafka event payload contracts (`com.fitsync.contracts.events.*`).
- `persistence-support` — `BaseAuditableEntity` and JPA/auditing support.
- `web-support` — shared web layer (error handling, `PageResponse`, security helpers).
- `media-support` — `MediaStorageService` (AWS SDK v2 S3 client) for object-storage media; backs
  exercise demo images and is reusable for avatars and other uploads.

## Run Locally

Start infrastructure (PostgreSQL, Redis, Kafka, MinIO):

```powershell
docker compose up -d postgres redis kafka minio minio-init
```

Run all tests:

```powershell
mvn test
```

Start services (default profile):

```powershell
mvn -pl auth-service spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl workout-service spring-boot:run
mvn -pl nutrition-service spring-boot:run
mvn -pl analytics-service spring-boot:run
mvn -pl api-gateway spring-boot:run
```

Start with Kafka event delivery enabled (`local,kafka` profiles):

```powershell
mvn -pl auth-service spring-boot:run "-Dspring-boot.run.profiles=local,kafka"
mvn -pl user-service spring-boot:run "-Dspring-boot.run.profiles=local,kafka"
mvn -pl workout-service spring-boot:run "-Dspring-boot.run.profiles=local,kafka"
mvn -pl nutrition-service spring-boot:run "-Dspring-boot.run.profiles=local,kafka"
mvn -pl analytics-service spring-boot:run "-Dspring-boot.run.profiles=local,kafka"
```

Kafka defaults:

- `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`
- `KAFKA_ENABLED=false` unless the `kafka` Spring profile is active
- per-service topics, e.g. `KAFKA_WORKOUT_EVENTS_TOPIC=workout-events`

Run a broker-backed integration test after `docker compose up -d kafka`:

```powershell
$env:KAFKA_INTEGRATION_TESTS="true"
mvn "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl workout-service -am verify
```

## Media storage & exercise demo catalog (seed)

Exercise demonstration images are **not stored in git or the jar** — they live in object storage
(MinIO locally, S3/CDN in prod) and the database stores only the image URL. `media-support`'s
`MediaStorageService` handles uploads. `docker compose up -d` starts `minio` + a `minio-init` job
that creates the `fitsync-media` bucket (anonymous read) at `http://localhost:9000`.

The global exercise catalog is seeded from the open-source **free-exercise-db**
(github.com/yuhonas/free-exercise-db, Unlicense / public domain). The catalog JSON + LICENSE are
committed; the ~94 MB of images are fetched once into `.local/free-exercise-db/exercises` and
uploaded to object storage by the importer.

**Seed through compose (per fresh environment):**

1. Build the service jars and runtime image:
   `mvn -DskipTests package` then `scripts/build-images.ps1` (Windows) or
   `scripts/build-images.sh` (bash).
2. Fetch the free-exercise-db `exercises/` tree into the ignored local cache:
   `scripts/fetch-free-exercise-db-images.ps1` or `scripts/fetch-free-exercise-db-images.sh`.
3. Start compose: `docker compose up -d`.

Compose mounts `${FREE_EXERCISE_DB_IMAGES_DIR:-./.local/free-exercise-db/exercises}` read-only at
`/opt/fitsync/free-exercise-db/exercises` and sets
`FREE_EXERCISE_DB_IMAGE_SOURCE_DIRECTORY` to that container path. The startup runner imports the
catalog automatically when `WORKOUT_CATALOG_AUTO_IMPORT` is unset or `true`; set
`WORKOUT_CATALOG_AUTO_IMPORT=false` to disable it. The manual admin import endpoint remains
available at `POST /api/v1/internal/workouts/exercises/import-catalog`.

The importer is idempotent (keyed on `(external_source, external_ref)`); re-running won't duplicate.
Production points media at a real bucket/CDN via `FITSYNC_MEDIA_*` env and uses the same
`FREE_EXERCISE_DB_IMAGES_DIR` read-only mount contract.

## Domain events (Kafka, via transactional outbox)

- `workout-service`: `WorkoutLogged`, `WorkoutUpdated`, `WorkoutDeleted`, `PersonalRecordAchieved`
- `nutrition-service`: meal logged / updated / deleted events
- `user-service`: `UserRegistered`, body-metric / goal events
- `analytics-service` consumes the above to build read-model projections.

## Useful endpoints

- `GET /actuator/health` — per-service health; `GET /api/v1/health` — edge health on `api-gateway`
- `POST /api/v1/auth/register`, `/login`, `/bootstrap`; `GET /api/v1/auth/me`
- `GET /api/v1/workouts/exercises`, `/exercises/{id}` (with demo media), `/exercises/metadata`
- `POST /api/v1/workouts/plans`, `/plans/{id}/start`, `/plans/{id}/activate`
- `POST /api/v1/workouts/sessions`, `/sessions/{id}/complete`, `/sessions/{id}/exercises/...`
- `GET /api/v1/workouts/exercises/{id}/records` + `/history`
- `GET /api/v1/analytics/dashboard`; `POST /api/v1/internal/analytics/.../rebuild`
- `GET /swagger-ui.html`

## Gateway

- `backend/api-gateway/README.md` defines ingress, route ownership, edge JWT validation, rate
  limiting, observability, and rollout phases.
- `api-gateway` defaults to port `8088` and routes to:
  - `AUTH_SERVICE_BASE_URL=http://localhost:8080`
  - `USER_SERVICE_BASE_URL=http://localhost:8081`
  - `WORKOUT_SERVICE_BASE_URL=http://localhost:8082`
  - `NUTRITION_SERVICE_BASE_URL=http://localhost:8083`
- Only `/api/v1/auth/bootstrap` is permitted unauthenticated (to forward the Cognito token to
  `auth-service`); other protected APIs use the local FitSync JWT returned by bootstrap.

## AWS Cognito (auth-service)

- `COGNITO_ENABLED=false` keeps the legacy local JWT flow only; `true` validates against Cognito.
- `COGNITO_ISSUER_URI=https://cognito-idp.<region>.amazonaws.com/<userPoolId>`
- `COGNITO_AUDIENCE=<app-client-id>`; `COGNITO_PRINCIPAL_CLAIM` defaults to `username`;
  `COGNITO_JWK_SET_URI` optional when the default JWKS path is used.

Current local Cognito SPA setup (confirmed 2026-06-14):

- Region `ap-southeast-1`, user pool `ap-southeast-1_myVyuOmPD`, SPA client `FitSync`
  (`4mvse0cgmkplgocfg827uolvad`), no client secret.
- Managed login domain `https://ap-southeast-1myvyuompd.auth.ap-southeast-1.amazoncognito.com`
- Callback `http://localhost:3000/auth/callback`, sign-out `http://localhost:3000/logout/callback`
- Auth-code grant; app-client flow `USER_PASSWORD_AUTH`; scopes `openid email profile`.

```text
COGNITO_ENABLED=true
COGNITO_ISSUER_URI=https://cognito-idp.ap-southeast-1.amazonaws.com/ap-southeast-1_myVyuOmPD
COGNITO_AUDIENCE=4mvse0cgmkplgocfg827uolvad
COGNITO_PRINCIPAL_CLAIM=username
```

Run `auth-service` with the `cognito` profile so `/api/v1/auth/bootstrap` validates the Cognito ID
token and returns a local FitSync JWT for downstream services:

```powershell
mvn -pl auth-service spring-boot:run "-Dspring-boot.run.profiles=local,kafka,cognito"
```

## Production (docker-compose.prod.yml)

- Runs `nginx -> api-gateway -> domain services`; expects host TLS material under `/etc/letsencrypt`.
- Set `NGINX_CERT_NAME` to the Let's Encrypt cert directory; optionally `NGINX_SERVER_NAME`.
- Media: set `FITSYNC_MEDIA_*` (endpoint, bucket, keys, public base URL) to a real S3/CDN.
- Per-service containers reach each other internally and are exposed only through `api-gateway`.
