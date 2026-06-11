# FitSync AI Backend

Multi-module Spring Boot backend foundation for FitSync. The current runnable implementation lives in `auth-service`, and `user-service` now includes persisted profile, preferences, body metrics, and goals APIs for incremental extraction.

## Stack

- Java 17 locally, ready to move to Java 21
- Spring Boot 3.3
- Maven
- Spring Web, Security, Validation, Data JPA, Actuator
- PostgreSQL, Redis, and Kafka
- OpenAPI 3 with Swagger UI

## Modules

- `auth-service`: current runnable implementation for auth and token issuance
- `user-service`: persisted user domain implementation for profile, preferences, body metrics, and goals
- `api-gateway`: edge gateway scaffold with design documented in `api-gateway/README.md`
- `workout-service`: persisted workout domain implementation for plans, sessions, and outbox-backed workout events
- `nutrition-service`, `analytics-service`, `recommendation-service`, `notification-service`: module scaffolds
- `event-contracts`: shared event contract scaffold

## Run Locally

Start infrastructure:

```powershell
docker compose up -d
```

Run tests:

```powershell
mvn test
```

Start the current runnable services:

```powershell
mvn -pl auth-service spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl workout-service spring-boot:run
mvn -pl api-gateway spring-boot:run
```

Start the services with Kafka event delivery enabled:

```powershell
mvn -pl auth-service spring-boot:run "-Dspring-boot.run.profiles=local,kafka"
mvn -pl user-service spring-boot:run "-Dspring-boot.run.profiles=local,kafka"
mvn -pl workout-service spring-boot:run "-Dspring-boot.run.profiles=local,kafka"
```

Kafka defaults:

- `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`
- `KAFKA_ENABLED=false` unless the `kafka` Spring profile is active
- `KAFKA_USER_REGISTERED_LISTENER_ENABLED=false` unless the `kafka` Spring profile is active
- `KAFKA_WORKOUT_EVENTS_TOPIC=workout-events` by default for `workout-service`

Run the broker-backed `WorkoutRelayKafkaIT` integration test after `docker compose up -d kafka`:

```powershell
$env:KAFKA_INTEGRATION_TESTS="true"
mvn "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl workout-service -am verify
```

Run the broker-backed `UserRegistered` integration test after `docker compose up -d kafka`:

```powershell
$env:KAFKA_INTEGRATION_TESTS="true"
mvn "-Dmaven.repo.local=E:/Code/NUS-ISS/Capstone/backend/.m2/repository" -pl user-service -am verify
```

Useful endpoints:

- `GET /actuator/health` for internal service health
- `GET /api/v1/health` on `api-gateway` for edge health
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/bootstrap`
- `GET /api/v1/auth/me`
- `POST /api/v1/workouts/plans`
- `POST /api/v1/workouts/sessions`
- `POST /api/v1/internal/outbox/publish`
- `GET /swagger-ui.html`

Gateway design reference:

- `backend/api-gateway/README.md` defines the recommended ingress path, route ownership, edge JWT validation, rate limiting, observability, and rollout phases.
- `api-gateway` defaults to port `8088` and routes to:
  - `AUTH_SERVICE_BASE_URL=http://localhost:8080`
  - `USER_SERVICE_BASE_URL=http://localhost:8081`

Cognito transition settings for `auth-service`:

- `COGNITO_ENABLED=false` keeps the legacy local JWT flow only
- `COGNITO_ENABLED=true` enables JWT validation against AWS Cognito
- `COGNITO_ISSUER_URI=https://cognito-idp.<region>.amazonaws.com/<userPoolId>`
- `COGNITO_AUDIENCE=<app-client-id>`
- `COGNITO_JWK_SET_URI` is optional when the default Cognito JWKS path is used
- `COGNITO_PRINCIPAL_CLAIM` defaults to `username`

Current local Cognito SPA setup confirmed on 2026-06-12:

- Region: `ap-southeast-2`
- User pool ID: `ap-southeast-2_8lgadn`
- SPA app client name: `FitSync`
- SPA app client ID: `4mvse0cgmkplgocfg827uolvad`
- Managed login domain: `https://ap-southeast-2kjyn2vzun.auth.ap-southeast-2.amazoncognito.com`
- Allowed callback URL: `http://localhost:3000/auth/callback`
- Allowed sign-out URL: `http://localhost:3000/logout/callback`
- OAuth grant type: `Authorization code grant`
- OIDC scopes: `openid`, `email`, `profile`
- Identity provider: `Cognito user pool directory`
- Client secret: not generated for the SPA client

Recommended local environment values for the current backend implementation:

```text
COGNITO_ENABLED=true
COGNITO_ISSUER_URI=https://cognito-idp.ap-southeast-2.amazonaws.com/ap-southeast-2_8lgadn
COGNITO_AUDIENCE=4mvse0cgmkplgocfg827uolvad
COGNITO_PRINCIPAL_CLAIM=username
```

## Notes

- The repository has been converted to a Maven multi-module layout.
- `auth-service` owns auth credentials and token issuance.
- `auth-service` now also supports optional AWS Cognito JWT validation plus local user bootstrapping for managed-login migration.
- `user-service` owns user profile, preferences, body metrics, and goals with JPA + Flyway persistence.
- `workout-service` owns workout plans and sessions with JPA + Flyway persistence plus outbox-backed Kafka delivery for `WorkoutLogged`, `WorkoutUpdated`, and `WorkoutDeleted`.
- Additional services remain scaffolded.
- Production compose now runs `nginx -> api-gateway -> domain services` and expects host-level TLS material under `/etc/letsencrypt`.
- Set `NGINX_CERT_NAME` to the Let's Encrypt certificate directory name and optionally set `NGINX_SERVER_NAME` for the generated Nginx config.
- `docker-compose.prod.yml` also defines a profile-gated `user-service` container for future rollout. Enable it with `COMPOSE_PROFILES=future-services`.
- The `user-service` container stays internal-only and is intended to be reached through `api-gateway`.
