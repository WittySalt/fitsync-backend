-- Idempotent staging DB bootstrap: one least-privilege login role per service +
-- a schema it can build into, matching each service's application.yml (DB_USERNAME
-- / Flyway schema). Run as the RDS master against the `fitsync` database, from
-- inside the VPC (the staging EC2) because the RDS is not publicly accessible.
--
-- The service password is injected at runtime and never stored here:
--   psql ... -v svc_pw="$STAGING_DB_PASSWORD" -f bootstrap-staging-db.sql
--
-- Schema is created by the master (owner) and the service role is GRANTed
-- USAGE+CREATE on it — NOT `CREATE SCHEMA ... AUTHORIZATION <role>`, because on
-- RDS the master is not a superuser and "must be able to SET ROLE <role>" to use
-- AUTHORIZATION. USAGE+CREATE is all Flyway/the app need (tables it creates are
-- owned by the service role).
--
-- role -> schema:
--   auth_svc      -> auth
--   user_svc      -> user_domain
--   workout_svc   -> workout
--   nutrition_svc -> nutrition
--   analytics_svc -> analytics
--   recommendation_svc -> recommendation
\set ON_ERROR_STOP on

-- auth
SELECT format('CREATE ROLE %I LOGIN', 'auth_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'auth_svc')\gexec
ALTER ROLE auth_svc LOGIN PASSWORD :'svc_pw';
CREATE SCHEMA IF NOT EXISTS auth;
GRANT USAGE, CREATE ON SCHEMA auth TO auth_svc;

-- user
SELECT format('CREATE ROLE %I LOGIN', 'user_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'user_svc')\gexec
ALTER ROLE user_svc LOGIN PASSWORD :'svc_pw';
CREATE SCHEMA IF NOT EXISTS user_domain;
GRANT USAGE, CREATE ON SCHEMA user_domain TO user_svc;

-- workout
SELECT format('CREATE ROLE %I LOGIN', 'workout_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'workout_svc')\gexec
ALTER ROLE workout_svc LOGIN PASSWORD :'svc_pw';
CREATE SCHEMA IF NOT EXISTS workout;
GRANT USAGE, CREATE ON SCHEMA workout TO workout_svc;

-- nutrition
SELECT format('CREATE ROLE %I LOGIN', 'nutrition_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nutrition_svc')\gexec
ALTER ROLE nutrition_svc LOGIN PASSWORD :'svc_pw';
CREATE SCHEMA IF NOT EXISTS nutrition;
GRANT USAGE, CREATE ON SCHEMA nutrition TO nutrition_svc;

-- analytics
SELECT format('CREATE ROLE %I LOGIN', 'analytics_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'analytics_svc')\gexec
ALTER ROLE analytics_svc LOGIN PASSWORD :'svc_pw';
CREATE SCHEMA IF NOT EXISTS analytics;
GRANT USAGE, CREATE ON SCHEMA analytics TO analytics_svc;

-- recommendation
SELECT format('CREATE ROLE %I LOGIN', 'recommendation_svc') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'recommendation_svc')\gexec
ALTER ROLE recommendation_svc LOGIN PASSWORD :'svc_pw';
CREATE SCHEMA IF NOT EXISTS recommendation;
GRANT USAGE, CREATE ON SCHEMA recommendation TO recommendation_svc;
