-- What became of a draft once a user decided.
--
-- Accepting creates a plan in workout-service and starts a session from it. The plan is the durable
-- link and already has a column; the session is recorded too, because "a real session appeared on
-- the Workout page" is the outcome the feature exists for, and a client should be able to open it
-- from the draft without searching for it.
--
-- Rejecting is not deletion. A rejected draft with its reason is the most direct signal the
-- evaluation harness has about whether the coach proposes things worth doing, and it is kept on the
-- artifact rather than in a separate feedback table because nothing else needs a row of its own.

-- Two statements rather than one with two ADD COLUMN clauses: PostgreSQL accepts the combined
-- form, H2 (which the test profile runs on) does not, and a migration that only works on the
-- production database is exactly the kind of drift the test profile exists to catch.
alter table coach_generated_artifact add column started_session_ref uuid;
alter table coach_generated_artifact add column rejection_reason varchar(500);
