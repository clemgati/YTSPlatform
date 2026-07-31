# ADR 0008: Synchronise with a server-assigned sequence and visible conflicts

- Status: Proposed
- Date: 2026-07-30

## Context

ADR 0006 shaped every table for synchronisation and then deliberately stopped short of
deciding how it would work: "committing to conflict-resolution semantics before the
entities have settled would be premature." ADR 0007 repeated the deferral. The entities
have now settled — twenty-one tables across fourteen migrations, and 0.6.0 closed without
needing to reshape any of them — so the deferral has run its course.

What already exists is not in doubt. Every row carries a client-generated UUID v7 primary
key, a `studio_id`, `created_at`, `updated_at`, a nullable `deleted_at` tombstone, and an
integer `version`. Deletes are soft. An `outbox` table has existed since the first
migration and is still unused.

Three properties of this application constrain the choice more than the general literature
does.

**Offline is the normal case, not the exceptional one.** `docs/VISION.md` requires that
shoot-day tools work without a connection, because wedding venues and warehouses do not
have one. A device may be offline for a whole working day and reconcile that evening.

**Concurrent edits to the same row are rare but not absent.** A studio is usually one
person on one device. It is occasionally an owner on a laptop and a second shooter on a
phone, both touching the same shoot day. The design does not need to be optimal under
heavy contention; it needs to be *correct and explicable* under light contention.

**The stakes are uneven across fields.** Losing a re-typed session title is an
irritation. Losing a recorded payment, a signed talent release, or a media copy is a
business problem, and one the studio would not discover for months.

Sync is also the first thing in this project whose bugs are invisible. Every defect so far
has been wrong on a screen where someone could see it. A reconciliation bug silently
discards work on a device that is not currently being looked at.

## Decision

### 1. The sync cursor is a server-assigned sequence, never a timestamp

Postgres assigns a monotonically increasing `server_seq` to every row it accepts, scoped
per studio. Clients pull with `WHERE studio_id = ? AND server_seq > ?` and remember the
highest value they have seen.

Timestamp cursors lose rows. Two devices with clocks ten minutes apart, or a single device
whose clock is corrected between writes, will produce rows that fall behind a cursor that
has already advanced past them. Those rows are never pulled again and the loss is silent.
A server-assigned sequence has no such failure: the server is the only writer of the
ordering, and it is monotonic by construction.

`updated_at` is retained, but for display and for the audit trail — never for ordering and
never for reconciliation.

### 2. Conflicts are detected with `version`, not by comparing timestamps

A push carries the `version` the edit was based on. If the server's row has moved past it,
the write is a conflict. This is ordinary optimistic concurrency, and `version` already
exists on every table and is already incremented by `AuditMetadata.touched`.

### 3. Resolution is last-write-wins by arrival, and the loser is kept

The winner is whichever version the server accepted last, ordered by `server_seq`. Client
clocks take no part in the decision, so a device with a wrong clock cannot win or lose on
that basis.

**The losing version is written to a `sync_conflict` table with both payloads, and the
studio is shown that it happened.** This is the condition on which last-write-wins is
acceptable at all. Silent LWW is a data-loss feature wearing the costume of a
synchronisation strategy: it is chosen because it is easy, and it is defensible only when
the discarded work can still be recovered by the person who did it. A studio that edited a
booking on two devices should be told so, and should be able to see what the other device
said.

### 4. Tombstones win over concurrent edits

A delete that races an edit resolves to deleted. Deleting is a deliberate act and is far
less likely to be accidental than an edit is to be concurrent — and because deletes are
soft (ADR 0006), the row is recoverable rather than gone.

### 5. Append-only collections merge by union, not by row replacement

Payments on an invoice, line items, packing entries, media copies, and crew are child
records with their own UUID primary keys. They reconcile by union on `id`, not by the
parent row's last-write-wins.

Row-level LWW applied to a parent and its children would let a stale device's copy of an
invoice discard a payment recorded on another device. The asymmetry in stakes from the
Context section is the whole reason: a lost title is retyped in seconds, a lost payment is
found during a tax return, if at all.

### 6. The outbox carries the entity identity, not a payload snapshot

`outbox` already has a nullable `payload` column. It stays null: the drain re-reads the
current row at upload time.

A payload captured at queue time is a photograph of a row that has since changed. Three
edits made offline would queue three payloads and upload three times, and the intermediate
states are of no interest to anyone. Re-reading uploads what the row actually is.

### 7. Sync state needs one new table

ADR 0006 claimed that "sync arrives without a schema migration". That is very nearly true
and worth correcting: the `outbox` was built ahead of time, but nowhere was provided to
store the pull cursor. A `sync_state` table — studio, last `server_seq` seen, last
successful sync — is required, plus the `sync_conflict` table from decision 3.

Both are additive, so the claim's substance holds: no existing table changes shape.

## Consequences

### Positive

- No client clock participates in correctness. Skew, timezone changes and manual clock
  corrections cannot cause silent loss.
- Pulling is a single indexed range scan per studio, and is resumable after a failure
  without re-sending everything.
- Conflicts become a visible event with both versions retained, rather than an outcome
  nobody observes.
- `version` and the `outbox` are used as ADR 0006 intended, so the groundwork pays off.
- Payments, releases and media copies cannot be destroyed by a stale parent row.

### Negative

- `server_seq` is a Postgres-side concept the SQLite schema does not have, so the two
  schemas are no longer mirror images and the mapping has to be maintained deliberately.
- A `sync_conflict` table nobody looks at is worse than useless — it costs storage and
  implies a safety property it is not delivering. Surfacing conflicts in the UI is part of
  this decision, not a follow-up to it.
- Union-merging child collections means a delete of a child must be a tombstone too, or it
  will be resurrected by any device that still has it.
- Optimistic concurrency requires every mutation to bump `version`. Two mutations in
  `StudioViewModel` currently do not, and any future one that forgets is invisible to
  reconciliation. This needs a test, not vigilance.
- Re-reading rows at drain time means an entity deleted before its outbox entry drains
  uploads as a tombstone rather than as its last live state. That is correct, but it is
  surprising the first time it is debugged.

## Alternatives considered

**Last-write-wins on `updated_at`, silently.** The default, and the reason so many
offline-first applications quietly lose work. Rejected on the clock-skew failure alone,
before the silence is even considered.

**Per-field last-write-wins.** Preserves strictly more work: two devices editing different
fields of one booking both survive. Rejected for now because it needs a timestamp or
version per field, which is a large schema and mapping cost paid on every table to serve a
case — simultaneous edits to different fields of the same row — that a mostly
single-operator studio meets rarely. Decision 5 already covers the sub-case where loss is
actually expensive. Worth revisiting if conflicts prove common in practice, which the
`sync_conflict` table will show rather than leave to guesswork.

**CRDTs.** Correct by construction and no conflict resolution to write. Rejected as
disproportionate: the entities are records with scalar fields, not collaboratively edited
text, and CRDT metadata would outweigh the data on rows this small. It would also make the
shared `core:model` types carry replication concerns, which is exactly the coupling ADR
0007 was trying to avoid.

**A managed sync engine such as PowerSync.** ADR 0007 deferred rather than rejected it.
Still deferred, and this ADR makes the deferral cheaper to reverse: decisions 1 and 2 are
close to what such engines do internally, so adopting one later replaces a mechanism rather
than a model.

**Server rejects conflicting writes and the client retries.** Honest, and wrong for this
application: a drain running after a day offline cannot stop and ask a photographer to
resolve fourteen conflicts, and a rejection the client cannot act on becomes a stuck
outbox.

## Migration signals

Revisit this decision when:

- The `sync_conflict` table shows conflicts are routine rather than rare, at which point
  per-field resolution starts to earn its schema cost.
- More than one person routinely edits the same studio's data at the same time — a
  second shooter with write access changes the contention assumptions in the Context.
- The mapping between the SQLite and Postgres schemas becomes a recurring source of bugs,
  at which point generating one from the other is cheaper than maintaining both.
