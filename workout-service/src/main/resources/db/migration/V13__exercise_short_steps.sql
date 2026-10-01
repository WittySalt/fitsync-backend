-- Short versions of an exercise's guide, shown one step at a time while the person trains and read
-- aloud later. The catalogue's own instructions are long paragraphs, so an exercise can carry two to
-- four steps of about fifteen words each. The table is keyed by the exercise code, not its id,
-- because the catalogue import can recreate catalogue rows and a custom exercise exists once per
-- user under the same code. Nothing in the application writes this table; the rows come from here.
create table exercise_short_steps (
    exercise_code varchar(100) not null,
    step_number integer not null,
    step_text varchar(200) not null,
    primary key (exercise_code, step_number)
);

insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_bench_press', 1, 'Lie back with the dumbbells at chest height, palms facing your feet.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_bench_press', 2, 'Breathe out and press both up until the arms are straight.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_bench_press', 3, 'Lower for two counts until the elbows are level with the bench.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_bench_press', 4, 'Keep the feet flat and the shoulder blades pressed into the bench.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('barbell_bench_press_-_medium_grip', 1, 'Lie with the eyes under the bar, hands a little wider than the shoulders.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('barbell_bench_press_-_medium_grip', 2, 'Lower the bar to the middle of the chest for two counts.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('barbell_bench_press_-_medium_grip', 3, 'Breathe out and press it straight back up.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('barbell_bench_press_-_medium_grip', 4, 'Keep the feet flat and the shoulder blades squeezed together.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('bent_over_two-dumbbell_row', 1, 'Hinge at the hips with a flat back, dumbbells under the shoulders.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('bent_over_two-dumbbell_row', 2, 'Breathe out and pull the elbows back past your sides.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('bent_over_two-dumbbell_row', 3, 'Squeeze the shoulder blades together for a second at the top.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('bent_over_two-dumbbell_row', 4, 'Lower slowly without moving the back.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('goblet_squat', 1, 'Hold the weight at your chest, feet a little wider than the hips.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('goblet_squat', 2, 'Sit down between the heels with the chest up.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('goblet_squat', 3, 'At the bottom, push the knees out with the elbows.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('goblet_squat', 4, 'Drive through the whole foot to stand.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('bodyweight_squat', 1, 'Feet a little wider than the hips, toes turned slightly out.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('bodyweight_squat', 2, 'Sit back and down as if onto a low chair, chest up.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('bodyweight_squat', 3, 'Go as low as you can with the heels on the floor.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('bodyweight_squat', 4, 'Push through the whole foot to stand tall.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('barbell_squat', 1, 'Rest the bar on the upper back, feet about shoulder width.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('barbell_squat', 2, 'Brace the stomach, then sit down between the heels.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('barbell_squat', 3, 'Keep the knees in line with the toes.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('barbell_squat', 4, 'Drive up through the whole foot, chest leading.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_lunges', 1, 'Stand tall with a dumbbell in each hand.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_lunges', 2, 'Step forward and lower until both knees bend to about 90 degrees.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_lunges', 3, 'Keep the front knee over the ankle and the torso upright.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_lunges', 4, 'Push through the front heel to step back, then switch legs.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('romanian_deadlift', 1, 'Hold the bar at the hips, knees slightly bent.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('romanian_deadlift', 2, 'Push the hips back and slide the bar down the thighs.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('romanian_deadlift', 3, 'Stop when you feel the stretch behind the legs, back flat.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('romanian_deadlift', 4, 'Squeeze the glutes to bring the hips forward and stand.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('barbell_deadlift', 1, 'Feet under the bar, hands just outside the legs.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('barbell_deadlift', 2, 'Flatten the back and brace before the bar leaves the floor.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('barbell_deadlift', 3, 'Push the floor away and stand up, bar close to the legs.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('barbell_deadlift', 4, 'Lower it the same way, hips back first.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_shoulder_press', 1, 'Sit tall with the dumbbells at shoulder height, palms forward.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_shoulder_press', 2, 'Breathe out and press up until the arms are straight.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_shoulder_press', 3, 'Lower under control back to the shoulders.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_shoulder_press', 4, 'Keep the lower back against the bench.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('wide-grip_lat_pulldown', 1, 'Hold the bar wider than the shoulders, thighs under the pad.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('wide-grip_lat_pulldown', 2, 'Lean back slightly and pull the bar to the top of the chest.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('wide-grip_lat_pulldown', 3, 'Lead with the elbows and squeeze the back at the bottom.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('wide-grip_lat_pulldown', 4, 'Let the bar rise slowly until the arms are straight.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_bicep_curl', 1, 'Stand tall, dumbbells at your sides, palms forward.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_bicep_curl', 2, 'Curl them up while the elbows stay at your sides.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_bicep_curl', 3, 'Squeeze at the top for a moment.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('dumbbell_bicep_curl', 4, 'Lower slowly until the arms are straight.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('pushups', 1, 'Hands a little wider than the shoulders, body in a straight line.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('pushups', 2, 'Lower until the chest is just above the floor.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('pushups', 3, 'Breathe out and press back up.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('pushups', 4, 'Keep the hips in line, and drop to the knees if the form slips.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('plank', 1, 'Forearms on the floor, elbows under the shoulders.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('plank', 2, 'Make a straight line from the head to the heels.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('plank', 3, 'Brace the stomach and breathe steadily.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('plank', 4, 'Stop when the hips start to drop.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('crunches', 1, 'Lie on your back, knees bent, hands lightly behind the head.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('crunches', 2, 'Breathe out and curl the shoulders off the floor.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('crunches', 3, 'Keep the lower back on the floor and the neck relaxed.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('crunches', 4, 'Lower slowly to the start.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('mountain_climbers', 1, 'Start in a high plank, hands under the shoulders.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('mountain_climbers', 2, 'Drive one knee towards the chest, then switch quickly.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('mountain_climbers', 3, 'Keep the hips low and level.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('mountain_climbers', 4, 'Breathe steadily through the whole set.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('jogging_treadmill', 1, 'Walk for a minute or two to warm up.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('jogging_treadmill', 2, 'Run upright with the eyes ahead and the arms relaxed.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('jogging_treadmill', 3, 'Land under the hips with short, light steps.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('jogging_treadmill', 4, 'Slow to a walk for the last few minutes.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('DEMO_TREADMILL', 1, 'Walk for a minute or two to warm up.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('DEMO_TREADMILL', 2, 'Run upright with the eyes ahead and the arms relaxed.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('DEMO_TREADMILL', 3, 'Land under the hips with short, light steps.');
insert into exercise_short_steps (exercise_code, step_number, step_text) values ('DEMO_TREADMILL', 4, 'Slow to a walk for the last few minutes.');
