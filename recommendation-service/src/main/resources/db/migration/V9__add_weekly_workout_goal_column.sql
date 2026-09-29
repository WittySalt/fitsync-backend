-- A user can hold a WORKOUT_FREQUENCY goal alongside a WEIGHT or CALORIES goal, but the
-- "current goal" slot on this table only ever remembers one goal at a time. Rather than
-- widen that slot, the workout-frequency target gets its own column so it survives a
-- later WEIGHT or CALORIES event instead of being overwritten by it (see the equivalent
-- protein_target_grams column, already present, for the same reasoning on nutrition).
--
-- Until now every goal event was written into the current-goal slot, so an existing row
-- may hold a WORKOUT_FREQUENCY or a PROTEIN goal there. Both values are copied into their
-- own columns so that no goal is lost. The current-goal columns are left as they are,
-- because the rules ignore a slot whose type is neither WEIGHT nor CALORIES.
alter table recommendation_context add column weekly_workout_goal_days integer;

update recommendation_context
   set weekly_workout_goal_days = cast(current_goal_target_value as integer)
 where upper(current_goal_type) = 'WORKOUT_FREQUENCY'
   and current_goal_target_value >= 1;

update recommendation_context
   set protein_target_grams = current_goal_target_value
 where upper(current_goal_type) = 'PROTEIN'
   and current_goal_target_value > 0
   and protein_target_grams is null;
