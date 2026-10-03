-- The zone a person keeps in their profile, projected from user-service events. The insight
-- endpoints read it as the default when a request does not name a zone, so "today" and "this week"
-- agree with the rest of the app instead of falling back to UTC.
create table workout_user_timezones (
    user_id uuid primary key,
    timezone varchar(64) not null,
    updated_at timestamp with time zone not null
);
