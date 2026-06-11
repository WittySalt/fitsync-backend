# FitSync API Gateway Design

## 1. Purpose

This document defines the recommended API gateway design for the current FitSync backend.

It is intentionally aligned with the current repository reality:

- `auth-service` and `user-service` are the only meaningful runnable domain services today
- `workout-service`, `nutrition-service`, `analytics-service`, `recommendation-service`, and `notification-service` are still scaffolds
- production ingress currently uses `nginx`

The gateway should therefore be introduced as a thin edge service, not as a second orchestration layer with business logic.

---

## 2. Design Goals

The FitSync API gateway should provide:

- one backend entry point for frontend clients
- centralized routing to domain services
- shared CORS policy
- shared request tracing and access logging
- JWT validation at the edge for protected APIs
- rate limiting for abuse-sensitive endpoints
- predictable error handling for unavailable downstream services

The gateway should not provide:

- domain business logic
- cross-service write orchestration
- distributed transaction coordination
- direct database access

---

## 3. Current-to-Target Transition

### 3.1 Current state

Today the public traffic path is:

`client -> nginx -> auth-service`

Even when `user-service` is enabled, traffic is still effectively routed through the `auth-service` entry layer.

### 3.2 Target state

The target public traffic path should become:

`client -> nginx/tls edge -> api-gateway -> domain services`

Recommended role split:

- `nginx`: TLS termination, HTTP to HTTPS redirect, basic edge hardening
- `api-gateway`: application-layer routing, auth enforcement, rate limiting, observability headers
- domain services: business logic and domain ownership

This keeps TLS and internet-facing proxy concerns separate from application routing concerns.

---

## 4. Recommended Technology Direction

Because the backend stack is already Spring Boot based, the gateway should stay inside the Spring ecosystem.

Recommended implementation direction:

- Spring Boot 3.3.x
- Spring Cloud Gateway MVC or reactive gateway, depending team preference
- Spring Security for edge JWT validation
- Actuator for health probes

Do not introduce a separate gateway platform unless there is an operational reason to do so.

For this project stage, the simplest operational model is:

- keep `nginx` for TLS and public ingress
- add one Spring-based `api-gateway` service behind it

---

## 5. Service Routing Design

### 5.1 External route conventions

Keep frontend-facing URLs versioned and stable:

- `/api/v1/auth/**`
- `/api/v1/users/**`
- `/api/v1/workouts/**`
- `/api/v1/nutrition/**`
- `/api/v1/analytics/**`
- `/api/v1/recommendations/**`
- `/api/v1/notifications/**`

### 5.2 Initial route map

Recommended initial route ownership:

| External path | Downstream service | Notes |
|---|---|---|
| `/api/v1/auth/**` | `auth-service` | register, login, me, token-related flows |
| `/api/v1/users/**` | `user-service` | profile, preferences, metrics, goals |
| `/api/v1/health` | `api-gateway` | edge health endpoint |
| `/swagger-ui.html` | `auth-service` initially | keep temporary until aggregated docs are needed |

### 5.3 Future route map

Add these when services become real:

| External path | Downstream service |
|---|---|
| `/api/v1/workouts/**` | `workout-service` |
| `/api/v1/nutrition/**` | `nutrition-service` |
| `/api/v1/analytics/**` | `analytics-service` |
| `/api/v1/recommendations/**` | `recommendation-service` |
| `/api/v1/notifications/**` | `notification-service` |

### 5.4 Path handling rule

The gateway should preserve the public API contract and avoid leaking internal service naming.

Good:

- client calls `/api/v1/users/me/profile`
- gateway forwards to `http://user-service:8081/api/v1/users/me/profile`

Avoid:

- exposing internal paths like `/user-service/api/v1/...`

---

## 6. Authentication and Authorization Design

### 6.1 Edge authentication responsibility

The gateway should validate JWTs for protected routes.

Recommended rules:

- public routes: register, login, health, docs
- protected routes: all user/workout/nutrition/analytics/recommendation/notification APIs by default

### 6.2 Authorization responsibility

Keep domain authorization inside the owning service.

Reason:

- the gateway can verify identity
- the downstream service must still verify ownership and role-based access

The gateway should never become the only authorization layer.

### 6.3 Identity propagation

After successful JWT validation, the gateway should forward normalized identity headers to downstream services.

Recommended propagated headers:

- `X-User-Id`
- `X-User-Email`
- `X-User-Roles`
- `X-Correlation-Id`

Important:

- downstream services must trust these headers only when the request comes from the gateway or internal network
- internet clients must not be allowed to spoof them

### 6.4 Auth-service special case

`/api/v1/auth/login` and `/api/v1/auth/register` should bypass JWT checks.

`/api/v1/auth/me` may be validated at the gateway first and then passed to `auth-service`.

---

## 7. Security Controls

The gateway should centralize the following edge controls:

- CORS allowlist by environment
- request size limits
- rate limiting for login and registration endpoints
- correlation id generation
- removal or overwrite of spoofable forwarding headers
- consistent `X-Forwarded-*` handling from `nginx`

Recommended initial rate-limited endpoints:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/bootstrap`

Recommended initial limits:

- conservative IP-based limits for anonymous auth endpoints
- user-based limits for authenticated expensive read endpoints later if needed

Do not place domain validation rules here. Input validation still belongs in each service.

---

## 8. Error Handling and Resilience

### 8.1 Gateway error contract

The gateway should return a stable error contract when routing or downstream failures occur.

Recommended payload:

```json
{
  "code": "DOWNSTREAM_UNAVAILABLE",
  "message": "User service is temporarily unavailable",
  "path": "/api/v1/users/me/profile",
  "timestamp": "2026-06-11T23:30:00Z",
  "correlationId": "4f6f5f65-1d9d-4ae6-a4ea-1b4b6d21c0e1"
}
```

### 8.2 Status code rules

Recommended mappings:

- `401`: missing or invalid token at gateway
- `403`: blocked by edge policy if applicable
- `404`: no route exists
- `429`: rate limit exceeded
- `502`: downstream returned invalid upstream response
- `503`: downstream unavailable or timed out

### 8.3 Timeout rules

Fail fast rather than hanging.

Recommended starting point:

- auth-service timeout: 2 to 3 seconds
- user-service timeout: 2 to 3 seconds
- analytics or recommendation reads later: 3 to 5 seconds depending on usage

Avoid automatic retries for non-idempotent requests.

---

## 9. Observability Design

The gateway should be the first place where request-level observability is normalized.

Recommended capabilities:

- generate or accept `X-Correlation-Id`
- log request method, path, response code, latency, correlation id
- expose actuator health and readiness
- include downstream target name in logs

Recommended log fields:

- timestamp
- correlation id
- client ip
- authenticated user id when present
- route id
- downstream service
- status code
- latency ms

This gives the team one place to trace cross-service requests.

---

## 10. OpenAPI and Documentation Strategy

Short term:

- keep service-local Swagger endpoints
- expose only one or two service docs while the platform is still evolving

Medium term:

- aggregate OpenAPI specs behind the gateway
- publish a frontend-facing API index page

Do not block gateway rollout on aggregated Swagger. That is an enhancement, not a prerequisite.

---

## 11. Deployment Design

### 11.1 Local development

Recommended local traffic path:

`frontend dev server -> api-gateway -> auth-service/user-service`

Suggested local ports:

- `api-gateway`: `8088`
- `auth-service`: `8080`
- `user-service`: `8081`

### 11.2 Production

Recommended production traffic path:

`internet -> nginx :443 -> api-gateway :8088 -> internal services`

The domain services should remain internal-only on the Docker network or cluster network.

### 11.3 Deployment rule

Do not expose every domain service directly to the public internet once the gateway is introduced.

That would defeat the purpose of centralized routing and security.

---

## 12. Rollout Phases

### Phase 1: thin gateway for existing services

Deliver:

- route `/api/v1/auth/**` to `auth-service`
- route `/api/v1/users/**` to `user-service`
- central CORS policy
- edge JWT validation
- request correlation id
- basic access logs

This is the right first milestone for the current repository.

### Phase 2: protection and resilience

Add:

- rate limiting for auth endpoints
- route-specific timeout configuration
- standardized gateway error payloads
- trusted forwarded header handling

### Phase 3: platform improvements

Add:

- aggregated API docs
- per-route metrics dashboards
- circuit breaker only where data proves it is needed

### Phase 4: future services

Extend routes to:

- workout
- nutrition
- analytics
- recommendation
- notification

---

## 13. What Not To Do

Avoid these mistakes:

- placing business orchestration in the gateway
- letting the gateway call databases
- making the gateway the only authorization layer
- routing all writes through multiple downstream synchronous hops
- exposing downstream services publicly after gateway rollout
- forcing Kafka-aware logic into the gateway

If the gateway starts coordinating business workflows, it becomes a distributed monolith entry point.

---

## 14. Immediate Recommendation For This Repository

For the current FitSync backend, the best gateway design is:

1. keep `nginx` as the TLS and public reverse proxy
2. implement `api-gateway` as a Spring Boot edge service
3. route only `auth-service` and `user-service` first
4. validate JWT at the gateway, but keep authorization checks in each domain service
5. add rate limiting only to abuse-prone public auth endpoints first
6. onboard additional services only when their APIs are actually implemented

This is the lowest-risk path that improves architecture without over-engineering the platform.
