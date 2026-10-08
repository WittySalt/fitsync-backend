# FitSync Backend Kafka Event Architecture

> **Status:** planning document written in June 2026, before most services were built. Its
> "current state" and phase sections are historical; for what exists today see
> [README.md](README.md) and the code.

## 1. Purpose

This document defines the recommended Kafka and event-driven design for the FitSync backend.

It covers:

- why Kafka should be introduced
- which domains should publish events
- which domains should consume events
- where notification fits in the event model
- event contract design
- topic strategy
- consumer safety and reliability rules
- phased rollout guidance

This document complements:

- [backend.md](backend.md)
- [backend-api-communication.md](backend-api-communication.md)

---

## 2. Role of Kafka in FitSync

Kafka should be introduced as a **secondary communication backbone** for asynchronous workflows.

Kafka should not control the primary success path of:

- register
- login
- profile update
- workout CRUD
- meal CRUD
- goal management

Kafka should support:

- analytics recomputation
- recommendation refresh
- notifications
- activity history
- audit-style side effects

Important clarification:

- Kafka does not require a notification service in order to send or receive events
- Kafka becomes useful as soon as one producer publishes and one consumer subscribes
- in FitSync, `analytics-service` and `recommendation-service` are already valid consumers
- `notification-service` is an additional consumer role, not the reason Kafka exists

Core rule:

- primary business writes succeed or fail based on the owning domain transaction, not on downstream event consumers

---

## 3. Event-Driven Design Goals

The event architecture should achieve:

- loose coupling between producer and consumer domains
- support for derived and fan-out workflows
- gradual migration toward selective service extraction
- eventual consistency for non-critical follow-up processing
- operational safety through idempotency and retry design

---

## 4. Recommended Scope

Kafka should be introduced gradually.

### 4.1 Initial Producer Domains

- `user`
- `workout`
- `nutrition`

### 4.2 Initial Consumer Domains

- `analytics`
- `recommendation`
- `notification`

### 4.3 Good First Event Candidates

- `WorkoutLogged`
- `MealLogged`
- `GoalUpdated`

Secondary event candidates:

- `UserRegistered`
- `UserProfileUpdated`
- `WorkoutUpdated`
- `MealUpdated`
- `WeightRecorded`

---

## 5. Event Flow Model

Recommended flow:

1. client sends API request
2. controller validates request
3. domain service performs business logic
4. owning domain writes to database
5. transaction commits
6. domain event is created
7. event publisher emits to Kafka
8. consumers process the event
9. analytics/recommendation/notification update derived state

Key rule:

- event publication must follow successful state change

Avoid:

- publishing events before commit
- requiring consumers to succeed before returning API success

---

## 6. Topic Strategy

Start with a small number of topics.

Recommended topic layout:

- `user-events`
- `workout-events`
- `nutrition-events`
- `progress-events`

Suggested ownership:

- `user-events`: registration and profile events
- `workout-events`: workout log and workout update events
- `nutrition-events`: meal log and nutrition update events
- `progress-events`: goal and weight-related events

Alternative simpler option:

- `fitsync-domain-events`

Recommendation:

- if the team wants clear ownership, use domain-specific topics
- if the team wants minimum operational surface, use one topic with typed envelopes

For this project, domain-specific topics are the better default because they make producer ownership clearer.

---

## 7. Event Envelope Design

All events should follow a consistent envelope.

Recommended structure:

```json
{
  "eventId": "8d2c7b2f-3a2d-4d86-b8b9-0f2e8cb24b91",
  "eventType": "WorkoutLogged",
  "version": 1,
  "occurredAt": "2026-06-11T20:15:00Z",
  "source": "workout",
  "userId": 123,
  "payload": {
    "workoutId": 456,
    "durationMinutes": 45,
    "caloriesBurned": 350,
    "completedAt": "2026-06-11T20:10:00Z"
  }
}
```

Required fields:

- `eventId`
- `eventType`
- `version`
- `occurredAt`
- `source`
- `userId`
- `payload`

Design rules:

- never publish raw JPA entities
- keep payloads minimal
- include only consumer-relevant fields
- version every event contract
- preserve backward compatibility where possible

---

## 8. Recommended Event Definitions

### 8.1 WorkoutLogged

Producer:

- `workout`

Typical payload:

```json
{
  "workoutId": 456,
  "planId": 1001,
  "durationMinutes": 45,
  "caloriesBurned": 350,
  "completedAt": "2026-06-11T20:10:00Z"
}
```

Consumers:

- `analytics`
- `recommendation`

### 8.2 MealLogged

Producer:

- `nutrition`

Typical payload:

```json
{
  "mealId": 789,
  "mealType": "DINNER",
  "calories": 620,
  "proteinGrams": 42,
  "carbsGrams": 55,
  "fatGrams": 21,
  "loggedAt": "2026-06-11T19:45:00Z"
}
```

Consumers:

- `analytics`
- `recommendation`

### 8.3 GoalUpdated

Producer:

- `user` or goal-owning domain

Typical payload:

```json
{
  "goalType": "WEIGHT_LOSS",
  "targetWeight": 72.0,
  "targetCalories": 2100,
  "updatedAt": "2026-06-11T20:20:00Z"
}
```

Consumers:

- `recommendation`
- `analytics`

### 8.4 WeightRecorded

Producer:

- `user` or progress-owning domain

Consumers:

- `analytics`
- `recommendation`

### 8.5 UserRegistered

Producer:

- `auth`

Consumers:

- `analytics`
- `notification`
- future onboarding workflows

### 8.6 NotificationTriggered

Producer:

- `notification` or a domain service if explicit notification events are needed later

Consumers:

- `notification`

Note:

- this is not required in the first event batch
- the notification service can directly consume business domain events such as `WorkoutLogged`, `GoalUpdated`, or `WeightRecorded`

---

## 9. Producer Design

Producer responsibilities:

- publish only after a successful domain transaction
- construct valid event envelopes
- include stable identifiers
- avoid leaking internal persistence details

Recommended abstraction:

```text
messaging/publisher/
|-- DomainEventPublisher.java
|-- InProcessEventPublisher.java
`-- KafkaDomainEventPublisher.java
```

Recommended rollout:

1. define the publisher interface
2. publish in-process Spring events first
3. later add Kafka-backed publisher implementation

This keeps business code stable while transport evolves.

---

## 10. Consumer Design

Consumers should be narrow and resilient.

Consumer responsibilities:

- deserialize event safely
- validate event envelope and payload
- route event to the correct domain handler
- execute derived processing
- record enough context for observability and retries

Recommended consumer structure:

```text
messaging/
|-- consumer/
|   |-- AnalyticsEventConsumer.java
|   |-- RecommendationEventConsumer.java
|   `-- NotificationEventConsumer.java
`-- handler/
    |-- AnalyticsEventHandler.java
    |-- RecommendationEventHandler.java
    `-- NotificationEventHandler.java
```

Rule:

- transport-facing consumer classes should stay thin
- business logic belongs in handlers/services

---

## 11. Consumer Group Strategy

Consumer groups should map to business responsibilities.

Suggested group layout:

- `analytics-consumer-group`
- `recommendation-consumer-group`
- `notification-consumer-group`

Meaning:

- each group gets its own full copy of the relevant events
- each business capability scales independently

Do not place unrelated business concerns in one shared consumer group.

---

## 12. Partitioning and Ordering

Ordering in Kafka is only guaranteed within a partition.

Recommended partitioning key:

- `userId`

Why:

- many FitSync workflows are user-centric
- it improves per-user event ordering
- it reduces cross-user coupling assumptions

Design rule:

- never assume total ordering across all users or all event types

Consumers must tolerate:

- replay
- delayed arrival
- duplicate messages

---

## 13. Reliability and Safety

### 13.1 Idempotency

Consumers must be idempotent.

Recommended options:

- store processed `eventId`
- use natural upsert logic
- design read model updates to be safely repeatable

### 13.2 Retries

Use retries only for transient failures.

Examples:

- temporary database issue
- temporary downstream integration issue

Do not retry forever on deterministic payload or validation errors.

### 13.3 Dead-Letter Handling

Kafka listeners use the shared `web-support` error handler for retry and dead-letter behavior.

Current policy:

- transient listener failures use `FixedBackOff(2000ms, 3)` before recovery
- deterministic payload and conversion failures are classified as not retryable
- recovered records are published to `<source-topic>.DLT` on the same partition
- the existing structured failure log is emitted before dead-letter publication
- when no `KafkaOperations` bean exists, the shared handler falls back to log-only recovery

The DLT record keeps Spring Kafka's default dead-letter headers, including source topic,
partition, offset, consumer group, and exception details. Local development relies on broker
topic auto-creation for `.DLT` topics; deployment-specific topic provisioning can be added
separately when broker policy requires explicit topics.

### 13.4 Observability

Need:

- correlation-friendly logging
- event ID logging
- consumer error tracing
- topic and consumer lag visibility

---

## 14. Schema Evolution Rules

Events will evolve over time.

Required rules:

- include a `version` field
- prefer additive changes over breaking changes
- do not silently repurpose existing field meanings
- treat old and new consumers as coexistence scenarios

Good practice:

- keep event names stable
- introduce `WorkoutLogged v2` through versioning, not through informal payload drift

---

## 15. Security and Data Minimization

Kafka events should carry only necessary data.

Rules:

- do not publish password data
- do not publish secrets or tokens
- minimize PII in events
- avoid excessive user profile duplication
- validate incoming event shape before processing

Logging rule:

- log event metadata by default
- avoid logging full sensitive payloads

---

## 16. Tooling and Infrastructure

Recommended Kafka-related tooling when implementation begins:

- Spring for Apache Kafka
- Kafka broker in Docker Compose
- Kafka integration tests via Testcontainers
- Kafka topic inspection UI
- structured logging
- health checks for Kafka producer/consumer connectivity

Keep Kafka infrastructure out of the baseline local setup until real event-driven implementation starts.

---

## 17. Testing Strategy

Kafka-related testing should include:

- event serialization tests
- event deserialization tests
- producer integration tests
- consumer integration tests
- idempotency tests
- dead-letter behavior tests, including `<topic>.DLT` publication and log-only fallback
- replay handling tests

Preferred approach:

- unit tests for mapping and handler logic
- Testcontainers-based integration tests for broker-backed flows

---

## 18. Recommended Rollout Plan

### Phase 1

- define event envelope
- define event model classes
- define publisher abstraction

### Phase 2

- publish internal in-process domain events
- implement analytics, recommendation, and notification handlers inside the monolith

### Phase 3

- add Kafka configuration
- add Kafka producer implementation
- route selected events to Kafka

### Phase 4

- add Kafka consumers for analytics/recommendation/notification
- introduce retries and dead-letter handling

### Phase 5

- extract analytics/recommendation only if scaling or deployment needs justify it

---

## 19. Immediate Practical Recommendation

For this repository, the best starting point is:

1. define `WorkoutLogged`, `MealLogged`, and `GoalUpdated`
2. standardize one event envelope
3. publish events only after successful writes
4. keep analytics, recommendation, and notification as async consumers
5. avoid putting core request success behind Kafka

---

## 20. Final Recommendation

For FitSync, Kafka should be treated as an asynchronous extension layer.

Use it for:

- derived computations
- fan-out side effects
- decoupled recommendation refresh
- analytics read model updates
- notification dispatch

Do not use it as a replacement for synchronous core business APIs.
