# FitSync API Gateway Design

## 1. Purpose

This document defines the recommended API gateway design for the current FitSync backend.

It is intentionally aligned with the current repository reality:

- `auth-service`, `user-service`, `workout-service`, `nutrition-service`, `analytics-service`, and `recommendation-service` (the AI coach) are runnable domain services today
- `notification-service` is still an empty placeholder
- production ingress uses `nginx` for TLS, in front of the `api-gateway`
- the `api-gateway` exists and routes to all six domain services, including `recommendation-service` on port 8086

The gateway is therefore a thin edge service, not a second orchestration layer with business logic.

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

Before the gateway, the public traffic path was:

`client -> nginx -> auth-service`

Even when `user-service` was enabled, traffic was still effectively routed through the `auth-service` entry layer.
The target state below is now in place.

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
| `/api/v1/workouts/**` | `workout-service` | exercises, workout plans, workout sessions |
| `/api/v1/nutrition/**` | `nutrition-service` | meals, custom foods, food catalog, daily summary |
| `/api/v1/health` | `api-gateway` | edge health endpoint |
| `/swagger-ui.html` | `auth-service` initially | keep temporary until aggregated docs are needed |

### 5.3 Future route map

Add these when services become real (analytics and recommendations, plus the coach's `/api/v1/coach/**`, are
already routed; only notifications is still pending):

| External path | Downstream service |
|---|---|
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

`/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/bootstrap` and `/api/v1/auth/refresh` bypass the
gateway's JWT check. Bootstrap is verified by auth-service with the Cognito token, and refresh carries its refresh
token in the body.

`/api/v1/auth/me` may be validated at the gateway first and then passed to `auth-service`.

### 6.5 Revoked sessions

`RevokedSessionFilter` runs right after `JwtAuthenticationFilter`. When a verified application token has a `sid`
claim, the filter looks up `fitsync:revoked-sid:<sid>` in Redis and answers 401 with code `SESSION_REVOKED` if the
key exists. auth-service writes that key when it revokes a session.

A token without `sid` was issued before sessions existed and is accepted until it expires. When Redis cannot be
reached, the filter lets the request through, stops asking Redis for five seconds, and logs a warning at most once a
minute. Revocation is then still enforced within one access-token lifetime, because auth-service refuses the
session's next refresh. The Redis health indicator is switched off so that Redis being down does not mark the
gateway unhealthy. The gateway reads `REDIS_HOST` and `REDIS_PORT` (default `localhost:6379`).

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

### 7.1 Rate limiting as implemented

The gateway counts every request in Redis and answers `429 Too Many Requests` once a caller is over its
limit. The code lives in `com.fitsync.gateway.ratelimit`, and the filter runs inside the security chain,
after authentication and before authorization.

| Who is counted | Key in Redis | Default limit |
|---|---|---|
| An authenticated caller, on any route | `fitsync:ratelimit:general:user:<userId>` | 120 requests a minute |
| An unauthenticated call to `/api/v1/auth/**` (sign-in, bootstrap, cancelling a deletion) | `fitsync:ratelimit:auth:ip:<client address>` | 10 requests a minute |
| Any other unauthenticated call | `fitsync:ratelimit:general:ip:<client address>` | 120 requests a minute |

A caller authenticated by a Cognito token rather than an application token is counted under a SHA-256
prefix of its principal name, so no user name or email is written into Redis. The coach routes keep their
own database-backed limit of five questions an hour in recommendation-service and are under the general
limit only here. `/actuator/health` and its sub-paths (the ALB health check), CORS preflights and the async
and error dispatches that follow an already counted request are never counted.

Each counter is a fixed one-minute window. A small Lua script increments the key and sets its expiry on the
first hit in one atomic step, and returns the count with the time left in the window. A rejected request
gets the standard error body with code `RATE_LIMITED` and a `Retry-After` header holding the seconds left;
every counted response also carries `X-RateLimit-Limit` and `X-RateLimit-Remaining`, and CORS exposes all
three to the SPA.

The client address comes from `X-Forwarded-For`, read from the right. The gateway appends the address the
connection came from and then counts `trusted-proxy-hops` entries back. In the cloud CloudFront and then
the ALB each append one entry, so with two hops the gateway reads the address CloudFront saw. Anything a
client writes into the header itself sits further left and is ignored, so a client cannot choose its own
key. This relies on the gateway being reachable only through those proxies, which the security groups
enforce in prod. An entry that is not an IP literal is never used as a key.

The limiter fails open. If Redis cannot answer, the request goes through, one warning is logged, and Redis
is left alone for 30 seconds before it is tried again. Redis is not part of the gateway's health check, so a
Redis outage cannot take the gateway out of the load balancer.

| Property | Environment variable | Default |
|---|---|---|
| `fitsync.gateway.rate-limit.enabled` | `GATEWAY_RATE_LIMIT_ENABLED` | `true` (set `false` locally to switch it off) |
| `fitsync.gateway.rate-limit.general-limit` | `GATEWAY_RATE_LIMIT_GENERAL` | `120` |
| `fitsync.gateway.rate-limit.auth-limit` | `GATEWAY_RATE_LIMIT_AUTH` | `10` |
| `fitsync.gateway.rate-limit.window` | `GATEWAY_RATE_LIMIT_WINDOW` | `PT1M` |
| `fitsync.gateway.rate-limit.trusted-proxy-hops` | `GATEWAY_RATE_LIMIT_TRUSTED_PROXY_HOPS` | `0` (the prod compose file sets `2`) |
| `fitsync.gateway.rate-limit.redis-backoff` | `GATEWAY_RATE_LIMIT_REDIS_BACKOFF` | `PT30S` |
| `spring.data.redis.host` / `port` | `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` | `localhost` / `6379` (compose sets `redis`) |

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
- `workout-service`: `8082`
- `nutrition-service`: `8083`

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
- route `/api/v1/workouts/**` to `workout-service`
- route `/api/v1/nutrition/**` to `nutrition-service`
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
3. route runnable domain services through the gateway: `auth-service`, `user-service`, `workout-service`, and `nutrition-service`
4. validate JWT at the gateway, but keep authorization checks in each domain service
5. add rate limiting only to abuse-prone public auth endpoints first
6. onboard additional services only when their APIs are actually implemented

This is the lowest-risk path that improves architecture without over-engineering the platform.
