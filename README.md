# FitSync Backend

The backend of FitSync, a fitness and nutrition app: Spring Boot microservices behind an API gateway,
each owning its own PostgreSQL schema, talking over REST and asynchronously over Kafka domain events
published through a transactional outbox. An AI coach in `recommendation-service` reads the other
services through the gateway and answers with an Anthropic, Amazon Bedrock or local Ollama model.

## Contents

- [Services](#services)
- [Shared modules](#shared-modules)
- [Stack](#stack)
- [Run locally](#run-locally)
- [Tests and checks](#tests-and-checks)
- [Domain events](#domain-events)
- [Seed data and media](#seed-data-and-media)
- [Deployment](#deployment)
- [Repository layout and further docs](#repository-layout-and-further-docs)

## Services

| Service | Port | Owns | API prefix |
|---|---|---|---|
| `api-gateway` | 8088 | Single entry point: routing, edge JWT check, CORS, rate limiting | `/api/v1/**` (forwards) |
| `auth-service` | 8080 | Accounts, credentials, JWT issuance, optional AWS Cognito sign-in, account deletion | `/api/v1/auth` |
| `user-service` | 8081 | Profile, preferences, body metrics, goals, energy and nutrient reference profile, first-run guide state | `/api/v1/users` |
| `workout-service` | 8082 | Exercise catalogue (free-exercise-db + custom), plans with day entries, live sessions with set logging, records, training progress | `/api/v1/workouts` |
| `nutrition-service` | 8083 | Food catalogue (USDA, brands, typo-tolerant search), meals and diary, recipes and cooks, water, nutrient progress | `/api/v1/nutrition` |
| `analytics-service` | 8085 | Read models built from events: dashboard, streaks, achievements, weekly recaps, Progress pages | `/api/v1/analytics` |
| `recommendation-service` | 8086 | AI coach: chat threads, session drafts, weekly reviews, Progress card readings, recommendations | `/api/v1/coach`, `/api/v1/recommendations` |

Every service also serves `GET /actuator/health` and Swagger UI at `/swagger-ui.html`. Paths under
`/api/v1/internal/**` are service-to-service only. The gateway forwards an explicit list of them
and nothing else.

`notification-service` is an empty placeholder module with no code yet. The image build skips it.

## Shared modules

- `event-contracts`: Kafka event payloads (`com.fitsync.contracts.events.*`) with golden JSON
  samples. Read [`event-contracts/SCHEMA-GOVERNANCE.md`](event-contracts/SCHEMA-GOVERNANCE.md)
  before changing a payload.
- `persistence-support`: the auditable base entity and JPA auditing.
- `web-support`: error responses, `PageResponse`, security helpers shared by the web layer.
- `media-support`: the S3 client behind exercise images and avatars (MinIO locally, S3 in AWS).

## Stack

- Java 17, Spring Boot 3, Maven multi-module build
- PostgreSQL 16 (one schema per service, Flyway migrations), Redis (caching), Kafka (events)
- MinIO or S3 for media
- JWT auth issued by `auth-service`, optionally fronted by AWS Cognito
- AI providers for the coach: Anthropic API, Amazon Bedrock or Ollama (`RECOMMENDATION_AI_PROVIDER`)
- Quality gates: SpotBugs + FindSecBugs (`-Psast`), Checkstyle, JaCoCo, gitleaks, Trivy, OWASP
  Dependency-Check

## Run locally

Prerequisites: JDK 17 or newer, Maven 3.9, Docker.

1. Start the infrastructure:

   ```bash
   docker compose up -d postgres redis minio minio-init kafka
   ```

2. Run the services you need, each in its own terminal. The `local` profile points them at the
   compose infrastructure:

   ```bash
   mvn -pl auth-service spring-boot:run -Dspring-boot.run.profiles=local
   mvn -pl user-service spring-boot:run -Dspring-boot.run.profiles=local
   mvn -pl workout-service spring-boot:run -Dspring-boot.run.profiles=local
   mvn -pl nutrition-service spring-boot:run -Dspring-boot.run.profiles=local
   mvn -pl analytics-service spring-boot:run
   mvn -pl recommendation-service spring-boot:run
   mvn -pl api-gateway spring-boot:run
   ```

3. Call the API through the gateway at `http://localhost:8088`.

**Events.** Kafka delivery is off by default, so the services work without a broker, but
cross-service projections (analytics, nutrition targets from goals, the coach's read model) stay
empty. To turn it on, add the `kafka` profile to the producing services
(`-Dspring-boot.run.profiles=local,kafka`) and set `KAFKA_ENABLED=true` plus the
`KAFKA_*_LISTENER_ENABLED` flags on the consumers. [`DEPLOY.md` §11](DEPLOY.md) lists every flag.

**AI coach.** The coach is off until `RECOMMENDATION_AI_ENABLED=true`. Then pick a provider:

| `RECOMMENDATION_AI_PROVIDER` | Also set |
|---|---|
| `anthropic` (default) | an Anthropic API key |
| `bedrock` | AWS credentials and region in the usual SDK places, and a Bedrock model or inference-profile ID |
| `ollama` | `RECOMMENDATION_AI_BASE_URL` (default `http://localhost:11434`) and a pulled model tag |

`recommendation-service/src/main/resources/application.yml` documents each setting.

**Full stack in containers.** `docker-compose.prod.yml` runs all services, Redis, Kafka (the
`events` profile) and nginx from the images `scripts/build-images.sh` builds. The demo recording
setup in [`scripts/README.md`](scripts/README.md) uses it locally.

## Tests and checks

```bash
mvn test                                     # all unit and integration tests
mvn -pl workout-service -am test             # one service and the modules it depends on
mvn -T 2 verify -Psast                       # what CI runs: tests, coverage, SpotBugs, Checkstyle
```

A few integration tests use Testcontainers and need Docker. The Kafka round-trip tests run only
with `KAFKA_INTEGRATION_TESTS=true` and a broker on `localhost:9092`.

The coach has an evaluation set in `scripts/coach-eval/` that asks a running stack fixed
questions and checks the answers.

## Domain events

Producers write events to an outbox table in the same transaction as the change. A relay then
publishes them to Kafka, and consumers record processed event IDs so a redelivery is a no-op.

| Producer | Events |
|---|---|
| `auth-service` | `UserRegistered`, `AccountDeletionRequested`, `AccountDeletionCancelled`, `AccountDeleted` |
| `user-service` | `UserProfileUpdated`, `UserPreferencesUpdated`, `BodyMetricsRecorded`, `GoalUpdated`, `EnergyProfileComputed`, `NutritionReferenceProfileUpdated`, `AccountDeleted` |
| `workout-service` | `WorkoutLogged`, `WorkoutUpdated`, `WorkoutDeleted`, `PersonalRecordAchieved` |
| `nutrition-service` | `MealLogged`, `MealUpdated`, `MealDeleted`, `RecipeCooked` |
| `analytics-service` | `WeeklyRecapGenerated`, `StreakMilestoneReached` |

The main consumers are `analytics-service` and `recommendation-service`. `nutrition-service`
also consumes goal and reference-profile events for its daily targets. Payloads live in
`event-contracts`. Failed records are retried, then sent to a dead-letter topic.

### Account deletion

A person asks to delete their account in auth-service, which signs them out and keeps the account
for a grace period (`ACCOUNT_DELETION_GRACE_DAYS`, 30 days) during which signing in again can cancel
it. A daily job in auth-service (also run once at startup; `ACCOUNT_DELETION_PURGE_ENABLED=false`
turns it off) then claims each account whose grace period has ended and publishes `AccountDeleted`
through its outbox. Every service that keeps the person's data erases it when it receives the event,
user-service included (rows, avatar images and its outbox rows). Once the relay has published the
event, auth-service deletes the Cognito user (`ACCOUNT_DELETION_COGNITO_DELETE_ENABLED`, which
follows `COGNITO_ENABLED`) and then the account row, its sessions and its outbox rows. These are
real deletes: everywhere else a user-facing delete is a soft delete, but erasure must not keep
anything that identifies the person.

## Seed data and media

- **Exercises:** the catalogue comes from the public-domain
  [free-exercise-db](https://github.com/yuhonas/free-exercise-db). The JSON is committed, but the
  images are not: `scripts/fetch-free-exercise-db-images.sh` downloads them, and
  `workout-service` imports them into object storage in the background at startup (set
  `WORKOUT_CATALOG_AUTO_IMPORT=false` to skip). If no local images are present, it falls back to
  the upstream download.
- **Foods and recipes:** `nutrition-service/src/main/resources/db/seed/` holds the USDA SR Legacy
  foods, micronutrients and portions, TheMealDB system recipes, nutrient guides and brand logos.
  [`tools/usda/`](tools/usda/README.md) and [`tools/themealdb/`](tools/themealdb/README.md)
  regenerate them.
- **Demo accounts:** `scripts/seed-demo-fitsync.sql` and the `demo-*.ps1` scripts, described in
  [`scripts/README.md`](scripts/README.md).

## Deployment

AWS is provisioned with Terraform (`infra/terraform/`): staging and prod, each with VPC, EC2
behind an ALB, RDS, ECR, Cognito, S3 media and Secrets Manager. CD
([`.github/workflows/cd.yml`](.github/workflows/cd.yml)) is started by hand. It runs only on a
commit CI passed, deploys staging first and promotes to prod after approval.

- [`DEPLOY.md`](DEPLOY.md): host setup, runtime configuration, media, Kafka
- [`infra/terraform/RUNBOOK.md`](infra/terraform/RUNBOOK.md): Terraform workflow and environments
- [`.github/CICD-VARIABLES.md`](.github/CICD-VARIABLES.md): GitHub Actions variables and secrets
- [`stress/README.md`](stress/README.md): k6 load tests

## Repository layout and further docs

```
api-gateway/  auth-service/  user-service/  workout-service/  nutrition-service/
analytics-service/  recommendation-service/  notification-service/   services
event-contracts/  persistence-support/  web-support/  media-support/   shared modules
infra/terraform/   AWS infrastructure          scripts/   ops, seed and demo scripts
tools/             seed generators             stress/    k6 load tests
nginx/             reverse proxy template      checkstyle/, spotbugs-exclude.xml   build rules
```

Design notes, written as the system was planned and updated in places since:

- [`backend.md`](backend.md): service boundaries, module rules, data ownership
- [`backend-api-communication.md`](backend-api-communication.md): synchronous API rules
- [`backend-kafka-event-architecture.md`](backend-kafka-event-architecture.md): event design
- Per service: [`api-gateway/README.md`](api-gateway/README.md),
  [`user-service/user-service-design.md`](user-service/user-service-design.md),
  [`workout-service/workout-service-design.md`](workout-service/workout-service-design.md),
  [`nutrition-service/nutrition-service-design.md`](nutrition-service/nutrition-service-design.md),
  [`analytics-service/analytics-service-design.md`](analytics-service/analytics-service-design.md),
  [`recommendation-service/README.md`](recommendation-service/README.md) and its `docs/`
