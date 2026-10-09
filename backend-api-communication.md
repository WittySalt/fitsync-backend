# FitSync Backend API Communication Design

> **Status:** planning document written in June 2026, before most services were built. Its
> "current state" and phase sections are historical; for what exists today see
> [README.md](README.md) and the code.

## 1. Purpose

This document defines how synchronous API communication should work inside the FitSync backend architecture.

It covers:

- when API-based communication should be used
- which flows must remain synchronous
- request/response design expectations
- error handling, timeout, security, and idempotency rules

This document complements:

- [backend.md](backend.md)
- [backend-kafka-event-architecture.md](backend-kafka-event-architecture.md)

---

## 2. Role of API Communication

API communication is the preferred model for **primary business flows**.

Use synchronous API calls when:

- the caller needs immediate success or failure
- the operation is part of a primary transaction
- the operation requires direct authorization checks
- the response data is needed immediately by the caller
- the user experience depends on immediate confirmation

For the current project stage, the main API boundary is still:

- frontend -> backend REST API

In the future, if some domains are extracted as services, the same principles should apply to service-to-service API calls.

---

## 3. What Must Stay Synchronous

The following use cases should remain API-driven:

- register
- login
- update profile
- create workout
- update workout
- delete workout
- create meal
- update meal
- delete meal
- create or update goals
- fetch user-facing current state

Reasons:

- they are core user actions
- they need validation and authorization before commit
- they need immediate error reporting
- they should not depend on asynchronous infrastructure

Important rule:

- a request should never require Kafka success in order to return `200`, `201`, or `204`

---

## 4. Communication Boundaries

### 4.1 Current Stage

Within the current modular monolith:

- controllers call domain services
- domain services may call internal domain interfaces
- one domain must not directly depend on another domain's repository

Preferred boundary:

- `controller -> service -> repository`

Cross-domain rule:

- if `recommendation` needs data from `user`, it should use a `user` service contract, not `user` repository internals

### 4.2 Future Extracted Services

If services are extracted later, likely synchronous paths include:

- API gateway -> auth
- API gateway -> user
- API gateway -> workout
- API gateway -> nutrition
- API gateway -> notification only for notification preference reads if needed
- recommendation -> user/workout/nutrition for point-in-time reads when needed

Do not design these as distributed transactions.

---

## 5. API Design Principles

### 5.1 Resource-Oriented Design

Use resource-oriented REST design for domain APIs.

Examples:

- `/api/v1/auth/register`
- `/api/v1/auth/login`
- `/api/v1/users/{id}`
- `/api/v1/workouts`
- `/api/v1/workouts/{id}`
- `/api/v1/meals`
- `/api/v1/goals`

### 5.1.1 Session endpoints (auth-service)

All paths are under `/api/v1/auth`. Errors use the standard `ApiError` body; the session errors carry `code`.

| Method and path | Auth | Success | Notes |
|---|---|---|---|
| `POST /bootstrap` | Cognito ID token | 200 | Returns the account fields as before plus `refreshToken`, `accessTokenExpiresAt` (ISO instant) and `sessionId`. Optional header `X-Device-Label` (sanitised, at most 60 characters); otherwise the label comes from the User-Agent, for example "Chrome on Windows". The IP address is not stored. |
| `POST /refresh` | none | 200 | Body `{"refreshToken": "..."}`. Returns `{accessToken, refreshToken, accessTokenExpiresAt, sessionId}`; the refresh token is new on every call. 401 `SESSION_EXPIRED` for an unknown or expired token, 401 `SESSION_REVOKED` for a revoked session or a reused (already rotated) token, which also revokes the session. A rotated token presented within 10 seconds of its rotation answers 401 `SESSION_EXPIRED` and leaves the session alive. |
| `POST /logout` | access token | 204 | Revokes the session named by the token's `sid`. |
| `GET /sessions` | access token | 200 | `{"sessions": [{sessionId, deviceLabel, createdAt, lastSeenAt, current}]}`, active sessions only, the most recently used first. |
| `DELETE /sessions/{sessionId}` | access token | 204 | Revokes one of the caller's sessions; someone else's id, an unknown id or a malformed id answers 404. Revoking the current one is a logout. |
| `DELETE /sessions?scope=others` | access token | 204 | Revokes every session of the caller except the current one; the count is in header `X-Revoked-Count`. Any other `scope` answers 400. |

The access token lives 15 minutes by default, so clients refresh shortly before `accessTokenExpiresAt`. Two tabs
should not refresh with the same refresh token at the same time. Browser clients should run refresh behind a
cross-tab lock (for example the Web Locks API), read the stored token again once they hold the lock, and share the
result. A duplicate within 10 seconds of a rotation is only refused; after that it counts as reuse and signs the
device out.

### 5.2 Clear DTO Boundaries

Use dedicated DTOs for:

- request validation
- response shape
- internal service contracts where needed

Do not expose persistence entities directly in API responses.

### 5.3 Validation at Entry

All incoming payloads must be validated at the controller boundary.

Examples:

- required fields
- field lengths
- format validation
- numeric ranges
- semantic constraints where appropriate

### 5.4 Thin Controllers

Controllers should:

- parse input
- trigger validation
- delegate to services
- map result to response

Controllers should not:

- implement business rules
- orchestrate complex cross-domain logic
- access repositories directly

---

## 6. Error Handling Design

All APIs should use consistent error responses.

Recommended rules:

- use centralized exception handling
- return meaningful HTTP status codes
- avoid leaking internal stack traces
- return stable machine-readable error formats

Typical mappings:

- `400 Bad Request`: invalid input
- `401 Unauthorized`: unauthenticated
- `403 Forbidden`: authenticated but not allowed
- `404 Not Found`: resource missing
- `409 Conflict`: business conflict
- `422 Unprocessable Entity`: semantically invalid business input if used by the team
- `500 Internal Server Error`: unexpected server failure

Recommended error payload shape:

```json
{
  "code": "CONFLICT",
  "message": "Email is already registered",
  "path": "/api/v1/auth/register",
  "timestamp": "2026-06-11T20:30:00Z"
}
```

---

## 7. Timeout and Retry Rules

### 7.1 Current Monolith

Inside the current monolith:

- avoid unnecessary retry logic between internal layers
- fail fast on invalid input
- keep business operations transactional where needed

### 7.2 Future External API Calls

If domains are later extracted into separate services:

- use explicit timeouts
- do not use infinite retries
- prefer bounded retries only for transient failures
- distinguish business failure from transport failure

Recommended principle:

- authentication and CRUD APIs should degrade predictably, not hang waiting for downstream systems

---

## 8. Idempotency Rules

Some API operations should be idempotent by design.

Examples:

- repeated `PUT` for the same resource state
- safe delete handling
- retried request submission with client-generated idempotency key for sensitive create flows if later needed

Practical guidance:

- prefer `PUT` where full replacement semantics are natural
- use unique constraints to guard against duplicate creation where applicable
- ensure retried client requests do not create inconsistent duplicate state

---

## 9. Security Rules

API communication must preserve strict security boundaries.

Requirements:

- validate and authenticate every protected request
- perform authorization in a domain-appropriate way
- never trust client-supplied role or ownership fields
- do not leak sensitive internals in error responses
- avoid returning secrets or password-related data

For future internal service APIs:

- propagate authenticated identity carefully
- avoid bypass channels that skip authorization checks
- keep auth and user identity handling centralized

---

## 10. Read vs Write Design

Separate synchronous read concerns from write concerns.

Write APIs should:

- validate
- authorize
- write to core domain tables
- optionally trigger follow-up events after commit

Read APIs should:

- return user-facing current state
- read from core tables or derived read models as appropriate

Guidance:

- if a response must reflect the latest committed write immediately, serve it from synchronous state
- if a dashboard can tolerate slight lag, it may later read from analytics-derived models

---

## 11. API Communication and Events

API calls and Kafka events should complement each other.

Recommended pattern:

1. API request arrives
2. synchronous validation and authorization happen
3. core data is written in the owning domain
4. success response is determined without waiting for Kafka consumers
5. post-commit event is published for async processing

This preserves:

- user responsiveness
- simpler failure semantics
- clear ownership of transactional state

---

## 12. Testing Expectations

Synchronous API design should be covered by:

- controller tests
- service tests
- repository integration tests
- security tests
- validation tests

Important cases:

- invalid request payloads
- unauthorized access
- forbidden access
- duplicate entity creation
- successful writes
- error contract consistency

---

## 13. Recommended Immediate API Priorities

For the current repository, the most important API design priorities are:

1. keep auth flows clean and synchronous
2. define future CRUD endpoint patterns for `user`, `workout`, and `nutrition`
3. standardize validation and error response structure
4. keep domain services as the main orchestration layer
5. ensure future event publication happens after successful writes, not before

---

## 14. Final Recommendation

For FitSync, synchronous API communication should remain the backbone of the backend.

Use it for:

- user-facing core operations
- strongly consistent writes
- immediate reads
- authenticated transactional workflows

Do not replace these flows with Kafka.

Kafka should extend the backend, not define its primary control path.

Notification delivery should normally remain event-driven rather than a synchronous dependency of user-facing write requests.
