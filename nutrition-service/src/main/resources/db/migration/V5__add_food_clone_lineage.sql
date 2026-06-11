alter table food_catalog_items add column base_food_id uuid;
alter table food_catalog_items add column creation_mode varchar(20) default 'MANUAL' not null;

create index idx_food_catalog_items_base_food_id
    on food_catalog_items (base_food_id);
