-- Removes the two sample accounts seeded by V4. The created_by marker keeps the delete from touching
-- a row that someone has since replaced under the same id, and the delete is a no-op once they are gone.
delete from app_users
where id in (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222')
  and created_by = 'sample-data';
