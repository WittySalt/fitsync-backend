-- RC-3 (R6 PR celebration): keep just the latest personal record achieved so the
-- rule can celebrate it while it is fresh (the full records tally lives in
-- analytics; this is the same cheap-local-projection pattern as the meal totals).

alter table recommendation_context add column last_pr_exercise varchar(120);
alter table recommendation_context add column last_pr_record_type varchar(40);
alter table recommendation_context add column last_pr_value numeric(12, 2);
alter table recommendation_context add column last_pr_at timestamp with time zone;
