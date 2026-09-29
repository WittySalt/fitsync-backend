-- A rejected chat answer is recorded as ANSWER_REJECTED: followed by the rule it broke, for example
-- ANSWER_REJECTED:NUMBER_NOT_IN_RECORDS:1735. The column was sized for the guardrail's own verdicts,
-- the longest of which is 21 characters, so the first rejected answer could not be stored and the
-- turn failed with an error where it should have ended with the fixed sentence.
alter table coach_turn alter column guardrail_verdict type varchar(64);
