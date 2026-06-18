-- NU-15: food category taxonomy for e-commerce-style hierarchical browse/filter.
-- A self-referencing (adjacency-list) tree with a denormalized materialized `path` so that
-- "filter by a parent category" is a single portable `path LIKE '/proteins/%'` query (no recursive
-- CTE) that works on both PostgreSQL (prod) and H2 (tests). Foods are linked by the EXISTING
-- food_catalog_items.category_code (= the leaf category's code), so no food backfill is needed.
-- The taxonomy is curated/fixed; users cannot create categories. `active` + audit columns are kept
-- so an admin maintenance API can be layered on later (NU-15b) without a schema change.

create table food_categories (
    category_id uuid primary key,
    parent_id uuid,
    code varchar(40) not null,
    display_name varchar(80) not null,
    slug varchar(80) not null,
    path varchar(255) not null,
    sort_order integer not null,
    active boolean not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_food_categories_parent foreign key (parent_id) references food_categories (category_id)
);

create unique index idx_food_categories_code on food_categories (code);
create index idx_food_categories_parent on food_categories (parent_id, sort_order);
create index idx_food_categories_path on food_categories (path);

-- Top-level groups (大类)
insert into food_categories (
    category_id, parent_id, code, display_name, slug, path, sort_order, active,
    date_time_created, date_time_updated, created_by, updated_by, version
) values
('ca700001-0000-0000-0000-000000000000',null,'PROTEINS','Proteins','proteins','/proteins/',1,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca700002-0000-0000-0000-000000000000',null,'STAPLES','Grains & Staples','staples','/staples/',2,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca700003-0000-0000-0000-000000000000',null,'PRODUCE','Produce','produce','/produce/',3,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca700004-0000-0000-0000-000000000000',null,'DAIRY_GROUP','Dairy','dairy_group','/dairy_group/',4,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca700005-0000-0000-0000-000000000000',null,'FATS','Fats & Nuts','fats','/fats/',5,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca700006-0000-0000-0000-000000000000',null,'LEGUMES_GROUP','Legumes','legumes_group','/legumes_group/',6,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0);

-- Subcategories (小类) — code matches the existing food_catalog_items.category_code values
insert into food_categories (
    category_id, parent_id, code, display_name, slug, path, sort_order, active,
    date_time_created, date_time_updated, created_by, updated_by, version
) values
('ca710001-0000-0000-0000-000000000000','ca700001-0000-0000-0000-000000000000','MEAT_POULTRY','Meat & Poultry','meat_poultry','/proteins/meat_poultry/',1,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca710002-0000-0000-0000-000000000000','ca700001-0000-0000-0000-000000000000','SEAFOOD','Seafood','seafood','/proteins/seafood/',2,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca710003-0000-0000-0000-000000000000','ca700001-0000-0000-0000-000000000000','EGGS','Eggs','eggs','/proteins/eggs/',3,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca710004-0000-0000-0000-000000000000','ca700001-0000-0000-0000-000000000000','PLANT_PROTEIN','Plant Protein','plant_protein','/proteins/plant_protein/',4,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca710005-0000-0000-0000-000000000000','ca700001-0000-0000-0000-000000000000','PROTEIN_SUPPLEMENT','Protein Supplements','protein_supplement','/proteins/protein_supplement/',5,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca710006-0000-0000-0000-000000000000','ca700002-0000-0000-0000-000000000000','GRAINS','Grains','grains','/staples/grains/',1,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca710007-0000-0000-0000-000000000000','ca700003-0000-0000-0000-000000000000','VEGETABLES','Vegetables','vegetables','/produce/vegetables/',1,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca710008-0000-0000-0000-000000000000','ca700003-0000-0000-0000-000000000000','FRUITS','Fruits','fruits','/produce/fruits/',2,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca710009-0000-0000-0000-000000000000','ca700004-0000-0000-0000-000000000000','DAIRY','Dairy Products','dairy','/dairy_group/dairy/',1,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca710010-0000-0000-0000-000000000000','ca700005-0000-0000-0000-000000000000','NUTS_SEEDS','Nuts & Seeds','nuts_seeds','/fats/nuts_seeds/',1,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca710011-0000-0000-0000-000000000000','ca700005-0000-0000-0000-000000000000','FATS_OILS','Fats & Oils','fats_oils','/fats/fats_oils/',2,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0),
('ca710012-0000-0000-0000-000000000000','ca700006-0000-0000-0000-000000000000','LEGUMES','Legumes','legumes','/legumes_group/legumes/',1,true,timestamp with time zone '2026-06-01 08:00:00+00',timestamp with time zone '2026-06-01 08:00:00+00','seed-foods','seed-foods',0);
