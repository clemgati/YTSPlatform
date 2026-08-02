-- Where the losing side of a conflict is kept.
--
-- ADR 0008 decision 3: last-write-wins is chosen because it is simple, and it is only
-- defensible while the version it discarded can still be recovered by whoever wrote it.
-- Silent last-write-wins is a data-loss feature wearing the costume of a synchronisation
-- strategy.
--
-- Mirrored on the device (SQLDelight migration 14 to 15) rather than server-only, because
-- a conflict nobody is shown is exactly the thing that ADR called worse than useless. The
-- server raises the row; the device pulls it like any other.

CREATE TABLE sync_conflict (
    id              text   NOT NULL PRIMARY KEY,
    studio_id       text   NOT NULL,
    entity_table    text   NOT NULL,
    entity_id       text   NOT NULL,
    -- Both payloads in full, not a diff. What a studio needs is to read the version it
    -- lost and retype the part that mattered, and a diff of two JSON documents is not
    -- that.
    losing_payload  text   NOT NULL,
    winning_payload text   NOT NULL,
    detected_at     bigint NOT NULL,
    resolved_at     bigint,
    created_at      bigint NOT NULL,
    updated_at      bigint NOT NULL,
    deleted_at      bigint,
    version         bigint NOT NULL DEFAULT 1,
    server_seq      bigint NOT NULL
);

CREATE INDEX sync_conflict_studio_idx ON sync_conflict(studio_id, resolved_at);

-- The loops in V1 and V2 ran over the tables that existed when they ran, so a table added
-- later has to say all of this for itself. `SchemaDriftTest` and `RowLevelSecurityTest`
-- are what notice when it does not: a new table with no trigger never reaches a device,
-- and one with no policy is visible to every studio.
CREATE INDEX sync_conflict_sync_idx ON sync_conflict(studio_id, server_seq);

CREATE TRIGGER sync_conflict_server_seq
    BEFORE INSERT OR UPDATE ON sync_conflict
    FOR EACH ROW EXECUTE FUNCTION assign_server_seq();

ALTER TABLE sync_conflict ADD CONSTRAINT sync_conflict_studio_fk
    FOREIGN KEY (studio_id) REFERENCES studio(id);

ALTER TABLE sync_conflict ENABLE ROW LEVEL SECURITY;
ALTER TABLE sync_conflict FORCE ROW LEVEL SECURITY;

CREATE POLICY sync_conflict_studio_isolation ON sync_conflict
    USING (studio_id = current_setting('app.studio_id', true))
    WITH CHECK (studio_id = current_setting('app.studio_id', true));

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE sync_conflict TO yellowtrack_app;
