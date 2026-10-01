-- A workout that was left open is closed by the server. The session records when that happened, so a
-- client can tell the person and the person can edit the result afterwards.
alter table workout_sessions
    add column auto_closed_at timestamp with time zone;

-- The last time a set was marked done or skipped. Set rows carry no audit columns, and the moment a
-- workout was last worked on is what decides whether it was left. Planned and repeated sets that
-- are still pending keep it empty.
alter table workout_exercise_set_logs
    add column updated_at timestamp with time zone;

-- Sets of sessions that are in progress today get the time their session was last saved, which is
-- the closest the existing data comes to the real moment. Finished sessions are never read for this.
update workout_exercise_set_logs
set updated_at = (
    select s.date_time_updated
    from workout_exercise_logs l
    join workout_sessions s on s.session_id = l.session_id
    where l.exercise_log_id = workout_exercise_set_logs.exercise_log_id)
where status in ('DONE', 'SKIPPED')
  and exercise_log_id in (
    select l.exercise_log_id
    from workout_exercise_logs l
    join workout_sessions s on s.session_id = l.session_id
    where s.status = 'IN_PROGRESS');

-- The sweep that closes left workouts looks for in-progress sessions by age.
create index idx_workout_sessions_status_started_at
    on workout_sessions (status, started_at);
