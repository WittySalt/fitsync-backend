alter table exercises
    add column video_url varchar(500);

alter table exercises
    add column animation_url varchar(500);

alter table exercises
    add column external_source varchar(120);

alter table exercises
    add column external_ref varchar(200);

-- PostgreSQL and H2 both allow multiple NULL values in a unique index, so
-- custom/manual exercises without provenance remain unconstrained while seeded
-- catalog rows are idempotent by source reference.
create unique index uk_exercises_external_source_ref
    on exercises (external_source, external_ref);

create table exercise_instructions (
    instruction_id uuid primary key,
    exercise_id uuid not null references exercises(exercise_id) on delete cascade,
    step_number integer not null,
    instruction_text varchar(1000) not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create unique index uk_exercise_instructions_step
    on exercise_instructions (exercise_id, step_number);

create table exercise_images (
    image_id uuid primary key,
    exercise_id uuid not null references exercises(exercise_id) on delete cascade,
    image_position integer not null,
    image_url varchar(500) not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create unique index uk_exercise_images_position
    on exercise_images (exercise_id, image_position);

create table exercise_secondary_muscles (
    secondary_muscle_id uuid primary key,
    exercise_id uuid not null references exercises(exercise_id) on delete cascade,
    muscle_group varchar(40) not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create unique index uk_exercise_secondary_muscles_group
    on exercise_secondary_muscles (exercise_id, muscle_group);
