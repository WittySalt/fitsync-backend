-- Coach conversation spine (platform design §5).
--
-- Three tables, and the reason they are separate is worth stating. A thread is the unit a user
-- sees and manages. A turn is the unit the context window is built from, and it carries the
-- per-answer telemetry — which tier answered, which model, how many tokens, how long — because
-- that telemetry is rendered in the product as the trace view, not just collected. A tool
-- invocation is a child of a turn rather than a column on it, because one turn can call several
-- tools and each needs its own timing and outcome.
--
-- Nothing here calls a model. This migration is the plumbing the orchestrator will sit on.

create table coach_thread (
    thread_id          uuid primary key,
    user_id            uuid                     not null,
    title              varchar(200),
    archived           boolean                  not null default false,
    last_active_at     timestamp with time zone not null,
    date_time_created  timestamp with time zone not null,
    date_time_updated  timestamp with time zone not null,
    created_by         varchar(120)             not null,
    updated_by         varchar(120)             not null,
    version            bigint                   not null
);

-- The thread list is always "mine, newest first, excluding archived".
create index idx_coach_thread_user_active
    on coach_thread (user_id, archived, last_active_at desc);

create table coach_turn (
    turn_id            uuid primary key,
    thread_id          uuid                     not null
        references coach_thread (thread_id) on delete cascade,
    seq                integer                  not null,
    role               varchar(20)              not null,
    content            text                     not null,

    -- Telemetry. Null on a USER turn; populated on an ASSISTANT turn.
    -- tier 0 rule · 1 template · 2 local model · 3 frontier model.
    tier               integer,
    model              varchar(80),
    prompt_tokens      integer,
    completion_tokens  integer,
    latency_ms         integer,
    guardrail_verdict  varchar(30),

    date_time_created  timestamp with time zone not null,
    date_time_updated  timestamp with time zone not null,
    created_by         varchar(120)             not null,
    updated_by         varchar(120)             not null,
    version            bigint                   not null,

    constraint uq_coach_turn_thread_seq unique (thread_id, seq)
);

-- Replaying a thread reads its turns in order; the unique constraint above already covers it,
-- but the explicit index keeps the ordering read cheap when a thread grows long.
create index idx_coach_turn_thread_seq on coach_turn (thread_id, seq);

create table tool_invocation (
    invocation_id      uuid primary key,
    turn_id            uuid                     not null
        references coach_turn (turn_id) on delete cascade,
    seq                integer                  not null,
    tool               varchar(60)              not null,
    args_json          text,
    -- A summary, not the payload. A catalogue search can return twenty rows and none of that
    -- belongs in an audit table; what matters is what was asked and what came back in outline.
    result_summary     varchar(500),
    latency_ms         integer                  not null,
    ok                 boolean                  not null,
    error              varchar(500),

    date_time_created  timestamp with time zone not null,
    date_time_updated  timestamp with time zone not null,
    created_by         varchar(120)             not null,
    updated_by         varchar(120)             not null,
    version            bigint                   not null,

    constraint uq_tool_invocation_turn_seq unique (turn_id, seq)
);

create index idx_tool_invocation_turn on tool_invocation (turn_id, seq);
