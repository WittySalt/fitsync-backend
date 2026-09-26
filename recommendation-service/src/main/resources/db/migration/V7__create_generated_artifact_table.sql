-- Things the coach produced that a user can act on: a training session today, a meal later.
--
-- Separate from coach_turn because an artifact outlives the conversation that made it. A user
-- accepts a draft, trains from it, and comes back a week later to ask why it looked like that. The
-- turn is one message in a thread; this is the object the answer is about.
--
-- The generation columns are not decoration. Whether a draft passed validation first time, needed
-- a repair round, or fell through to the rule engine is the measurement that says whether the model
-- is earning its place, and it has to be recorded as it happens because it cannot be recovered
-- afterwards.

create table coach_generated_artifact (
    artifact_id           uuid primary key,
    user_id               uuid                     not null,

    -- Both nullable: a draft can be produced from a studio surface rather than inside a chat.
    thread_id             uuid
        references coach_thread (thread_id) on delete set null,
    turn_id               uuid,

    -- SESSION_DRAFT today, MEAL_DRAFT once Meal Studio lands.
    kind                  varchar(32)              not null,
    -- DRAFT, ACCEPTED or DISCARDED. Accepting is what turns a draft into a real session.
    status                varchar(32)              not null,

    -- The validated draft, as the client renders it and the accept step reads it.
    payload               text                     not null,

    -- MODEL, MODEL_REPAIRED or RULE_FALLBACK: which path actually produced what is stored.
    source                varchar(32)              not null,
    -- How many validations the draft went through, so a repair round shows as 2.
    validation_attempts   integer                  not null,
    -- Violations from the first validation, kept even when the repair fixed them, because what the
    -- model got wrong is more informative than the version that finally passed.
    first_pass_violations text,

    -- Set on accept: the plan or session this became, so the draft and the real thing stay linked.
    accepted_ref          uuid,
    accepted_at           timestamp with time zone,

    date_time_created     timestamp with time zone not null,
    date_time_updated     timestamp with time zone not null,
    created_by            varchar(120)             not null,
    updated_by            varchar(120)             not null,
    version               bigint                   not null
);

-- The list a user sees: their own drafts of one kind, newest first.
create index idx_coach_artifact_user_kind
    on coach_generated_artifact (user_id, kind, date_time_created desc);

-- How the evaluation harness reads it: across users, by how it was produced and how it ended.
create index idx_coach_artifact_source_status
    on coach_generated_artifact (source, status);

-- Drafts made inside a conversation, for showing them in the thread they came from.
create index idx_coach_artifact_thread
    on coach_generated_artifact (thread_id);
