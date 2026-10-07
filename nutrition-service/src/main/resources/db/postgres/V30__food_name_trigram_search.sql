-- Typo-tolerant food search (NU-14). pg_trgm gives similarity(), and a trigram GIN index on the lower-cased
-- display name backs the % operators. This file lives in db/postgres, which only the PostgreSQL profiles
-- load: H2 has neither the extension nor GIN. The migration user needs the right to create the extension
-- (pg_trgm is a trusted extension from PostgreSQL 13, and the RDS master user has it).

create extension if not exists pg_trgm;

create index if not exists idx_food_catalog_items_name_trgm
    on food_catalog_items using gin (lower(display_name) gin_trgm_ops);
