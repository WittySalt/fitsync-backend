# Event Schema Governance

## Status

Accepted for the current JSON event-contracts model.

## Scope

This document governs the Java record payloads in `event-contracts` and the JSON messages
published through FitSync Kafka topics. It intentionally does not introduce Avro, a schema
registry, or any new infrastructure.

## Compatibility Rules

Event schemas must evolve additively.

Allowed changes:

- add optional payload fields that consumers can ignore
- add new event types with a new `eventType`
- add new enum-like string values only when existing consumers treat unknown values safely
- add producer-only metadata outside the payload when consumers do not depend on it

Breaking changes:

- removing a field from an existing payload
- renaming a field
- changing a field type or meaning
- changing required envelope fields in `FitSyncDomainEvent`
- reusing an existing `eventType` for incompatible semantics

When a breaking change is required, publish a new event type and increment the envelope
`version`. Keep the old event type available until all consumers have migrated.

## Tolerant Reader Requirement

Consumers must ignore unknown JSON fields on both the event envelope and payload. This keeps
older services compatible when producers add optional fields.

The shared Kafka listener reader is configured with:

- `FAIL_ON_UNKNOWN_PROPERTIES=false`
- Java record payload binding through `FitSyncDomainEvent<T>`

Consumers that do not use the shared listener base must still use the same tolerant-reader
configuration.

## Golden Samples

Each payload type has a canonical JSON sample under:

`event-contracts/src/test/resources/contracts/`

Compatibility tests enforce:

- backward compatibility: every golden sample must still deserialize into the current record,
  and all golden fields must survive a record round trip
- forward compatibility: a sample with an added unknown field must still deserialize

If a payload field is deleted, renamed, or no longer represented by the Java record, the
golden sample round-trip test fails.

## Nutrition Topic Events

The `nutrition-events` topic carries these event types. Each has a golden sample under
`contracts/nutrition/`, and analytics-service gives each its own listener flag.

| eventType | Payload | Key | Published when | Analytics flag |
|---|---|---|---|---|
| `MealLogged` | `MealLoggedPayload` | meal id | a meal is logged | `KAFKA_MEAL_LOGGED_LISTENER_ENABLED` |
| `MealUpdated` | `MealUpdatedPayload` | meal id | a meal is changed | `KAFKA_MEAL_UPDATED_LISTENER_ENABLED` |
| `MealDeleted` | `MealDeletedPayload` | meal id | a meal is deleted | `KAFKA_MEAL_DELETED_LISTENER_ENABLED` |
| `RecipeCooked` | `RecipeCookedPayload` (`cookId`, `recipeId`, `servings`, `finishedAt`) | cook id | a recipe cook is recorded for the first time | `KAFKA_RECIPE_COOKED_LISTENER_ENABLED` |

## Notification Events

notification-service sends the emails in `Documents/architecture/email-notifications-spec.md`.
These four event types exist for it; each has a golden sample and its own listener flag there.

| eventType | Topic | Payload | Key | Published when | Notification flag |
|---|---|---|---|---|---|
| `NotificationPreferencesUpdated` | `user-events` | `NotificationPreferencesUpdatedPayload` (whole current state) | user id | the notification toggles, quiet hours, timezone or preferred training time change | `KAFKA_NOTIFICATION_PREFERENCES_UPDATED_LISTENER_ENABLED` |
| `AccountDeletionRequested` | `user-events` | `AccountDeletionRequestedPayload` (`requestedAt`, `scheduledFor`) | user id | auth-service accepts a deletion request | `KAFKA_ACCOUNT_DELETION_REQUESTED_LISTENER_ENABLED` |
| `AccountDeletionCancelled` | `user-events` | `AccountDeletionCancelledPayload` (`cancelledAt`) | user id | a pending deletion is cancelled | `KAFKA_ACCOUNT_DELETION_CANCELLED_LISTENER_ENABLED` |
| `WorkoutPlanScheduleChanged` | `workout-events` | `WorkoutPlanScheduleChangedPayload` (active plan's weekly days, empty when none) | user id | the active plan, or which plan is active, changes | `KAFKA_WORKOUT_PLAN_SCHEDULE_CHANGED_LISTENER_ENABLED` |

## Versioning

Use the `version` field on `FitSyncDomainEvent` for event-type-level compatibility milestones.
For additive payload fields, keep the same event type and version unless a consumer needs a
new interpretation contract.

## Review Checklist

Before changing an event payload:

- confirm the change is additive or create a new event type
- update or add the golden sample JSON
- run `mvn -pl event-contracts test`
- confirm Kafka consumer tests still accept unknown fields
- document any intentional version bump in the producer and consumer change notes
