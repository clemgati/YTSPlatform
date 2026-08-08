-- What SES said happened to a message after it accepted it.
--
-- `MailHealth` watches the SMTP conversation and can only see the moment of handing over:
-- a refusal, a bad credential, an unreachable host. SES accepts first and reports the
-- outcome afterwards, out of band, so a bounce to a mistyped domain looks exactly like a
-- success from there. `docs/DEPLOYMENT.md` said this was unwatched; this table is where it
-- stops being unwatched.
--
-- Deliveries are recorded as well as failures, and that is the point rather than
-- completeness for its own sake. "SMTP accepted it" and "somebody received it" are
-- different claims, and until now only the first could be made.

CREATE TABLE mail_notification (
    -- The SNS MessageId. SNS retries on any non-2xx and may deliver the same notification
    -- more than once even after a 200, so recording has to be idempotent or a single
    -- bounce becomes a rising count that nobody can trust.
    id           text   NOT NULL,
    -- One notification can name several recipients, so the message id alone is not unique.
    -- Learned from the shape of the payload rather than from a broken deployment.
    recipient    text   NOT NULL,
    -- 'Delivery' | 'Bounce' | 'Complaint'.
    kind         text   NOT NULL,
    -- 'Permanent' or 'Transient' for a bounce, the feedback type for a complaint, null for
    -- a delivery. The distinction matters: a transient bounce is a full mailbox and a
    -- permanent one is an address that will never work.
    subtype      text,
    -- The diagnostic SES passed on, truncated by the writer. Kept because "bounced" on its
    -- own has never helped anybody work out why.
    detail       text,
    -- When SES says it happened, not when this row was written. A retry days later must not
    -- look like a fresh bounce.
    occurred_at  bigint NOT NULL,
    recorded_at  bigint NOT NULL,

    PRIMARY KEY (id, recipient)
);

-- Answering "how many bounces recently" and "has anything been delivered", which are the
-- two questions /ready asks.
CREATE INDEX mail_notification_kind_idx ON mail_notification(kind, occurred_at DESC);
-- Answering "what happened to mail for this address", which is the question asked when one
-- studio says a reset never arrived.
CREATE INDEX mail_notification_recipient_idx ON mail_notification(recipient);

-- Outside the studio policies, for the same reason the authentication tables are: this is
-- operational fact about the deployment, not a row belonging to one studio. A bounce for a
-- client's address is not the client's studio's data to be scoped by.
--
-- No UPDATE or DELETE: a notification is something that happened. Nothing in the
-- application has any business rewriting it, and the grant says so.
GRANT SELECT, INSERT ON TABLE mail_notification TO yellowtrack_app;
