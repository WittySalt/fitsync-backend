-- Weekly reviews (surface PROGRESS_WEEK) keep one reading per user and per Monday week, so the
-- reading row gains the period it describes. Every page reading keeps one row per user and surface,
-- under the empty key, which is what the default gives the rows that already exist; the unique
-- constraint moves to (user_id, surface, period_key) so the race protection holds for both.
--
-- facts_json holds the facts a weekly review was written from, so a week that has ended is served
-- again without reading three services. Page readings leave it null.

alter table coach_page_reading add column period_key varchar(10) not null default '';
alter table coach_page_reading add column facts_json varchar(4000);

alter table coach_page_reading drop constraint uq_coach_page_reading_user_surface;
alter table coach_page_reading
    add constraint uq_coach_page_reading_user_surface_period unique (user_id, surface, period_key);
