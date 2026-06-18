create table if not exists account_data_export_requests (
    export_request_id uuid primary key,
    user_id uuid not null,
    status varchar(30) not null,
    requested_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create index if not exists idx_account_data_export_requests_user_requested_at
    on account_data_export_requests (user_id, requested_at desc);
