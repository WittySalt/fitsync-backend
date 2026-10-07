-- Idempotent prod DB bootstrap. Same shape as bootstrap-staging-db.sql, but every
-- service gets its own password (the values of the <SERVICE>_DB_PASSWORD secrets).
-- On a fresh database it creates the six login roles and their schemas; run again, it
-- only resets the passwords, and existing roles keep their tables and grants.
-- Run as the RDS master from the prod EC2, because the RDS is not publicly accessible:
--
--   psql -h <rds-host> -U fitsync_master -d fitsync -v ON_ERROR_STOP=1 \
--     -v auth_pw="$AUTH_DB_PASSWORD" -v user_pw="$USER_DB_PASSWORD" \
--     -v workout_pw="$WORKOUT_DB_PASSWORD" -v nutrition_pw="$NUTRITION_DB_PASSWORD" \
--     -v analytics_pw="$ANALYTICS_DB_PASSWORD" -v recommendation_pw="$RECOMMENDATION_DB_PASSWORD" \
--     -f bootstrap-prod-db.sql
\set ON_ERROR_STOP on

-- auth
SELECT format('CREATE ROLE %I LOGIN', 'auth_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'auth_svc')\gexec
ALTER ROLE auth_svc LOGIN PASSWORD :'auth_pw';
CREATE SCHEMA IF NOT EXISTS auth;
GRANT USAGE, CREATE ON SCHEMA auth TO auth_svc;

-- user_domain
SELECT format('CREATE ROLE %I LOGIN', 'user_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'user_svc')\gexec
ALTER ROLE user_svc LOGIN PASSWORD :'user_pw';
CREATE SCHEMA IF NOT EXISTS user_domain;
GRANT USAGE, CREATE ON SCHEMA user_domain TO user_svc;

-- workout
SELECT format('CREATE ROLE %I LOGIN', 'workout_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'workout_svc')\gexec
ALTER ROLE workout_svc LOGIN PASSWORD :'workout_pw';
CREATE SCHEMA IF NOT EXISTS workout;
GRANT USAGE, CREATE ON SCHEMA workout TO workout_svc;

-- nutrition
SELECT format('CREATE ROLE %I LOGIN', 'nutrition_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nutrition_svc')\gexec
ALTER ROLE nutrition_svc LOGIN PASSWORD :'nutrition_pw';
CREATE SCHEMA IF NOT EXISTS nutrition;
GRANT USAGE, CREATE ON SCHEMA nutrition TO nutrition_svc;

-- analytics
SELECT format('CREATE ROLE %I LOGIN', 'analytics_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'analytics_svc')\gexec
ALTER ROLE analytics_svc LOGIN PASSWORD :'analytics_pw';
CREATE SCHEMA IF NOT EXISTS analytics;
GRANT USAGE, CREATE ON SCHEMA analytics TO analytics_svc;

-- recommendation
SELECT format('CREATE ROLE %I LOGIN', 'recommendation_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'recommendation_svc')\gexec
ALTER ROLE recommendation_svc LOGIN PASSWORD :'recommendation_pw';
CREATE SCHEMA IF NOT EXISTS recommendation;
GRANT USAGE, CREATE ON SCHEMA recommendation TO recommendation_svc;
