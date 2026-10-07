create table user_guide_state (
    user_id uuid primary key,
    state text not null,
    updated_at timestamp with time zone not null
);
