-- Nutrition page (boards 13c and 13e): where each nutrient sits in the all-nutrients table, and the guide table.
-- group_code, symbol and atomic_number are null for the nutrients the table does not show (fibre, cholesterol,
-- alcohol and caffeine have no group), so GET /nutrients/summary?scope=all lists only nutrients with a group.
-- The guide rows themselves are written by the prod-only seed V903; the table is created here so that tests have it.

alter table nutrients add column group_code varchar(20);
alter table nutrients add column symbol varchar(4);
alter table nutrients add column atomic_number integer;
alter table nutrients add column display_order integer not null default 0;

update nutrients set group_code = 'FAT_SOLUBLE', symbol = 'D', atomic_number = null, display_order = 10 where code = 'VIT_D';
update nutrients set group_code = 'FAT_SOLUBLE', symbol = 'E', atomic_number = null, display_order = 20 where code = 'VIT_E';
update nutrients set group_code = 'FAT_SOLUBLE', symbol = 'A', atomic_number = null, display_order = 30 where code = 'VIT_A';
update nutrients set group_code = 'FAT_SOLUBLE', symbol = 'K1', atomic_number = null, display_order = 40 where code = 'VIT_K';
update nutrients set group_code = 'WATER_SOLUBLE', symbol = 'B1', atomic_number = null, display_order = 50 where code = 'THIAMIN';
update nutrients set group_code = 'WATER_SOLUBLE', symbol = 'B2', atomic_number = null, display_order = 60 where code = 'RIBOFLAVIN';
update nutrients set group_code = 'WATER_SOLUBLE', symbol = 'B3', atomic_number = null, display_order = 70 where code = 'NIACIN';
update nutrients set group_code = 'WATER_SOLUBLE', symbol = 'B6', atomic_number = null, display_order = 80 where code = 'VIT_B6';
update nutrients set group_code = 'WATER_SOLUBLE', symbol = 'B9', atomic_number = null, display_order = 90 where code = 'FOLATE';
update nutrients set group_code = 'WATER_SOLUBLE', symbol = 'B12', atomic_number = null, display_order = 100 where code = 'VIT_B12';
update nutrients set group_code = 'WATER_SOLUBLE', symbol = 'C', atomic_number = null, display_order = 110 where code = 'VIT_C';
update nutrients set group_code = 'MAJOR_MINERAL', symbol = 'Ca', atomic_number = 20, display_order = 200 where code = 'CALCIUM';
update nutrients set group_code = 'MAJOR_MINERAL', symbol = 'Mg', atomic_number = 12, display_order = 210 where code = 'MAGNESIUM';
update nutrients set group_code = 'MAJOR_MINERAL', symbol = 'P', atomic_number = 15, display_order = 220 where code = 'PHOSPHORUS';
update nutrients set group_code = 'MAJOR_MINERAL', symbol = 'K', atomic_number = 19, display_order = 230 where code = 'POTASSIUM';
update nutrients set group_code = 'TRACE_MINERAL', symbol = 'Fe', atomic_number = 26, display_order = 300 where code = 'IRON';
update nutrients set group_code = 'TRACE_MINERAL', symbol = 'Zn', atomic_number = 30, display_order = 310 where code = 'ZINC';
update nutrients set group_code = 'TRACE_MINERAL', symbol = 'Cu', atomic_number = 29, display_order = 320 where code = 'COPPER';
update nutrients set group_code = 'TRACE_MINERAL', symbol = 'Se', atomic_number = 34, display_order = 330 where code = 'SELENIUM';
update nutrients set group_code = 'LIMIT', symbol = 'Na', atomic_number = 11, display_order = 400 where code = 'SODIUM';
update nutrients set group_code = 'LIMIT', symbol = 'Su', atomic_number = null, display_order = 410 where code = 'SUGAR';
update nutrients set group_code = 'LIMIT', symbol = 'SF', atomic_number = null, display_order = 420 where code = 'SAT_FAT';
update nutrients set group_code = null, symbol = null, atomic_number = null, display_order = 900 where code = 'FIBER';
update nutrients set group_code = null, symbol = null, atomic_number = null, display_order = 910 where code = 'CHOLESTEROL';
update nutrients set group_code = null, symbol = null, atomic_number = null, display_order = 920 where code = 'ALCOHOL';
update nutrients set group_code = null, symbol = null, atomic_number = null, display_order = 930 where code = 'CAFFEINE';

create table nutrient_guides (
    code varchar(40) primary key,
    what_it_does text not null,
    too_little_too_much text not null,
    who_needs_more text not null,
    tips text not null,
    source_label varchar(120) not null,
    updated_at timestamp with time zone not null,
    constraint fk_nutrient_guides_nutrient foreign key (code) references nutrients (code)
);
