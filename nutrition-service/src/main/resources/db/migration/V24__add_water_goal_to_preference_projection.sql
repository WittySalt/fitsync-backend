-- The daily water goal set on Me, carried by UserPreferencesUpdated.waterGoalMl (an additive field). Null
-- until user-service publishes or republishes the preferences, which means "no goal known".
alter table nutrition_preference_projections add column water_goal_ml integer;
