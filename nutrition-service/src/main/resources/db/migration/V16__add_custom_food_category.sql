-- NU-19 (SNAP-2): give custom foods a food-category code so the line-item classification snapshot
-- (SNAP-3) can classify user-created foods, which otherwise carry no category. Nullable/additive.

alter table custom_foods add column category_code varchar(40);
