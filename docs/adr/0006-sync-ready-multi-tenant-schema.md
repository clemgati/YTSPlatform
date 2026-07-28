# ADR 0006: Adopt a sync-ready, multi-tenant schema from the first table

- Status: Accepted
- Date: 2026-07-28

## Context

Yellow Track Platform is currently a single-device application with no persistence.
Three future requirements are already committed:

- **Offline-first operation.** `docs/VISION.md` requires that core studio workflows remain
  useful without an internet connection. This is a functional requirement, not an
  aspiration: wedding venues, warehouses, and outdoor locations routinely have no signal,
  and shoot-day tools are needed precisely there.
- **A cloud Postgres database** behind a Ktor API, reached over HTTPS through an Apache
  reverse proxy.
- **Multiple photographers** eventually running their businesses on the platform.

Each of these imposes schema requirements that are cheap to satisfy now and expensive to
retrofit. Adding a tenant column to a populated multi-user database, or converting
sequence-based primary keys to client-generated ones after clients hold local data, are
both migrations with no safe path.

The synchronisation layer itself is deliberately deferred — the domain model is still
moving, and committing to conflict-resolution semantics before the entities have settled
would be premature. Only the schema shape is decided here.

## Decision

Every persisted entity, from the first table onward, carries the same base columns:

| Column | Type | Purpose |
| --- | --- | --- |
| `id` | UUID (v7) | Primary key, generated on the client |
| `studio_id` | UUID | Tenant scope |
| `created_at` | Instant | Audit |
| `updated_at` | Instant | Audit; basis for last-write-wins reconciliation |
| `deleted_at` | Instant, nullable | Soft delete |
| `version` | Int | Optimistic concurrency and conflict detection |

Additionally:

1. **Primary keys are client-generated UUIDs**, never sequences or auto-increment. UUID v7
   is preferred over v4 because it is time-sortable and therefore index-friendly.
2. **Deletes are soft.** Rows are tombstoned via `deleted_at`; queries filter them out.
   A hard delete cannot propagate to a peer that was offline when it happened.
3. **An `outbox` table exists from the first migration**, recording local mutations. It is
   unused until the sync layer lands, but its presence means sync arrives without a schema
   migration.
4. **Monetary values are stored as integer minor units plus an explicit currency code**,
   never as a floating-point number.
5. **Timestamps are stored as instants with an explicit zone** where the zone is
   semantically meaningful (notably `Session` start and end times).
6. `core:model` depends on **neither Compose nor SQLDelight**, so that the future Ktor
   server can depend on the same module and share one definition of every entity.

## Consequences

### Positive

- Offline record creation is possible with no server round trip.
- Postgres Row Level Security becomes a one-line policy per table when multi-tenancy
  arrives, rather than an application-wide audit.
- The synchronisation layer can be added without a schema migration.
- Client and server share a compiler-checked domain model, so contract drift becomes a
  build failure rather than a production bug.
- Monetary arithmetic is exact.

### Negative

- UUID keys are 16 bytes rather than 4 or 8, and are less readable during debugging.
- Every query must filter `deleted_at IS NULL`; forgetting to do so is a new class of bug.
- Soft-deleted rows accumulate and will eventually need a purge policy.
- The `outbox` table and `version` column carry no value until sync ships, so they are
  visible complexity with deferred payoff.
- Writing `studio_id` on every row before multi-tenancy exists is redundant work in the
  single-studio case.

## Migration signals

Revisit these conventions when:

- Soft-deleted row volume measurably affects query performance — introduce a purge policy.
- Last-write-wins proves insufficient and field-level or CRDT-based merge is needed.
- A managed sync engine (for example PowerSync) is adopted, which may impose its own
  conventions on the local schema.
