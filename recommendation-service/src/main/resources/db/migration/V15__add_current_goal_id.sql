-- The goal the current-goal slot holds. A GoalUpdated for the same goal (an edit, or the same event sent
-- again) keeps the pace baseline taken at the goal's start; only a new goal takes a new one. Before this
-- column every GoalUpdated reset the baseline to the latest weight, so the pace read 0.00 kg a week.

alter table recommendation_context add column current_goal_id uuid;
