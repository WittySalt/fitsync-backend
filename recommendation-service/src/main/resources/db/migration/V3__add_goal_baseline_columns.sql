-- RC-2 (R3 goal pace): the context keeps only the latest weight, so pace needs a
-- baseline — the weight (and date) when the current weight goal was set. Captured by
-- the GoalUpdated handler (from the latest known weight) or, if no weight was known
-- yet, by the first BodyMetricsRecorded after the goal.

alter table recommendation_context add column goal_baseline_weight_kg numeric(6, 2);
alter table recommendation_context add column goal_baseline_on date;
