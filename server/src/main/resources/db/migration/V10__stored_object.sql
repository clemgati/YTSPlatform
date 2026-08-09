-- What a studio has in object storage, and where.
--
-- ADR 0013 puts photographs in S3 rather than in Postgres, which breaks a promise made in
-- ADR 0009 unless something keeps track: a studio that deletes itself is told its records go
-- for good after thirty days, and a purge that only deletes rows would leave every
-- photograph in a bucket forever. This table is what makes that promise keepable — it is the
-- only record of which keys belong to whom.
--
-- Deliberately built before there is anything to put in it. The alternative is adding it
-- alongside the first upload, at which point the purge is being changed under a feature
-- rather than before it, and a purge that quietly misses objects is exactly the kind of
-- silent failure this codebase keeps finding.

CREATE TABLE stored_object (
    id           text   NOT NULL PRIMARY KEY,
    studio_id    text   NOT NULL REFERENCES studio(id),
    -- The key in the bucket. Unique across the deployment, because two studios' keys live in
    -- one bucket and a collision would hand one studio another's photograph.
    object_key   text   NOT NULL UNIQUE,
    content_type text   NOT NULL,
    -- Bytes. Kept so a studio's usage can be answered without asking S3, which charges for
    -- listing and would make the question expensive enough that nobody asks it.
    size_bytes   bigint NOT NULL,
    created_at   bigint NOT NULL,
    -- Set when the object is known to be gone from the bucket. A row is removed by the purge
    -- only after its object is deleted, so a crash between the two leaves a row that says
    -- what still needs removing rather than an orphan nobody can find.
    deleted_at   bigint
);

CREATE INDEX stored_object_studio_idx ON stored_object(studio_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE stored_object TO yellowtrack_app;

-- Under the same policy as every other business table. An object belongs to one studio, and
-- a query that forgets to name a studio must return nothing rather than everything — see
-- ADR 0009 and `RowLevelSecurityTest`.
ALTER TABLE stored_object ENABLE ROW LEVEL SECURITY;
-- FORCE so the policy binds the table's owner too, which is what makes it hold when
-- migrations and requests connect as different roles.
ALTER TABLE stored_object FORCE ROW LEVEL SECURITY;

CREATE POLICY stored_object_studio_isolation ON stored_object
    USING (studio_id = current_setting('app.studio_id', true))
    WITH CHECK (studio_id = current_setting('app.studio_id', true));
