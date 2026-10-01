-- The Diary reads the person's sex, birth date and latest weight from events, so the projection that already
-- holds the dietary preference gets the three facts as well. The weight keeps the time it was measured, so that
-- a late event for an older weigh-in does not replace a newer one.
alter table nutrition_preference_projections add column sex varchar(30);
alter table nutrition_preference_projections add column birth_date date;
alter table nutrition_preference_projections add column weight_kg numeric(6, 2);
alter table nutrition_preference_projections add column weight_measured_at timestamp with time zone;
