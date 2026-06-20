insert into code_sets (
    code_set_id, code_set_key, enum_class_name, display_name, description,
    date_effective_from, date_effective_to, active,
    date_time_created, date_time_updated, created_by, updated_by, version
) values
('00000000-0000-0000-0000-000000020004', 'training_equipment', 'com.fitsync.user.domain.TrainingEquipment', 'Training equipment', 'Available workout equipment inventory', null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000020005', 'training_focus_area', 'com.fitsync.user.domain.TrainingFocusArea', 'Training focus area', 'Preferred training focus areas', null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0);

insert into code_values (
    code_value_id, code_set_key, enum_name, code, display_name, description, sort_order, metadata_json,
    date_effective_from, date_effective_to, active,
    date_time_created, date_time_updated, created_by, updated_by, version
) values
('00000000-0000-0000-0000-000000230001', 'training_equipment', 'NONE', '230001', 'None', 'No dedicated training equipment', 10, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000230002', 'training_equipment', 'DUMBBELLS', '230002', 'Dumbbells', 'Dumbbells available', 20, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000230003', 'training_equipment', 'BARBELL', '230003', 'Barbell', 'Barbell available', 30, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000230004', 'training_equipment', 'BANDS', '230004', 'Bands', 'Resistance bands available', 40, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000230005', 'training_equipment', 'PULL_UP_BAR', '230005', 'Pull-up bar', 'Pull-up bar available', 50, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000230006', 'training_equipment', 'KETTLEBELL', '230006', 'Kettlebell', 'Kettlebell available', 60, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000230007', 'training_equipment', 'MACHINES', '230007', 'Machines', 'Selectorized or cable machines available', 70, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000230008', 'training_equipment', 'FULL_GYM', '230008', 'Full gym', 'Full commercial gym access', 80, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000240001', 'training_focus_area', 'FULL_BODY', '240001', 'Full body', 'Balanced full body training', 10, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000240002', 'training_focus_area', 'UPPER', '240002', 'Upper body', 'Upper body emphasis', 20, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000240003', 'training_focus_area', 'CORE', '240003', 'Core', 'Core strength and stability', 30, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000240004', 'training_focus_area', 'LOWER', '240004', 'Lower body', 'Lower body emphasis', 40, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000240005', 'training_focus_area', 'MOBILITY', '240005', 'Mobility', 'Mobility and movement quality', 50, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0),
('00000000-0000-0000-0000-000000240006', 'training_focus_area', 'CARDIO', '240006', 'Cardio', 'Cardiovascular conditioning', 60, null, null, null, true, current_timestamp, current_timestamp, 'migration', 'migration', 0);

create table user_training_preference_equipment (
    user_id uuid not null,
    sort_order integer not null,
    equipment_code varchar(40) not null,
    constraint pk_user_training_pref_equipment primary key (user_id, sort_order),
    constraint fk_user_training_pref_equipment_user foreign key (user_id) references user_training_preferences (user_id) on delete cascade,
    constraint fk_user_training_pref_equipment_code foreign key (equipment_code) references code_values (code)
);

create table user_training_preference_focus_areas (
    user_id uuid not null,
    sort_order integer not null,
    focus_area_code varchar(40) not null,
    constraint pk_user_training_pref_focus_areas primary key (user_id, sort_order),
    constraint fk_user_training_pref_focus_user foreign key (user_id) references user_training_preferences (user_id) on delete cascade,
    constraint fk_user_training_pref_focus_code foreign key (focus_area_code) references code_values (code)
);

create table user_health_readiness (
    user_id uuid primary key,
    heart_condition boolean not null default false,
    chest_pain_activity boolean not null default false,
    dizziness boolean not null default false,
    bone_joint_issue boolean not null default false,
    bp_or_heart_meds boolean not null default false,
    other_reason boolean not null default false,
    acknowledged_disclaimer_at timestamp with time zone,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create table user_health_readiness_injury_areas (
    user_id uuid not null,
    sort_order integer not null,
    injury_area varchar(40) not null,
    constraint pk_user_health_readiness_injury primary key (user_id, sort_order),
    constraint fk_user_health_readiness_injury_user foreign key (user_id) references user_health_readiness (user_id) on delete cascade
);
