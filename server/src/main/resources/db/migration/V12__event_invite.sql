-- The token behind a QR code on a banner.
--
-- ADR 0013 decision 3: somebody signs up by scanning a code and giving an address. This is
-- what the code encodes, and it is the first thing in this schema an unauthenticated caller
-- can reach.
--
-- Deliberately outside the studio policies, for the same reason `password_reset` and the
-- authentication tables are (ADR 0009 decision 7). A policy keyed on `app.studio_id` cannot
-- guard the lookup that *establishes* which studio a request concerns, and this lookup runs
-- before anybody has said who they are. The narrowness is the safety: exactly one query
-- reads this table, and it is `WHERE token = ?`.
--
-- The token is therefore the only secret. It is 128 bits from a secure source, so it cannot
-- be guessed, and it is per event, so holding one says nothing about any other event or
-- studio.
CREATE TABLE event_invite (
    token       text   NOT NULL PRIMARY KEY,
    studio_id   text   NOT NULL REFERENCES studio(id),
    event_id    text   NOT NULL REFERENCES event(id),
    created_at  bigint NOT NULL,
    -- Set when the studio withdraws it.
    --
    -- A QR code printed on a banner cannot be recalled, so the only way to stop honouring it
    -- is here. Kept as a row rather than deleted: somebody scanning a withdrawn code should
    -- meet a closed sign-up rather than a page that cannot tell them apart from a typo.
    revoked_at  bigint
);

-- One live invite per event. A second would mean two codes for one sign-up, and no way to
-- tell which banner somebody scanned when one of them has to be withdrawn.
CREATE UNIQUE INDEX event_invite_live_idx ON event_invite(event_id) WHERE revoked_at IS NULL;

-- For the studio's own screen, which lists an event's invite alongside it.
CREATE INDEX event_invite_studio_idx ON event_invite(studio_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE event_invite TO yellowtrack_app;
