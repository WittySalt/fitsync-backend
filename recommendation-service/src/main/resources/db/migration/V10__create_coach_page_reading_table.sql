-- The cached "reading" behind a page's insight panel: a short model-written observation over
-- facts the app already computed, kept next to the hash of the facts it was written from.
--
-- One row per user and per page, not one row per attempt. A page's facts change every time the
-- underlying period does, and what a client needs is "the current reading for this page", not a
-- log of every one there ever was; the unique constraint on (user_id, surface) is what makes a new
-- hash overwrite the row rather than add to it.
--
-- The outcome and the hash together are what stop the service from re-asking the model for facts
-- it already failed on. A REJECTED row for a given hash is remembered for an hour, so a user
-- refreshing the page does not silently re-run a prompt that just failed the safety and number
-- checks; an ACCEPTED row is remembered for a week, matching how long the underlying week of
-- training stays the same story.

create table coach_page_reading (
    id                 uuid                     primary key,
    user_id            uuid                     not null,
    surface            varchar(20)              not null,
    facts_hash         varchar(64)              not null,
    outcome            varchar(20)              not null,

    -- Set only once the outcome is ACCEPTED.
    headline           varchar(200),
    body               varchar(800),
    -- Comma-separated fact numbers the body cited, e.g. "1,2".
    citations          varchar(40),
    source             varchar(20),

    started_at         timestamp with time zone not null,
    generated_at       timestamp with time zone,
    expires_at         timestamp with time zone,

    date_time_created  timestamp with time zone not null,
    date_time_updated  timestamp with time zone not null,
    created_by         varchar(120)             not null,
    updated_by         varchar(120)             not null,
    version            bigint                   not null,

    constraint uq_coach_page_reading_user_surface unique (user_id, surface)
);

-- How AccountDeletedEventHandler purges a user's readings.
create index idx_coach_page_reading_user
    on coach_page_reading (user_id);
