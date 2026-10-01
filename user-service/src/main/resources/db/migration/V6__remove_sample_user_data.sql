-- Removes the rows V2 seeded for the two sample accounts. Every statement keeps the created_by marker so that
-- a row someone has since replaced is left alone, and each one is a no-op when the rows are already gone.
delete from user_goals
where goal_id in (
    '81111111-1111-1111-1111-111111111111',
    '81111111-1111-1111-1111-111111111112',
    '81111111-1111-1111-1111-111111111113',
    '81111111-1111-1111-1111-111111111114')
  and created_by = 'sample-data';

delete from body_metric_entries
where metric_id in (
    '71111111-1111-1111-1111-111111111111',
    '71111111-1111-1111-1111-111111111112')
  and created_by = 'sample-data';

delete from user_notification_preferences
where user_id in (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222')
  and created_by = 'sample-data';

delete from user_nutrition_preferences
where user_id in (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222')
  and created_by = 'sample-data';

delete from user_training_preferences
where user_id in (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222')
  and created_by = 'sample-data';

delete from user_app_preferences
where user_id in (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222')
  and created_by = 'sample-data';

delete from user_profiles
where user_id in (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222')
  and created_by = 'sample-data';
