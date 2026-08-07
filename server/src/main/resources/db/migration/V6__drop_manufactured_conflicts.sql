-- Removes every recorded conflict, because they describe edits that did not happen.
--
-- ADR 0012 decision 4. A studio with one device was holding 154, the screen told it "you
-- edited these on two devices at once", and most said the set-aside version could not be read
-- back. They were manufactured by a fault since fixed: a pull overwrote a locally-deleted row,
-- the outbox retried at the version it had just received, and the server recorded a conflict
-- against itself — every cycle, for every unsent row.
--
-- All of them rather than the ones that can be shown to be false. Telling the two apart means
-- reading a payload that mostly will not parse, and a genuine conflict from this period would
-- have arrived through the same broken path. Anything a studio actually needs is still in the
-- row itself; what is discarded is a claim about how it got there.
--
-- The table stays for now. It goes with the version negotiation once every entity writes
-- online, which is the last step of ADR 0012 — this only empties it.

DELETE FROM sync_conflict;
