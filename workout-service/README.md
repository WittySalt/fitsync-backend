# Workout Service

`workout-service` owns exercises, workout plans, workout sessions, exercise logs, and outbox-backed workout domain events.

Related design references:

- [workout-service-design.md](workout-service-design.md)
- [backend-kafka-event-architecture.md](../backend-kafka-event-architecture.md)

## Current Scope

Implemented backend capabilities:

- exercise catalog list and user custom exercise creation
- workout plan create, list, read, update, archive
- workout session create, list, latest, read, update, delete
- JWT-protected user-scoped APIs
- Flyway-managed persistence
- outbox-backed event publication for:
  - `WorkoutLogged`
  - `WorkoutUpdated`
  - `WorkoutDeleted`
  - `PersonalRecordAchieved`
- admin-only internal outbox relay endpoint

## Local Run

Start shared infrastructure:

```powershell
docker compose up -d postgres kafka
```

Run the service in local mode:

```powershell
mvn -pl workout-service spring-boot:run
```

Run the service with Kafka publishing enabled:

```powershell
mvn -pl workout-service spring-boot:run "-Dspring-boot.run.profiles=local,kafka"
```

Default local settings:

- `SERVER_PORT=8082`
- `DB_URL=jdbc:postgresql://localhost:5432/fitsync`
- `DB_USERNAME=fitsync`
- `DB_PASSWORD=fitsync`
- `JWT_SECRET=change-me-change-me-change-me-change-me`
- `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`
- `KAFKA_WORKOUT_EVENTS_TOPIC=workout-events`
- `KAFKA_ENABLED=false` unless `kafka` profile is active
- `OUTBOX_RELAY_ENABLED=false` unless explicitly enabled

## Relay Flow

Primary writes do not block on Kafka delivery.

Flow:

1. `workout-service` writes the workout transaction.
2. The service writes a pending outbox row.
3. An admin or scheduler triggers relay.
4. Relay publishes to Kafka and marks the outbox row as published.

Manual relay example:

```powershell
curl -X POST "http://localhost:8082/api/v1/internal/outbox/publish?batchSize=10" ^
  -H "Authorization: Bearer <admin-jwt>"
```

The internal endpoints are `ADMIN`-only:

- `POST /api/v1/internal/outbox/publish`
- `GET /api/v1/internal/outbox/pending`
- `POST /api/v1/internal/workouts/exercises/import-catalog`
- `GET /api/v1/internal/workouts/exercises/summary`

## Useful APIs

- `GET /actuator/health`
- `POST /api/v1/workouts/exercises`
- `GET /api/v1/workouts/exercises?q=&exerciseType=&primaryMuscleGroup=&equipment=&difficulty=&page=0&size=20`
- `POST /api/v1/workouts/plans`
- `GET /api/v1/workouts/plans?status=&page=0&size=20`
- `PUT /api/v1/workouts/plans/{planId}`
- `POST /api/v1/workouts/sessions`
- `GET /api/v1/workouts/sessions?from=&to=&status=&planId=&page=0&size=20`
- `GET /api/v1/workouts/sessions/latest`
- `PUT /api/v1/workouts/sessions/{sessionId}`
- `DELETE /api/v1/workouts/sessions/{sessionId}`
- `GET /swagger-ui.html`

## Tests

Run the normal test suite:

```powershell
mvn -pl workout-service -am test
```

Run the broker-backed Kafka relay integration test after Kafka is up:

```powershell
$env:KAFKA_INTEGRATION_TESTS="true"
mvn -pl workout-service -am verify
```

The Kafka integration test class is:

- `com.fitsync.workout.WorkoutRelayKafkaIT`
