alter table workout_exercise_set_logs
    add column status varchar(20) not null default 'PENDING';

-- Keep the WO-6 logged semantics intact: historical real performed rows stay
-- DONE, while plan/repeat seeded template rows remain PENDING.
update workout_exercise_set_logs
set status = case when logged = true then 'DONE' else 'PENDING' end;
