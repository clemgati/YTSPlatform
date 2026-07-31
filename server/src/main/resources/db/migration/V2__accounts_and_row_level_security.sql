-- Accounts, sessions, and the tenant boundary.
--
-- See `docs/adr/0009-accounts-authentication-and-tenant-isolation.md`. The short version:
-- `studio_id` has been on every row since the first migration and has never referenced
-- anything. This gives it something to reference, gives a person a way to sign in, and
-- moves the tenant boundary out of the application's WHERE clauses and into Postgres.

-- --------------------------------------------------------------------------------------
-- Who exists
-- --------------------------------------------------------------------------------------

-- The tenant. Every `studio_id` in the schema has been pointing at this row since the
-- first migration, in the manner of a foreign key nobody had written down yet.
CREATE TABLE studio (
    id         text   NOT NULL PRIMARY KEY,
    name       text   NOT NULL,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    deleted_at bigint,
    version    bigint NOT NULL DEFAULT 1
);

-- A person who can sign in.
--
-- `password_hash` is nullable on purpose (ADR 0009 decision 2): an account that
-- authenticates through Apple or Google would have no password, and that is a complete
-- account rather than a broken one. Adding a `federated_identity` table later reshapes
-- nothing here.
--
-- `email` is stored already lowercased and trimmed — see `Accounts.normaliseEmail`. Doing
-- it at the boundary rather than with a functional index means the uniqueness constraint
-- is the plain one it appears to be, and two accounts cannot differ only by capitals.
CREATE TABLE account (
    id            text   NOT NULL PRIMARY KEY,
    email         text   NOT NULL UNIQUE,
    name          text   NOT NULL,
    password_hash text,
    created_at    bigint NOT NULL,
    updated_at    bigint NOT NULL,
    deleted_at    bigint,
    version       bigint NOT NULL DEFAULT 1
);

-- Which people belong to which studio, and as what.
--
-- A join table rather than an `account.studio_id` column, because 0.8.0 adds second
-- shooters and editors: the difference between the two shapes is the difference between
-- an additive change and a data migration over every account by then.
--
-- `role` is 'Owner' for everybody today. A column with one value is not yet doing work,
-- but it is where the work goes.
CREATE TABLE studio_member (
    id         text   NOT NULL PRIMARY KEY,
    studio_id  text   NOT NULL REFERENCES studio(id),
    account_id text   NOT NULL REFERENCES account(id),
    role       text   NOT NULL DEFAULT 'Owner',
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    deleted_at bigint,
    version    bigint NOT NULL DEFAULT 1,
    UNIQUE (studio_id, account_id)
);

-- A signed-in device.
--
-- `token_digest` is the SHA-256 of the token, never the token: a copy of this table is
-- then a list of sessions rather than a set of working keys, for the same reason
-- `password_hash` is not a password.
--
-- Sessions are long-lived because the application is offline-first — a device in a field
-- all day reconciles that evening without a sign-in prompt — and that is affordable only
-- because `revoked_at` makes a lost phone a solved problem rather than a wait.
CREATE TABLE auth_session (
    id           text   NOT NULL PRIMARY KEY,
    account_id   text   NOT NULL REFERENCES account(id),
    studio_id    text   NOT NULL REFERENCES studio(id),
    token_digest text   NOT NULL UNIQUE,
    created_at   bigint NOT NULL,
    last_used_at bigint,
    expires_at   bigint NOT NULL,
    revoked_at   bigint
);

CREATE INDEX studio_member_account_idx ON studio_member(account_id, deleted_at);
CREATE INDEX auth_session_account_idx ON auth_session(account_id);

-- --------------------------------------------------------------------------------------
-- The role the application connects as
-- --------------------------------------------------------------------------------------
--
-- Postgres exempts superusers, roles holding BYPASSRLS, and by default a table's own
-- owner from row level security. Migrations run as the owner, and on a Homebrew
-- installation the developer's own role is a superuser — so without a second role, every
-- policy below would be silently inert on exactly the machine they were written on.
--
-- Created without LOGIN. Deployment grants that separately:
--
--     ALTER ROLE yellowtrack_app LOGIN PASSWORD '...';
--
-- so no credential is implied by anything in this repository. Where the connecting role is
-- more privileged than this one — development, and the tests — `withStudio` issues
-- `SET LOCAL ROLE yellowtrack_app` before it touches business data, so the policies apply
-- there too.
--
-- Roles are cluster-wide while migrations run per database, so this must tolerate already
-- existing: `yellowtrack_dev` and `yellowtrack_test` both run it.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'yellowtrack_app') THEN
        CREATE ROLE yellowtrack_app NOLOGIN;
    END IF;
END;
$$;

GRANT USAGE ON SCHEMA public TO yellowtrack_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO yellowtrack_app;
-- `assign_server_seq` runs as the invoking role, so the cursor needs to be drawable by it.
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO yellowtrack_app;

-- Flyway's own bookkeeping is the migration role's business, not the application's.
-- Guarded so the file can also be applied by hand, where Flyway's table is absent.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_tables
        WHERE schemaname = current_schema() AND tablename = 'flyway_schema_history'
    ) THEN
        REVOKE ALL ON TABLE flyway_schema_history FROM yellowtrack_app;
    END IF;
END;
$$;

-- Tables added by later migrations, so a new table is not silently unreachable. Scoped to
-- the role running this migration, which is the role that will run the later ones.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO yellowtrack_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE ON SEQUENCES TO yellowtrack_app;

-- --------------------------------------------------------------------------------------
-- The tenant boundary
-- --------------------------------------------------------------------------------------
--
-- Every business table is keyed to a studio and gets a policy comparing that key against
-- `app.studio_id`, which each request sets for the length of its transaction.
--
-- `current_setting(..., true)` is the missing-ok form: it yields NULL rather than raising
-- when nothing has been set. `studio_id = NULL` is NULL rather than true, so a query that
-- forgot to set the studio matches no rows. **A forgotten SET LOCAL returns nothing rather
-- than everything.** That is the entire reason this lives in the database.
--
-- WITH CHECK is spelled out even though Postgres already defaults it to the USING
-- expression. Deleting it changes no behaviour — that was checked — so it is documentation
-- rather than the thing doing the work, and should not be mistaken for it. It earns its
-- place by putting the write rule next to the read rule, so that anyone narrowing one has
-- to decide about the other rather than silently inherit it.
--
-- Driven off the presence of a `studio_id` column for the same reason the sync trigger is:
-- a list of table names is a second place to remember. The two authentication tables that
-- also carry `studio_id` are excluded deliberately — a policy keyed on `app.studio_id`
-- cannot guard the lookup that decides what `app.studio_id` should be (ADR 0009 decision
-- 7). `RowLevelSecurityTest` asserts this set is exactly what it claims to be.
DO $$
DECLARE
    scoped_table text;
    unguarded CONSTANT text[] := ARRAY['studio_member', 'auth_session'];
BEGIN
    FOR scoped_table IN
        SELECT table_name
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND column_name = 'studio_id'
          AND table_name <> ALL (unguarded)
        ORDER BY table_name
    LOOP
        -- `studio_id` finally references a row rather than an understanding.
        EXECUTE format(
            'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (studio_id) REFERENCES studio(id)',
            scoped_table, scoped_table || '_studio_fk'
        );

        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', scoped_table);
        -- FORCE so the policy binds the table's owner too. Without it, anything that ever
        -- runs application queries as the owner quietly sees everything.
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
