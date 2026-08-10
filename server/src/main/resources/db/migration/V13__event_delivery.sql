-- Handing a sitting's photographs to the person in them.
--
-- ADR 0013: a slot's photographs are held until the slot is closed and the studio has
-- released them. A mis-advanced slot sends one person's headshot to another, which is a
-- privacy incident rather than a glitch, so delivery is an act somebody performs and not a
-- consequence of time passing.

-- `event_slot.delivered_at` already exists — V11 declared it when the tables were designed,
-- against exactly this. Nothing to add there.

-- Where somebody's own photographs live.
--
-- A token per registration rather than per slot: one person at one event has one gallery,
-- and a second sitting adds to it rather than sending them a second link.
--
-- Outside the studio policies for the same reason `event_invite` is, and at the same cost.
-- The lookup resolves a token to a studio and so runs before anybody has said who they are,
-- which a policy keyed on `app.studio_id` cannot guard. Every studio-scoped query on this
-- table must therefore name the studio itself — see `EventInvites` for the two places that
-- was got wrong when the pattern was introduced.
CREATE TABLE event_gallery (
    token           text   NOT NULL PRIMARY KEY,
    studio_id       text   NOT NULL REFERENCES studio(id),
    registration_id text   NOT NULL REFERENCES event_registration(id),
    created_at      bigint NOT NULL,
    -- Set when somebody asks to be forgotten, or the studio withdraws the gallery. The link
    -- is in an inbox and cannot be recalled, so this is the only way to stop honouring it.
    revoked_at      bigint
);

-- One live gallery per registration, so a second delivery reuses the link already sent
-- rather than stranding it.
CREATE UNIQUE INDEX event_gallery_live_idx ON event_gallery(registration_id) WHERE revoked_at IS NULL;

CREATE INDEX event_gallery_studio_idx ON event_gallery(studio_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE event_gallery TO yellowtrack_app;
