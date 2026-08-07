# ADR 0012: Work online, cache for reading, and queue writes only on the shoot day

- Status: Accepted, and carried out
- Date: 2026-08-06
- Supersedes: `ADR 0008`, whose own first migration signal fired.

## Context

ADR 0008 chose offline-first: every device holds the whole studio, every write lands locally
first, and an outbox reconciles them later with `version` negotiation and a visible conflict
inbox. It listed the condition under which it should be revisited:

> The `sync_conflict` table shows conflicts are routine rather than rare.

**A studio with one device now has 154 of them.** The screen tells it "you edited these on two
devices at once", which never happened; most say "the set-aside version could not be read
back"; and they are dismissed one at a time because nothing bulk-resolves them. A conflict
system that manufactures conflicts and cannot show what they were is worse than none, because
it teaches the reader to dismiss without looking — which is precisely when a real one is
missed.

They were manufactured. A pull overwrote a locally-deleted row; the outbox retried at the
version it had just received; the server saw an incoming version equal to its own and recorded
a conflict. Every cycle, for every unsent row. The immediate fault is fixed. **The design that
made it expressible is what this ADR changes.**

Two further facts came out of the same investigation.

**The browser has no local database at all.** The web build runs sql.js in a worker, in
memory, so a reload starts empty and re-pulls everything. Offline-first is not merely unproved
there; it is absent, on the one platform a studio can reach without clicking past a security
warning.

**The offline requirement is real but narrow.** A photographer at a barn wedding or a basement
ceremony has no signal — and what they need there is the shot list, the kit list, the call
sheet, and somewhere to record a release. Quotes, invoices, payments, packages and the pricing
floor are done at a desk. Offline-first was paid for across all twenty-five entities to serve
about four.

## Decision

### 1. The server is the source of truth, and writes go to it

A write is sent to the server and awaited. It succeeds when the server has it.

This is the change everything else follows from. **Conflicts come from blind writes** — from a
device changing something without knowing the current state. A device that must reach the
server to write always holds current state, so there is almost nothing left to conflict.

When there is no connection, the application says so and the write does not happen. That is a
worse moment than a silent local save, and a better day: a studio that is told now can act
now, where one told in five minutes' time has already moved on.

### 2. The local database becomes a read cache, not a ledger

Every device keeps the studio's data locally and reads from it, so screens open instantly and
still show the last-known state with no connection. It is refreshed from the server and may be
rebuilt from it at any time.

The distinction that matters: **nothing is only here.** A cache that is lost costs a refresh.
The current design loses work, which is why the browser's in-memory database is a fault today
and will be merely a cold start afterwards.

### 3. The shoot day may still be written offline

A named, closed set of surfaces queues writes and syncs when signal returns:

- shot list ticks
- kit packed and returned
- talent releases
- media copy verifications

They share the properties that make offline safe: they happen where signal does not exist,
they are toggles and appends rather than edits to a shared figure, and two people ticking the
same shot want the same outcome. **The list is closed on purpose.** Every entity added to it
brings back a share of what this ADR removes, so adding one is a decision to be argued rather
than a default.

Anything not on that list requires a connection.

### 4. The later write wins, silently, and nothing is set aside

For the queued surfaces, the server takes the later arrival. No `version` negotiation, no
`sync_conflict`, no inbox.

ADR 0008 decision 3 kept the loser because discarding work invisibly was the thing to avoid.
That was right for a design where a whole ledger was edited blind. It is wrong for a tick box:
preserving the older value of "packed" is not preserving work, it is generating a row somebody
must dismiss.

`sync_conflict` and its screen go. The 154 existing rows are dropped rather than migrated —
they describe events that did not occur.

### 5. `version` stays as a record, and stops being a negotiation

The column remains, and still increments, because it is useful for ordering and for the audit
trail. Nothing reads it to decide a winner any more.

Keeping the column costs nothing and removes a migration across twenty-five tables on both
sides of the wire. Removing the *behaviour* is the point.

### 6. The outbox survives, smaller

It still exists for the shoot-day surfaces, and keeps the guard added when deletes were coming
back: a refresh must not overwrite a row that is still queued.

For everything else there is no outbox, because there is nothing to hold.

### 7. Getting there without a flag day

The change is made entity by entity, not all at once:

1. Move the ledger — quotes, invoices, payments, expenses, packages, the pricing floor — to
   write through the server. These are the desk-bound ones and the ones generating conflicts.
2. Then clients, contacts, bookings, sessions and enquiries.
3. Leave the four shoot-day surfaces on the outbox.
4. Delete `sync_conflict`, its screen, and the version negotiation once nothing writes through
   the old path.

**All four steps are done.** Step 4 kept one thing it planned to remove: `SyncPushOutcome.Conflicted`
stays on the wire, never sent. A device updated before the server it talks to still has to be able
to read the answer, and an unknown enum value fails the whole response rather than one field.
`version` also stays and still increments, as decision 5 said it would — what went is anything
reading it to pick a winner.

Each step is releasable, and the conflict machinery keeps working for whatever has not moved
yet. **A single cut-over would be the most dangerous change this application has ever had**,
and the one whose failures are least visible.

## Consequences

### Positive

- A studio stops being asked to adjudicate conflicts that did not happen
- The failure mode becomes "you are offline", which is legible, instead of "your delete came
  back", which is not
- The browser's missing database stops being data loss and becomes a cold start
- Roughly: the outbox for twenty-one entities, `sync_conflict`, the conflict screen, the
  version negotiation and a large share of the sync tests all go
- Two schemas still exist, but the second stops needing to be *authoritative* — a divergence
  becomes a cache bug rather than a data-loss bug

### Negative

- **A desk with no connection can read but not write.** That is a real loss for anybody
  working from a train, and it is the price of this decision rather than an oversight
- Every write costs a round trip, so forms feel slower than a local save — mitigated by
  optimistic display, not by pretending the write landed
- The migration touches every repository, and the middle of it is a codebase with two
  write paths
- ADR 0008's work is largely undone. Not wasted — the schema, the entity registry and the
  transport all survive — but the reconciliation half was built and is being removed

### Neutral

- `server_seq` and the cursor stay: refreshing a cache incrementally is the same problem as
  syncing one, and that part of ADR 0008 was never the expensive half

## Alternatives considered

**Keep offline-first and fix the conflicts.** The immediate fault is already fixed, and a
bulk dismiss would clear the 154. But the machinery would still be there, still able to
manufacture rows on the next unforeseen interaction, and still charging twenty-five entities
for four. The cost is structural rather than a bug.

**Go fully online, with no offline at all.** Simplest of all, and it breaks the day this
application exists for: no shot list at the venue, no kit ticks, no release recorded. The
narrow queue in decision 3 is what makes online-first affordable here.

**Keep offline-first but only for the shoot day, with everything else read-only locally.**
This is decision 3 stated the other way round, and is the same thing. Named here because the
difference is emphasis: what changes is that *writing* requires a connection, not that data
stops being cached.

**Per-field conflict resolution**, which ADR 0008 listed as the upgrade path. It would make
conflicts more useful without making them rarer, which is the wrong axis — a studio does not
want better conflict reports, it wants fewer.

## Migration signals

Revisit when:

- Studios report being blocked by "you are offline" often enough to name it — the queue in
  decision 3 is then too narrow, and the argument is about which surface to add
- Two people routinely work one studio at once, which changes the contention assumptions
  the same way ADR 0008 said it would
- The round trip on a write becomes the thing people complain about, at which point
  optimistic local display with server confirmation is the answer rather than local authority
