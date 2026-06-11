create table code_sets (
    code_set_id uuid primary key,
    code_set_key varchar(80) not null,
    enum_class_name varchar(180) not null,
    display_name varchar(120) not null,
    description varchar(500),
    date_effective_from date,
    date_effective_to date,
    active boolean not null default true,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint uk_code_sets_key unique (code_set_key)
);

create table code_values (
    code_value_id uuid primary key,
    code_set_key varchar(80) not null,
    enum_name varchar(80) not null,
    code varchar(40) not null,
    display_name varchar(120) not null,
    description varchar(500),
    sort_order integer not null,
    metadata_json text,
    date_effective_from date,
    date_effective_to date,
    active boolean not null default true,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_code_values_code_set foreign key (code_set_key) references code_sets (code_set_key),
    constraint uk_code_values_code unique (code),
    constraint uk_code_values_set_enum unique (code_set_key, enum_name)
);

create index idx_code_values_set_active_sort
    on code_values (code_set_key, active, sort_order);

create table user_profiles (
    user_id uuid primary key,
    display_name varchar(80) not null,
    first_name varchar(80),
    last_name varchar(80),
    avatar_url varchar(255),
    birth_date date,
    gender varchar(30),
    height_cm numeric(6, 2),
    activity_level varchar(30),
    timezone varchar(80),
    bio varchar(300),
    onboarding_completed boolean not null default false,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create table user_app_preferences (
    user_id uuid primary key,
    preferred_units_code varchar(40) not null,
    language_code varchar(40) not null,
    timezone varchar(80),
    privacy_level_code varchar(40) not null,
    data_sharing_consent boolean not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_user_app_pref_units foreign key (preferred_units_code) references code_values (code),
    constraint fk_user_app_pref_language foreign key (language_code) references code_values (code),
    constraint fk_user_app_pref_privacy foreign key (privacy_level_code) references code_values (code)
);

create table user_training_preferences (
    user_id uuid primary key,
    fitness_level_code varchar(40) not null,
    training_location_code varchar(40) not null,
    weekly_workout_days integer,
    preferred_duration_minutes integer,
    preferred_workout_time time,
    intensity_preference_code varchar(40) not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_user_training_pref_fitness foreign key (fitness_level_code) references code_values (code),
    constraint fk_user_training_pref_location foreign key (training_location_code) references code_values (code),
    constraint fk_user_training_pref_intensity foreign key (intensity_preference_code) references code_values (code)
);

create table user_nutrition_preferences (
    user_id uuid primary key,
    dietary_pattern_code varchar(40) not null,
    meal_frequency integer,
    calorie_tracking_mode_code varchar(40) not null,
    macro_preference_code varchar(40) not null,
    water_goal_ml integer,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_user_nutrition_pref_diet foreign key (dietary_pattern_code) references code_values (code),
    constraint fk_user_nutrition_pref_calorie foreign key (calorie_tracking_mode_code) references code_values (code),
    constraint fk_user_nutrition_pref_macro foreign key (macro_preference_code) references code_values (code)
);

create table user_notification_preferences (
    user_id uuid primary key,
    push_enabled boolean not null,
    email_enabled boolean not null,
    workout_reminder_enabled boolean not null,
    meal_reminder_enabled boolean not null,
    weigh_in_reminder_enabled boolean not null,
    goal_progress_reminder_enabled boolean not null,
    marketing_emails_enabled boolean not null,
    quiet_hours_start time,
    quiet_hours_end time,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create table body_metric_entries (
    metric_id uuid primary key,
    user_id uuid not null,
    weight_kg numeric(6, 2),
    body_fat_rate numeric(5, 2),
    waist_cm numeric(6, 2),
    hip_cm numeric(6, 2),
    chest_cm numeric(6, 2),
    arm_cm numeric(6, 2),
    thigh_cm numeric(6, 2),
    calf_cm numeric(6, 2),
    measured_at timestamp with time zone not null,
    source varchar(30) not null,
    note varchar(200),
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index idx_body_metric_entries_user_measured_at
    on body_metric_entries (user_id, measured_at desc);

create table user_goals (
    goal_id uuid primary key,
    user_id uuid not null,
    goal_type varchar(40) not null,
    target_value numeric(10, 2),
    target_unit varchar(20),
    target_date date,
    status varchar(30) not null,
    notes varchar(300),
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint uk_user_goals_user_type unique (user_id, goal_type)
);

create index idx_user_goals_user_id
    on user_goals (user_id);

create table outbox_events (
    event_id uuid primary key,
    topic varchar(120) not null,
    event_type varchar(120) not null,
    aggregate_type varchar(120) not null,
    aggregate_id varchar(120) not null,
    user_id uuid,
    event_version integer not null,
    payload text not null,
    status varchar(30) not null,
    date_time_produced timestamp with time zone not null,
    date_time_published timestamp with time zone,
    error_message varchar(500),
    date_time_created timestamp with time zone not null
);

create index idx_user_outbox_events_status_date_time_created
    on outbox_events (status, date_time_created);

insert into code_sets (
    code_set_id, code_set_key, enum_class_name, display_name, description,
    date_effective_from, date_effective_to, active,
    date_time_created, date_time_updated, created_by, updated_by, version
) values
('00000000-0000-0000-0000-000000010001', 'unit_system', 'com.fitsync.user.domain.UnitSystem', 'Unit system', 'Measurement unit preference', null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000010002', 'language_preference', 'com.fitsync.user.domain.LanguagePreference', 'Language preference', 'Application language preference', null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000010003', 'privacy_level', 'com.fitsync.user.domain.PrivacyLevel', 'Privacy level', 'User profile visibility level', null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000020001', 'fitness_level', 'com.fitsync.user.domain.FitnessLevel', 'Fitness level', 'User training experience level', null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000020002', 'training_location', 'com.fitsync.user.domain.TrainingLocation', 'Training location', 'Preferred training environment', null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000020003', 'intensity_preference', 'com.fitsync.user.domain.IntensityPreference', 'Intensity preference', 'Preferred workout intensity', null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000030001', 'dietary_preference', 'com.fitsync.user.domain.DietaryPreference', 'Dietary preference', 'Dietary pattern preference', null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000030002', 'calorie_tracking_mode', 'com.fitsync.user.domain.CalorieTrackingMode', 'Calorie tracking mode', 'Calorie tracking strictness', null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000030003', 'macro_preference', 'com.fitsync.user.domain.MacroPreference', 'Macro preference', 'Macronutrient distribution preference', null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0);

insert into code_values (
    code_value_id, code_set_key, enum_name, code, display_name, description, sort_order, metadata_json,
    date_effective_from, date_effective_to, active,
    date_time_created, date_time_updated, created_by, updated_by, version
) values
('00000000-0000-0000-0000-000000100001', 'unit_system', 'METRIC', '100001', 'Metric', 'Metric units', 10, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000100002', 'unit_system', 'IMPERIAL', '100002', 'Imperial', 'Imperial units', 20, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000110001', 'language_preference', 'EN', '110001', 'English', 'English', 10, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000110002', 'language_preference', 'ZH_CN', '110002', 'Simplified Chinese', 'Simplified Chinese', 20, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000120001', 'privacy_level', 'PRIVATE', '120001', 'Only me', 'Only me', 10, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000120002', 'privacy_level', 'FRIENDS', '120002', 'Friends only', 'Friends only', 20, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000120003', 'privacy_level', 'PUBLIC', '120003', 'Public profile', 'Public profile', 30, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000200001', 'fitness_level', 'BEGINNER', '200001', 'Beginner', 'Beginner', 10, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000200002', 'fitness_level', 'INTERMEDIATE', '200002', 'Intermediate', 'Intermediate', 20, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000200003', 'fitness_level', 'ADVANCED', '200003', 'Advanced', 'Advanced', 30, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000210001', 'training_location', 'HOME', '210001', 'Home', 'Home', 10, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000210002', 'training_location', 'GYM', '210002', 'Gym', 'Gym', 20, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000210003', 'training_location', 'OUTDOOR', '210003', 'Outdoor', 'Outdoor', 30, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000210004', 'training_location', 'MIXED', '210004', 'Mixed', 'Mixed', 40, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000220001', 'intensity_preference', 'LOW', '220001', 'Low', 'Low intensity', 10, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000220002', 'intensity_preference', 'MODERATE', '220002', 'Moderate', 'Moderate intensity', 20, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000220003', 'intensity_preference', 'HIGH', '220003', 'High', 'High intensity', 30, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000300001', 'dietary_preference', 'NONE', '300001', 'No dietary pattern', 'No dietary pattern', 10, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000300002', 'dietary_preference', 'HALAL', '300002', 'Halal', 'Halal', 20, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000300003', 'dietary_preference', 'VEGETARIAN', '300003', 'Vegetarian', 'Vegetarian', 30, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000300004', 'dietary_preference', 'VEGAN', '300004', 'Vegan', 'Vegan', 40, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000300005', 'dietary_preference', 'KETO', '300005', 'Keto', 'Keto', 50, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000300006', 'dietary_preference', 'LOW_CARB', '300006', 'Low carbohydrate', 'Low carbohydrate', 60, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000310001', 'calorie_tracking_mode', 'OFF', '310001', 'Off', 'Do not track calories', 10, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000310002', 'calorie_tracking_mode', 'FLEXIBLE', '310002', 'Flexible', 'Flexible calorie tracking', 20, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000310003', 'calorie_tracking_mode', 'STRICT', '310003', 'Strict', 'Strict calorie tracking', 30, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000320001', 'macro_preference', 'BALANCED', '320001', 'Balanced', 'Balanced macros', 10, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000320002', 'macro_preference', 'HIGH_PROTEIN', '320002', 'High protein', 'Higher protein', 20, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000320003', 'macro_preference', 'LOW_CARB', '320003', 'Low carbohydrate', 'Lower carbohydrate', 30, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0);
