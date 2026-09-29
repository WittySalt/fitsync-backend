-- A session can carry its own distance, used by sport sessions logged without an exercise row.
alter table workout_sessions
    add column distance_km numeric(7,2);
