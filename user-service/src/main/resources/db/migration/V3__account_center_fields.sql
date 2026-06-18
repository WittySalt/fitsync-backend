alter table user_profiles
    add column if not exists location_display varchar(120);

alter table user_profiles
    add column if not exists membership_tier varchar(30) not null default 'MEMBER';

alter table user_profiles
    add column if not exists membership_status varchar(30) not null default 'ACTIVE';

alter table user_notification_preferences
    add column if not exists weekly_progress_report_enabled boolean not null default false;

alter table user_notification_preferences
    add column if not exists weekly_progress_report_channel varchar(20) not null default 'EMAIL';
