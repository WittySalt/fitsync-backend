-- Erasure of an account whose deletion grace period has ended (AccountPurgeService). The purge runs in two steps:
-- first it claims the account by setting account_purge_started_at, in the same transaction that writes
-- AccountDeleted to the outbox, and remembers that event's id; then, once the relay has published the event, it
-- deletes the Cognito user and this row. The claim is a conditional update, so two instances never purge the
-- same account twice.
alter table app_users
    add column if not exists account_purge_started_at timestamp with time zone;
alter table app_users
    add column if not exists account_purge_event_id uuid;

create index if not exists idx_app_users_account_deletion_requested_at
    on app_users (account_deletion_requested_at);
