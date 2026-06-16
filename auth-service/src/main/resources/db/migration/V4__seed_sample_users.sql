insert into app_users (
    id,
    date_time_created,
    date_time_updated,
    created_by,
    updated_by,
    version,
    email,
    display_name,
    password_hash,
    role,
    auth_provider,
    external_subject
) values
(
    '11111111-1111-1111-1111-111111111111',
    timestamp with time zone '2026-06-01 08:00:00+00',
    timestamp with time zone '2026-06-10 08:00:00+00',
    'sample-data',
    'sample-data',
    0,
    'alice@example.com',
    'Alice Tan',
    '$2a$10$uC6cARLmEnJT6QxaDrEQY.QquE2.M3/CqQDQk2d7paAQuyeo6FnRS',
    'USER',
    'LOCAL',
    null
),
(
    '22222222-2222-2222-2222-222222222222',
    timestamp with time zone '2026-06-01 08:05:00+00',
    timestamp with time zone '2026-06-10 08:05:00+00',
    'sample-data',
    'sample-data',
    0,
    'admin@example.com',
    'FitSync Admin',
    '$2a$10$uC6cARLmEnJT6QxaDrEQY.QquE2.M3/CqQDQk2d7paAQuyeo6FnRS',
    'ADMIN',
    'LOCAL',
    null
);
