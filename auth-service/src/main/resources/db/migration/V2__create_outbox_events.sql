create table outbox_events (
    event_id uuid primary key,
    topic varchar(120) not null,
    event_type varchar(120) not null,
    aggregate_type varchar(120) not null,
    aggregate_id varchar(120) not null,
    user_id uuid,
    version integer not null,
    payload text not null,
    status varchar(30) not null,
    occurred_at timestamp with time zone not null,
    published_at timestamp with time zone,
    error_message varchar(500),
    created_at timestamp with time zone not null
);

create index idx_outbox_events_status_created_at
    on outbox_events (status, created_at);
