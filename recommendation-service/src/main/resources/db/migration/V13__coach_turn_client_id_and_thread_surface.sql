-- C-3: a client-chosen id on the user turn lets a retried question find the turn it already started
-- instead of creating a second one. Null for turns that carried no id. A unique constraint over
-- (thread_id, client_turn_id) is enough because null values never collide.
alter table coach_turn add column client_turn_id uuid;
alter table coach_turn add constraint uq_coach_turn_thread_client_turn unique (thread_id, client_turn_id);

-- C-9: the page a thread was started from, for the history list. Null for threads that predate it
-- or were started without a page context.
alter table coach_thread add column surface varchar(20);
