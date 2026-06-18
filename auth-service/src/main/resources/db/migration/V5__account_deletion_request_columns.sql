alter table app_users
    add column if not exists account_deletion_requested_at timestamp with time zone;
