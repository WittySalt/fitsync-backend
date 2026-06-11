create table app_users (
    id uuid primary key,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    email varchar(120) not null unique,
    display_name varchar(80) not null,
    password_hash varchar(255) not null,
    role varchar(30) not null
);
