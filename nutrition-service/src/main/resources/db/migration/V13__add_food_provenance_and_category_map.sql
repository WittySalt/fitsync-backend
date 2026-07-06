-- NU-16: open-data food ingestion support (USDA FoodData Central + Open Food Facts).
-- Two additive changes, both backward-compatible:
--   1. Provenance + diet-quality columns on food_catalog_items (all nullable; existing/seeded rows and
--      manually-created foods stay valid). source_dataset/source_ref identify the origin record so an
--      importer can upsert idempotently; nova_group/nutri_score are Open Food Facts diet-quality signals;
--      data_completeness (0.000-1.000) lets analytics distinguish "missing" from "zero".
--   2. external_food_category_map: maps a source category string (USDA category / OFF tag) onto the
--      EXISTING internal food_categories.code. This is the food-group layer open data does not provide.
-- See recommendation-service/nutrition-data-plan.md (§4, §5) and analytics-collaboration.md.

alter table food_catalog_items add column source_dataset varchar(30);
alter table food_catalog_items add column source_ref varchar(120);
alter table food_catalog_items add column nova_group integer;
alter table food_catalog_items add column nutri_score varchar(1);
alter table food_catalog_items add column data_completeness numeric(4, 3);

-- Idempotent upsert key for importers (only meaningful for imported rows; many rows leave these null,
-- so this is a partial-style guard enforced in application code rather than a DB unique constraint).
create index idx_food_catalog_items_source_ref on food_catalog_items (source_dataset, source_ref);

create table external_food_category_map (
    mapping_id uuid primary key,
    source_dataset varchar(30) not null,
    source_category_code varchar(120) not null,
    internal_category_code varchar(40) not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null
);

create unique index idx_external_food_category_map_source
    on external_food_category_map (source_dataset, source_category_code);
