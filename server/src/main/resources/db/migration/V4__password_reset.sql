-- Codes that let somebody back into their own account.
--
-- See `docs/adr/0010-password-reset-by-emailed-code.md`. Server-only: a device has no use
-- for anybody's reset codes, including its own — the code arrives by email and is typed in.

CREATE TABLE password_reset (
    id           text   NOT NULL PRIMARY KEY,
    account_id   text   NOT NULL REFERENCES account(id),
    -- The SHA-256 of the code, never the code, for the same reason `auth_session` stores a
    -- digest: a copy of this table should not be a set of working credentials.
    code_digest  text   NOT NULL,
    created_at   bigint NOT NULL,
    expires_at   bigint NOT NULL,
    -- Set when the code is used. Single-use, so a code read from an inbox somebody else
    -- also has does not stay valid.
    consumed_at  bigint,
    -- Set when a newer request supersedes this one. Two live codes for one account is one
    -- more than anybody needs, and one more chance for the older to be the leaked one.
    superseded_at bigint
);

-- Every lookup is by digest, and there is at most one live row per account.
CREATE UNIQUE INDEX password_reset_code_idx ON password_reset(code_digest);
CREATE INDEX password_reset_account_idx ON password_reset(account_id, consumed_at);

-- Reachable by the application role, and outside the studio policies for the same reason
-- the other authentication tables are: this is what establishes who somebody is, so it
-- cannot be guarded by a policy keyed on which studio they are.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE password_reset TO yellowtrack_app;
