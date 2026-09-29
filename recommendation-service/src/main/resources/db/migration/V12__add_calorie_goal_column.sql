-- A user holds a CALORIES goal alongside a WEIGHT goal, and both were written into the one
-- current-goal slot, so whichever event came last decided what the rules saw. With the weight goal
-- in the slot the daily target was estimated, and the coach quoted a target that was not the one
-- on the Nutrition page. The calorie goal gets its own column, as the protein and the
-- workout-frequency goals did in V9, and the slot is left to the weight goal.
alter table recommendation_context add column calorie_goal_kcal integer;

update recommendation_context
   set calorie_goal_kcal = cast(current_goal_target_value as integer)
 where upper(current_goal_type) = 'CALORIES'
   and current_goal_target_value >= 1;

-- The slot no longer holds calorie goals. A row that held one is marked stale so that the rules
-- are worked out again from the new column.
update recommendation_context
   set current_goal_type = null,
       current_goal_target_value = null,
       current_goal_target_unit = null,
       current_goal_target_date = null,
       stale = true
 where upper(current_goal_type) = 'CALORIES';
