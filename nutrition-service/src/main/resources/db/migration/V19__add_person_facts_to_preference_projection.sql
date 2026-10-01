-- The Diary picks nutrient reference ranges from two derived facts that user-service publishes, the reference
-- sex (FEMALE, MALE or BLENDED) and the age band, and it reads the latest weight from the body metrics events.
-- The gender and the birth date themselves never reach this service. The weight keeps the time it was
-- measured, so that a late event for an older weigh-in does not replace a newer one.
alter table nutrition_preference_projections add column reference_sex varchar(10);
alter table nutrition_preference_projections add column age_band varchar(10);
alter table nutrition_preference_projections add column weight_kg numeric(6, 2);
alter table nutrition_preference_projections add column weight_measured_at timestamp with time zone;
