-- Events, and how a photograph finds the person in it.
--
-- ADR 0013 decision 4. Every event has a gallery; a station is a period a photographer opens
-- inside it and closes again. A photograph is routed by one question — was a slot open on
-- the source this arrived from — and these tables exist to answer it.
--
-- Server-only, and not mirrored to devices. Under ADR 0012 the studio application writes
-- through the server and caches for reading, so a photographer opening a slot is an API call
-- rather than a local row that synchronises later. That matters more here than elsewhere: a
-- slot opened on a device that is offline would route photographs by a state the server does
-- not share, and the failure would be somebody receiving another person's headshot.

-- An event a studio is photographing. A headshot day, a conference, a wedding, a festival.
CREATE TABLE event (
    id          text   NOT NULL PRIMARY KEY,
    studio_id   text   NOT NULL REFERENCES studio(id),
    name        text   NOT NULL,
    starts_at   bigint,
    created_at  bigint NOT NULL,
    updated_at  bigint NOT NULL,
    deleted_at  bigint
);

CREATE INDEX event_studio_idx ON event(studio_id);

-- Somebody who signed up to receive photographs.
--
-- An address rather than an account (ADR 0013 decision 7): a person photographed once at a
-- conference should not be made to hold a credential for it.
CREATE TABLE event_registration (
    id            text   NOT NULL PRIMARY KEY,
    studio_id     text   NOT NULL REFERENCES studio(id),
    event_id      text   NOT NULL REFERENCES event(id),
    email         text   NOT NULL,
    name          text,
    registered_at bigint NOT NULL,
    -- One registration per address per event. Somebody who scans the QR code twice is the
    -- same person, and two registrations would mean two half-galleries.
    UNIQUE (event_id, email)
);

CREATE INDEX event_registration_event_idx ON event_registration(event_id);

-- A period during which one photographer is shooting one subject at a time.
--
-- `source_key` is what binds a station to an ingest source — a watched folder, and through
-- it one camera. Without it, a second photographer roaming the same wedding would have their
-- candids swallowed by the first photographer's open slot.
CREATE TABLE event_station (
    id         text   NOT NULL PRIMARY KEY,
    studio_id  text   NOT NULL REFERENCES studio(id),
    event_id   text   NOT NULL REFERENCES event(id),
    name       text   NOT NULL,
    source_key text   NOT NULL,
    opened_at  bigint NOT NULL,
    closed_at  bigint
);

-- One open station per source, **per studio**. Two open stations on one camera would make
-- the routing question ambiguous and the answer arbitrary rather than wrong-and-obvious.
--
-- Scoped by studio because a source key is a name a studio gives a folder, not a global
-- identifier. Unscoped, two studios that both call a folder "Camera A" collide: the second
-- cannot open a station at all, and the unique violation tells it that somebody else has one
-- open — a cross-tenant leak that row level security does not cover, because a constraint is
-- checked against rows the querying studio cannot see. Found by a test, not by reasoning.
CREATE UNIQUE INDEX event_station_open_source_idx
    ON event_station(studio_id, source_key)
    WHERE closed_at IS NULL;

-- One attendee's turn at a station.
--
-- `delivered_at` is set when their photographs have been sent. Nothing is delivered until
-- the slot is closed, which is what leaves a moment in which a mis-advance is recoverable.
CREATE TABLE event_slot (
    id              text   NOT NULL PRIMARY KEY,
    studio_id       text   NOT NULL REFERENCES studio(id),
    station_id      text   NOT NULL REFERENCES event_station(id),
    registration_id text   NOT NULL REFERENCES event_registration(id),
    opened_at       bigint NOT NULL,
    closed_at       bigint,
    delivered_at    bigint
);

-- One open slot per station. A photographer shoots one subject, then advances.
CREATE UNIQUE INDEX event_slot_open_station_idx
    ON event_slot(station_id)
    WHERE closed_at IS NULL;

CREATE INDEX event_slot_registration_idx ON event_slot(registration_id);

-- A photograph, and where it belongs.
--
-- `slot_id` null means the event's gallery, which is the default destination and the whole
-- of a roaming event. Not null means it belongs to one person.
--
-- `published_at` is the studio's decision, not the camera's: nothing reaches a gallery until
-- somebody has looked. An event is not an unreviewed feed of whatever came off a card.
CREATE TABLE event_photo (
    id               text   NOT NULL PRIMARY KEY,
    studio_id        text   NOT NULL REFERENCES studio(id),
    event_id         text   NOT NULL REFERENCES event(id),
    stored_object_id text   NOT NULL REFERENCES stored_object(id),
    slot_id          text   REFERENCES event_slot(id),
    captured_at      bigint NOT NULL,
    published_at     bigint
);

CREATE INDEX event_photo_event_idx ON event_photo(event_id, captured_at);
CREATE INDEX event_photo_slot_idx ON event_photo(slot_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE event TO yellowtrack_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE event_registration TO yellowtrack_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE event_station TO yellowtrack_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE event_slot TO yellowtrack_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE event_photo TO yellowtrack_app;

-- Every one of these belongs to a studio, and a query that forgets to say which must return
-- nothing rather than everything. See ADR 0009 and `RowLevelSecurityTest`.
DO $$
DECLARE
    scoped_table text;
BEGIN
    FOREACH scoped_table IN ARRAY ARRAY['event', 'event_registration', 'event_station', 'event_slot', 'event_photo']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', scoped_table);
        -- FORCE so the policy binds the table's owner too, which is what makes it hold when
        -- migrations and requests connect as different roles.
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', scoped_table);
        EXECUTE format(
            'CREATE POLICY %I ON %I
                 USING (studio_id = current_setting(''app.studio_id'', true))
                 WITH CHECK (studio_id = current_setting(''app.studio_id'', true))',
            scoped_table || '_studio_isolation', scoped_table
        );
    END LOOP;
END;
$$;
