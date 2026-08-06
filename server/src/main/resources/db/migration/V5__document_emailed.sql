-- Records that a document was emailed to a client, and where it went.
--
-- ADR 0011 decision 5: emailing is not a state change. An invoice is *sent* when the studio
-- decides the figure is right, and emailing it twice has emailed it twice — so this sits
-- beside `status` rather than in it, and nothing derives from it.
--
-- It exists because the screen could not answer "did I press that button". The send reported
-- itself once, in a line of text that disappeared on the next reload, which is the same as
-- not reporting it for anybody who looked twice.
--
-- Two columns rather than one. A timestamp alone says a document went somewhere and not to
-- whom, and "which address did this go to" is the question actually asked six weeks later —
-- usually because nobody has paid.

ALTER TABLE invoice ADD COLUMN last_emailed_at bigint;
ALTER TABLE invoice ADD COLUMN last_emailed_to text;

ALTER TABLE quote ADD COLUMN last_emailed_at bigint;
ALTER TABLE quote ADD COLUMN last_emailed_to text;
