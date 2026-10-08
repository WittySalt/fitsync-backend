-- Lets the outbox relay retry a FAILED event with backoff instead of dropping it
-- (com.fitsync.persistence.outbox.OutboxRetryPolicy).
alter table outbox_events add column attempt_count integer not null default 0;
alter table outbox_events add column next_attempt_at timestamp with time zone;

-- Events that failed before retries existed may be weeks old; replaying them now could apply a stale
-- update over a newer one. Mark them as out of attempts (OutboxRetryPolicy.MAX_ATTEMPTS) so they stay
-- FAILED for a person to look at.
update outbox_events set attempt_count = 10 where status = 'FAILED';
