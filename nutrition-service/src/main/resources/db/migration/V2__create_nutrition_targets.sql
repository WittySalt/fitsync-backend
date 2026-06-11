create table nutrition_targets (
    user_id uuid primary key,
    calorie_goal_id uuid,
    calorie_target integer,
    calorie_target_date date,
    protein_goal_id uuid,
    protein_target numeric(10, 2),
    protein_target_date date,
    updated_at timestamp with time zone not null
);
