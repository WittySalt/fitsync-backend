create table food_nutrient_details (
    detail_id uuid primary key,
    food_id uuid not null,
    nutrient_code varchar(40) not null,
    display_name varchar(80) not null,
    amount numeric(10, 2) not null,
    unit varchar(20) not null,
    sort_order integer not null,
    date_effective_from date,
    date_effective_to date,
    active boolean not null,
    date_time_created timestamp with time zone not null,
    date_time_updated timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    version bigint not null,
    constraint fk_food_nutrient_details_food
        foreign key (food_id) references food_catalog_items (food_id) on delete cascade
);

create index idx_food_nutrient_details_food_id
    on food_nutrient_details (food_id, sort_order);
