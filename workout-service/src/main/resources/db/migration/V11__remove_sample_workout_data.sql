-- Removes the rows V2 seeded for the sample account: its session, its plan and its own custom exercise.
-- The three shared catalog exercises that V2 also inserted are left in place because other people's plans
-- and logs may already point at them. Rows without an audit column are removed through their parent, and the
-- parent keeps the created_by marker so that anything someone has since replaced is left alone. Each statement
-- is a no-op when the rows are already gone.
delete from workout_exercise_set_logs
where exercise_log_id in (
    select l.exercise_log_id
    from workout_exercise_logs l
    join workout_sessions s on s.session_id = l.session_id
    where s.session_id = '55111111-1111-1111-1111-111111111111'
      and s.created_by = 'sample-data');

delete from workout_exercise_logs
where session_id in (
    select s.session_id
    from workout_sessions s
    where s.session_id = '55111111-1111-1111-1111-111111111111'
      and s.created_by = 'sample-data');

delete from workout_sessions
where session_id = '55111111-1111-1111-1111-111111111111'
  and created_by = 'sample-data';

delete from workout_plan_exercise_set_targets
where plan_exercise_id in (
    select e.plan_exercise_id
    from workout_plan_exercises e
    join workout_plans p on p.plan_id = e.plan_id
    where p.plan_id = '52111111-1111-1111-1111-111111111111'
      and p.created_by = 'sample-data');

delete from workout_plan_exercises
where plan_id in (
    select p.plan_id
    from workout_plans p
    where p.plan_id = '52111111-1111-1111-1111-111111111111'
      and p.created_by = 'sample-data');

delete from workout_plans
where plan_id = '52111111-1111-1111-1111-111111111111'
  and created_by = 'sample-data';

delete from exercises
where exercise_id = '51111111-1111-1111-1111-111111111114'
  and created_by = 'sample-data';
