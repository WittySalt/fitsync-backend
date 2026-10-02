-- Energy this week (contract section 11): every EnergyProfileComputed event is kept as history so
-- the BMR of a past day can be found, meals remember their type for the breakfast/lunch/dinner/snack
-- split, and a workout day remembers how many of its calories were estimated by the server.

create table analytics_user_energy_profiles (
    event_id uuid primary key,
    user_id uuid not null,
    bmr_kcal integer not null,
    estimated boolean not null default false,
    computed_at timestamp with time zone not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_analytics_user_energy_profiles_user_computed
    on analytics_user_energy_profiles (user_id, computed_at desc);

-- Existing rows stay null and count as "other" in the split.
alter table analytics_meal_contributions
    add column meal_type varchar(20);

alter table analytics_workout_contributions
    add column calories_estimated boolean not null default false;

alter table analytics_user_daily_workout
    add column estimated_calories_burned integer not null default 0;
