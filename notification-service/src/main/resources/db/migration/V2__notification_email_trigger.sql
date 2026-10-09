-- Who asked for an email: EVENT for every email the service decides itself, ADMIN for an admin's
-- send now from the console. An admin send also keeps the admin's id, which is the audit record
-- and what the hourly limit per admin counts.
alter table notification_emails add column trigger_source varchar(10) not null default 'EVENT';
alter table notification_emails add column admin_id uuid;

create index idx_notification_emails_admin on notification_emails (admin_id, created_at);
