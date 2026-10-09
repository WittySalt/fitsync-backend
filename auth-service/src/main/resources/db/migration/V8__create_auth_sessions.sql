-- One row per signed-in device. The refresh token itself is never stored: only its SHA-256 hash (hex),
-- and the hash of the token it replaced, so presenting an already-rotated token can be recognised as reuse.
-- A session is never deleted. Revoking it sets revoked_at and revoked_reason (soft delete).
-- No foreign key to app_users: like the other services' user_id columns this is a plain reference, and the
-- row has to outlive any later clean-up of the account it belonged to.
create table auth_sessions (
    id uuid primary key,
    user_id uuid not null,
    device_label varchar(60) not null,
    refresh_token_hash varchar(64) not null,
    previous_refresh_token_hash varchar(64),
    created_at timestamp with time zone not null,
    last_seen_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    revoked_reason varchar(40),
    constraint uk_auth_sessions_refresh_token_hash unique (refresh_token_hash)
);

create index idx_auth_sessions_user_id on auth_sessions (user_id);
create index idx_auth_sessions_previous_refresh_token_hash on auth_sessions (previous_refresh_token_hash);
