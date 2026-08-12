-- V14's backfill updated nothing, and said nothing about it.
--
-- `event_registration` is FORCE ROW LEVEL SECURITY with a policy of
-- `studio_id = current_setting('app.studio_id', true)`. A migration sets no `app.studio_id`,
-- so that predicate is NULL, so it is not true, so the UPDATE matched no rows at all. The
-- unique index created straight afterwards raised nothing either, because NULLs do not
-- collide with one another.
--
-- The result on the deployment was sixteen registrations of eighteen holding no number, with
-- every step reporting success. V11 says why FORCE is there — "so the policy binds the
-- table's owner too, which is what makes it hold when migrations and requests connect as
-- different roles" — and this is that working exactly as designed against a migration written
-- as though it would not.

ALTER TABLE event_registration DISABLE ROW LEVEL SECURITY;

-- Distinct within an event, and distinct from the numbers already issued.
--
-- Two things a single UPDATE gets wrong here. `random()` is evaluated per row, so a "random
-- base plus row number" is not a constant base at all and uniqueness would be luck rather
-- than construction. And the rows being filled in are not the only rows: this deployment
-- already has registrations numbered by the application, and a backfill that ignored them
-- could pick one of theirs and fail the whole migration on the unique index.
--
-- So each row is given a number that is checked against what the event already holds. It is
-- a loop, which for a table of this size is nothing, and it is obviously correct — which
-- matters more here than elegance, since the last version of this was quietly wrong.
DO $$
DECLARE
    person record;
    candidate int;
BEGIN
    FOR person IN SELECT id, event_id FROM event_registration WHERE number IS NULL LOOP
        LOOP
            candidate := 10000 + floor(random() * 90000)::int;

            EXIT WHEN NOT EXISTS (
                SELECT 1 FROM event_registration
                WHERE event_id = person.event_id AND number = candidate
            );
        END LOOP;

        UPDATE event_registration SET number = candidate WHERE id = person.id;
    END LOOP;
END
$$;

-- The line that would have made V14 fail loudly instead of quietly.
--
-- Every row must have one now, so a backfill that silently does nothing cannot pass unnoticed
-- a second time — and neither can an insert that forgets to allocate one. The application has
-- always set it; this is what makes that a rule rather than a habit.
ALTER TABLE event_registration ALTER COLUMN number SET NOT NULL;

ALTER TABLE event_registration ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_registration FORCE ROW LEVEL SECURITY;
