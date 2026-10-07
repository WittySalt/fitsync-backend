-- Idempotent DB bootstrap for every environment: one least-privilege login role per
-- service plus the schema it builds into. On a fresh database it creates the six roles
-- and schemas; on later runs it only re-applies the passwords, and existing roles keep
-- their tables and grants. Flyway then builds the tables as each service user.
--
-- scripts/ec2-deploy.sh runs it on every deploy, as the RDS master, with the passwords
-- read from Secrets Manager and passed as psql variables (auth_pw, user_pw, workout_pw,
-- nutrition_pw, analytics_pw, recommendation_pw). Nothing is stored in this file.
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
