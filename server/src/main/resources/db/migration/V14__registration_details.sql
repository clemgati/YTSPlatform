-- Who somebody is, when two of them share a name.
--
-- A sign-up used to be an address and an optional name. Two John Smiths at one event were
-- then indistinguishable to the photographer seating them, and the studio that ran this found
-- exactly that: four names against two addresses, and no way to tell which sitting belonged
-- to whom.

-- The name in parts, because "John Smith" cannot be sorted, greeted, or matched against a
-- list a client sends afterwards.
--
-- Nullable, and deliberately. The API requires both on a new sign-up; these columns hold
-- registrations that predate that requirement, and a NOT NULL here would mean either
-- refusing to migrate or inventing a family name for somebody who never gave one.
ALTER TABLE event_registration ADD COLUMN given_name  text;
ALTER TABLE event_registration ADD COLUMN family_name text;

-- Optional, and the studio's alone.
--
-- Given so a guest can be sent their link by message later; nothing sends one yet. It is
-- shown to the studio because it is the other thing that tells two people of the same name
-- apart, and it is never shown to another guest.
ALTER TABLE event_registration ADD COLUMN phone text;

-- A short number, unique within its event.
--
-- Five digits so it can be said out loud across a room and written on a form, and random
-- rather than sequential so it does not publish how many people have signed up. Unique per
-- event rather than globally: it is spoken in one room on one day, and a number that had to
-- be unique across every studio would need to be longer than a person will read back.
ALTER TABLE event_registration ADD COLUMN number integer;

-- Existing registrations get one too, so the studio's list has no gaps in it. `random()`
-- gives 10000..99999; the unique index below is created afterwards so a collision here fails
-- loudly rather than being carried forward.
UPDATE event_registration SET number = 10000 + floor(random() * 90000)::int WHERE number IS NULL;

-- One number per person per event. The application retries on collision, and this is what
-- makes that retry necessary rather than optional.
CREATE UNIQUE INDEX event_registration_number_idx ON event_registration(event_id, number);
