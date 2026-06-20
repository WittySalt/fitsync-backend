-- V1 created a global unique index named uk_exercises_code on exercises(code).
-- Replace it with owner-scoped uniqueness so different users can use the same
-- custom exercise code. PostgreSQL and H2 allow multiple NULL values in unique
-- indexes, so global catalog rows (owner_user_id is null) do not collide through
-- this composite key; existing global seed rows remain individually addressable.
drop index if exists uk_exercises_code;

create unique index uk_exercises_owner_code
    on exercises (owner_user_id, code);
