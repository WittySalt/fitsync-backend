# recommendation-service — Delivery Checkpoint & Handoff

> **Status:** MVP cut line complete (except X-3, which is main-bound — see §4).
> **Branch:** `claude/recommendation-interface-review-0x4pzv` — per the iron rule in `CLAUDE.md`,
> this branch holds ALL recommendation work and (as designed) does not merge to `main` unless the
> team explicitly decides to graduate the module.
> **Verify gate at handoff:** `mvn -pl recommendation-service,api-gateway verify -Psast
> -Dspring.profiles.active=test` → BUILD SUCCESS, 69 + 27 tests, SpotBugs clean.

---

## 1. What was delivered (commit by commit)

| Commit | Backlog | What landed |
|---|---|---|
| `7565272` | **RC-1**, X-2 | Module scaffold: main class + JWT security + config, README §9 package skeleton, `recommendation_context` + `recommendation_result` tables (Flyway V1), H2 test profile, module built+tested by the CI reactor (deliberately **not** packaged as a deploy image — X-5 undecided) |
| `679ed0b` | **RC-8** | 11 thin Kafka listeners (group `recommendation-consumer-group`, all off by default via `fitsync.kafka.listeners.*`) → handlers project events into the per-user context + mark it stale; idempotent via `recommendation_processed_events` (Flyway V2); AccountDeleted purges everything |
| `21eb7cf` | **RC-2** | Local rule engine `service/rules`: R1 nutrition gap (TDEE estimate), R2 weekly adherence, R3 goal pace (baseline captured at goal-set, Flyway V3), R4 streak nudge, R5 cold-start — pure functions, facts only |
| `3cc61ac` | **RC-4/RC-5** | `RecommendationResolver` read funnel (cache fresh → rule → template), payload `{facts, narrative}`, SHA-256 context-hash change detection, `rule-result-ttl` (PT24H), prunes unfired rules; `NarrativeTemplates` — 13 hand-written templates |
| `741df94` | **RC-7/X-1** | Read API: `GET /api/v1/recommendations`, `GET /{type}` (case-insensitive, 404), `POST /refresh` (202); api-gateway `recommendation-service-base-url` (:8086) + `/api/v1/recommendations/**` proxy route (branch-only gateway change) |
| `74db57e` | **RC-6/X-4** | Live A1 weekly narrative: `ClaudeClient` seam → `anthropic-java` adapter (key via `ANTHROPIC_API_KEY`/`fromEnv()`, prompt-cached system prompt, model configured — default `claude-sonnet-4-6`) → `WeeklyNarrativeService` (async, hash-gated, P7D TTL, silent rule/template fallback). AI tier **off by default** |
| (follow-up) | **RC-6** | Provider switch for the narrative backend: `fitsync.recommendation.ai.provider` = `anthropic` (default, unchanged) or `ollama` (`OllamaClaudeClient` → local open model via `/api/chat`, no API key anywhere). `NarrativeClientConfiguration` builds the one client bean; everything downstream of the seam is provider-agnostic. Verified live against local `llama3.2:3b` |
| (follow-up) | **RC-3/RC-6/RC-9 slice** | Coach upgrade: (1) A1 facts rebuilt by `CoachFacts` — semantic groups with rule statuses (training/nutritionToday/goalPace/loggingStreak/personalRecord/foodGroupWeek), few-shot 3-sentence prompt with guardrails, previous-narrative continuity; (2) weekly food-group balance projected from `MealLogged.foodGroupCalories` with Monday-anchored rollover (Flyway V4); (3) R6 PR-celebration (new `workout-events` listener + PR snapshot, Flyway V5) and R8 logging-reminder rules + templates T14–T16; (4) A4 coach Q&A: `POST /api/v1/recommendations/coach/ask` — sync single-turn, grounded only on `CoachFacts`, per-user hourly budget (`coach-questions-per-hour`, default 5), friendly 503/429, rides the existing gateway route |

**End-to-end thread now working:** domain/analytics events → context projection (stale marking) →
rules → templates → cached `recommendation_result` → JWT-protected read API behind the gateway,
plus one genuine AI feature riding the same cache.

### Architecture in one diagram

```
Kafka topics (user/progress/nutrition/analytics events)
      │  consumer/ (11 thin listeners, recommendation-consumer-group)
      ▼
handler/ (idempotent projections)──▶ recommendation_context (per-user read model, stale flag)
                                          │
              POST /refresh / stale read  ▼
service/RecommendationResolver:  cache(fresh) → RuleEngine(R1–R5) → NarrativeTemplates ─▶ recommendation_result
                                          │                                                   ▲
                                          └── enqueue (async, hash-gated) ──▶ service/ai/WeeklyNarrativeService (A1)
      ▲
GET /api/v1/recommendations[/{type}]  ← api-gateway /api/v1/recommendations/** (X-1)
```

## 2. Design invariants (do not regress)

1. **Consumers never call AI, never compute recommendations** — they only update the context and
   flip `stale`. The single sanctioned exception: `WeeklyRecapGeneratedEventHandler` *enqueues*
   (never runs) the A1 narrative — weekly cadence, async, hash-gated.
2. **The read path never generates** beyond the ms-level rule stage, and **never errors because of
   AI**: any Claude failure logs a warning and the cached rule/template output keeps serving.
3. **Compute-in-code, model-phrases**: prompts carry rule-computed facts as JSON, never raw data.
4. **`ANTHROPIC_API_KEY` lives only in the environment** (SDK `fromEnv()`); model IDs are config.
5. **Every entity column has a Flyway migration** (`ddl-auto: validate`); migrations are at **V3**.
6. Rule results carry `context_hash` + `expires_at`; AI regeneration is skipped when the hash is
   unchanged; expired AI rows are pruned by refresh so they can't pin the cache stale.

## 3. How to run / verify

```bash
# The CI gate (same as verify-backend):
mvn -pl recommendation-service -am verify -Psast -Dspring.profiles.active=test

# Run locally (needs Postgres per application.yml; schema "recommendation"):
SERVER_PORT=8086 mvn -pl recommendation-service spring-boot:run

# Enable Kafka consumers (all off by default):  KAFKA_ENABLED + per-listener flags, e.g.
#   KAFKA_WEEKLY_RECAP_GENERATED_LISTENER_ENABLED=true ...
# Enable the AI tier (off by default):
#   RECOMMENDATION_AI_ENABLED=true + ANTHROPIC_API_KEY=sk-ant-...   (fails fast without the key)
#   Optional: RECOMMENDATION_AI_MODEL / _MAX_TOKENS / _NARRATIVE_TTL
# Or run the narrative on a local open model instead (no key, zero cost — e.g. for demos):
#   ollama pull llama3.2:3b, then
#   RECOMMENDATION_AI_ENABLED=true RECOMMENDATION_AI_PROVIDER=ollama \
#     RECOMMENDATION_AI_MODEL=llama3.2:3b   (RECOMMENDATION_AI_BASE_URL defaults to :11434)
```

## 4. What is NOT done (the honest gap list)

### Missing from the MVP cut line
| Item | Why it's not here | What to do |
|---|---|---|
| **X-3** OFF attribution (ODbL) | **Main-bound** — touches nutrition/frontend surfaces, not this branch | Separate branch from `main` → PR → merge (per CLAUDE.md merge flow) |
| **FD-1** OFF import validation | Owner's task — needs the real DB (`data-validation-runbook.md`) | Run the runbook against the production snapshot |

### Known limitations inside the delivered scope
- **Meal edits/deletes only mark stale** — `MealDeleted` carries no reversible delta, so the
  running today-totals can drift after an edit until the next `MealLogged`/day rollover. The clean
  fix is consuming an analytics daily-nutrition fact instead (see `analytics-collaboration.md` §3).
- **TDEE uses neutral age/sex constants** (events carry neither — by design, PII). Precise targets
  need EV-3 (`EnergyProfileComputed` from user-service) — the #1-ranked enrichment.
- **R3 pace baseline starts at goal-set time**; goals predating the branch's deploy have no
  baseline until the next `GoalUpdated` or first `BodyMetricsRecorded`.
- **Resolver upsert races** under concurrent refresh of the same user are resolved by the
  `(user_id, type)` unique constraint + optimistic version (a loser may see a transient conflict);
  acceptable at MVP traffic, revisit if refresh becomes hot.
- **No deploy image / compose entry** — X-5 (monolith-first vs standalone) is deliberately open;
  CI builds and tests the module but does not ship it.
- **A4 coach Q&A is deliberately synchronous** (the user asked and is watching a spinner) — the
  one sanctioned exception to invariant 2's "read path never generates". Its rate-limit window is
  in-memory per instance; fine single-instance, needs a shared store if the service scales out.
- **R7 (focus-area balance) remains blocked on EV-2**; A2/A3/A5 are still open (RC-9).

### Full-scope backlog (documented growth path, untouched)
`RC-3` (rules R6–R8) · `RC-9` (A2/A3/A5) · `RC-10` (retrieval, cascade, Batch API, distillation) ·
`FD-5…FD-11` · `EV-2/EV-3/EV-4` · `AN-0/AN-2/AN-3/AN-5` · `X-5` — statuses and dependencies in
`BACKLOG.md`.

## 5. Checkpoint for the next session

- **Branch/HEAD:** `claude/recommendation-interface-review-0x4pzv` @ `74db57e` (pushed).
- **Read first:** `CLAUDE.md` (iron rule + conventions) → `BACKLOG.md` (statuses) → this file.
- **Flyway:** V5. **Tests:** 96 (recommendation) + 27 (gateway). **SpotBugs:** clean (exclusions
  in root `spotbugs-exclude.xml` are per-class with justifications).
- **Sensible next moves,** in value order:
  1. X-3 via a main-bound PR (small, completes the MVP list).
  2. EV-3 `EnergyProfileComputed` (main-bound, unlocks exact R1 targets, PII-clean).
  3. Feed R1/R3 facts into the A1 prompt; consider A5 rationale as a cheap second AI surface.
  4. RC-3 rules once EV-2 lands.
- **If the team decides to graduate the module to `main`:** squash-review this branch, drop the
  planning docs or move them to `docs/`, add the service to `RUNNABLE_SERVICES`/compose/Terraform
  (X-5 decision), and only then open the PR — do not merge casually; the branch was built under
  the assumption it stays isolated.
