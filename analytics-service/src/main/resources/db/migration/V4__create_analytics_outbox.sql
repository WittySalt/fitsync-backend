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

create index idx_analytics_outbox_events_status_created_at
    on outbox_events (status, date_time_created);
