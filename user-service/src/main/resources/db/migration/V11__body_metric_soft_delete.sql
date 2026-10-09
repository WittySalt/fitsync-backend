-- A body metric the person deletes is kept and marked, as every user-facing delete is (soft delete); reads leave
-- marked rows out. Erasing the whole account still removes every row, marked or not.
alter table body_metric_entries
    add column if not exists deleted_at timestamp with time zone;
