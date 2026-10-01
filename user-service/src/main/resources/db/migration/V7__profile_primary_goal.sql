alter table user_profiles
    add column if not exists primary_goal varchar(30);
