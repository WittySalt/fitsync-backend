-- Idempotent staging DB bootstrap: one least-privilege login role per service +
-- its owned schema, matching each service's application.yml (DB_USERNAME / Flyway
-- schema). Run as the RDS master against the `fitsync` database, from inside the
-- VPC (the staging EC2) because the RDS is not publicly accessible.
--
-- The service password is injected at runtime and never stored here:
--   psql ... -v svc_pw="$STAGING_DB_PASSWORD" -f bootstrap-staging-db.sql
--
-- role -> schema:
--   auth_svc      -> auth
--   user_svc      -> user_domain
--   workout_svc   -> workout
--   nutrition_svc -> nutrition
--   analytics_svc -> analytics
\set ON_ERROR_STOP on

-- auth
SELECT format('CREATE ROLE %I LOGIN', 'auth_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'auth_svc')\gexec
ALTER ROLE auth_svc LOGIN PASSWORD :'svc_pw';
CREATE SCHEMA IF NOT EXISTS auth AUTHORIZATION auth_svc;

-- user
SELECT format('CREATE ROLE %I LOGIN', 'user_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'user_svc')\gexec
ALTER ROLE user_svc LOGIN PASSWORD :'svc_pw';
CREATE SCHEMA IF NOT EXISTS user_domain AUTHORIZATION user_svc;

-- workout
SELECT format('CREATE ROLE %I LOGIN', 'workout_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'workout_svc')\gexec
ALTER ROLE workout_svc LOGIN PASSWORD :'svc_pw';
CREATE SCHEMA IF NOT EXISTS workout AUTHORIZATION workout_svc;

-- nutrition
SELECT format('CREATE ROLE %I LOGIN', 'nutrition_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nutrition_svc')\gexec
ALTER ROLE nutrition_svc LOGIN PASSWORD :'svc_pw';
CREATE SCHEMA IF NOT EXISTS nutrition AUTHORIZATION nutrition_svc;

-- analytics
SELECT format('CREATE ROLE %I LOGIN', 'analytics_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'analytics_svc')\gexec
ALTER ROLE analytics_svc LOGIN PASSWORD :'svc_pw';
CREATE SCHEMA IF NOT EXISTS analytics AUTHORIZATION analytics_svc;
