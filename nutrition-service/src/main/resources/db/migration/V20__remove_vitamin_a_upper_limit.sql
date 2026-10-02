-- The NIH upper intake for vitamin A applies to preformed vitamin A (retinol) only, while our amounts are RAE including plant beta-carotene, so a vegetable-rich day would be flagged wrongly.
delete from nutrient_daily_references where nutrient_code = 'VIT_A' and kind = 'UPPER';
