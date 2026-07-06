-- NU-18 (SNAP-1): food-classification snapshot columns on food line items.
-- AbstractFoodLineItemEntity is a @MappedSuperclass, so its columns live in every subclass table:
-- meal_items and meal_template_items. All columns are nullable and additive (existing rows stay valid);
-- they are populated at log time by a later step (SNAP-3). Snapshotting the classification on the line
-- item makes food-group / processing-level analysis immune to later catalog recategorization and works
-- for custom/external foods. See recommendation-service/analytics-collaboration.md, nutrition-data-plan.md.

alter table meal_items add column category_code varchar(40);
alter table meal_items add column nova_group integer;
alter table meal_items add column nutri_score varchar(1);
alter table meal_items add column grams_consumed numeric(10, 2);
alter table meal_items add column source_dataset varchar(30);

alter table meal_template_items add column category_code varchar(40);
alter table meal_template_items add column nova_group integer;
alter table meal_template_items add column nutri_score varchar(1);
alter table meal_template_items add column grams_consumed numeric(10, 2);
alter table meal_template_items add column source_dataset varchar(30);
