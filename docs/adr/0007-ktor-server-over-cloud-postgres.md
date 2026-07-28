# ADR 0007: Serve cloud Postgres through a Ktor server that shares the domain model

- Status: Accepted
- Date: 2026-07-28

## Context

The platform will eventually keep its data in a cloud-hosted Postgres database. Clients
cannot reach Postgres directly: there is no Postgres driver for iOS or wasm, and exposing
a database to client applications would be wrong regardless of whether a driver existed.
Something has to sit in front of it.

The initial intention was a Node.js API. Two properties of this project argue against that
default:

- The client is Kotlin Multiplatform, and its domain entities already live in a module
  (`:shared:core:model`) that deliberately depends on neither Compose nor SQLDelight. A
  Kotlin server can depend on that same module.
- Contract drift between client and server is the most common source of integration bugs
  in this shape of system, and it is the one thing a shared, compiled domain model
  eliminates outright.

Separately, this is a photography and videography platform. Media files are large — RAW
frames run 25–80 MB, and a single delivered wedding gallery can reach tens of gigabytes.
How media is stored and served is an architectural decision, not an implementation detail.

Offline-first operation is a standing requirement (`docs/VISION.md`), so the client keeps a
local SQLite database and reconciles with the server. The synchronisation mechanism itself
is deferred; ADR 0006 covers the schema that makes it possible.

## Decision

**The API is Ktor, written in Kotlin, depending on `:shared:core:model`.**

One definition of `Client`, `Project`, `Session`, and every entity that follows is compiled
into both the client and the server. Adding a field is a compile error on both sides rather
than a runtime surprise.

**Deployment is a JAR behind an Apache reverse proxy.**

```
Android / iOS / Desktop / Web
            │  HTTPS (JSON, JWT auth)
            ▼
   Apache  (TLS termination, mod_proxy → localhost)
            │
            ▼
   Ktor JAR  (systemd or Docker)      ← database credentials live only here
            │  JDBC over TLS
            ▼
   Cloud Postgres
```

Apache does not execute the JAR; it proxies to it, exactly as it would have proxied to a
Node process. The server stack is Ktor, the Postgres JDBC driver, HikariCP for pooling,
Exposed or jOOQ for queries, and Flyway for versioned migrations.

**Media never enters Postgres and never streams through the API.**

The server stores metadata rows and issues presigned upload and download URLs; the client
transfers bytes directly to and from object storage. Cloudflare R2 is preferred over S3
because client gallery downloads are egress-heavy and R2 charges no egress fees — a
recurring cost difference for a business whose product is delivered as large downloads.

**Multi-tenancy is enforced in Postgres via Row Level Security on `studio_id`**, which
every table already carries (ADR 0006).

## Consequences

### Positive

- Client and server share one compiler-checked domain model.
- No second language, build system, or dependency ecosystem to maintain.
- The team's existing Kotlin expertise transfers directly to the server.
- Serialisation is already configured — the domain entities are `@Serializable` today.
- Database credentials never leave the server.
- Row Level Security makes tenant isolation a per-table policy rather than an
  application-wide audit that must be re-verified on every new query.
- Media costs and media transfer stay off the API's critical path.

### Negative

- A JVM server has a higher memory floor and slower cold start than a Node process, which
  matters if deployment ever moves to scale-to-zero serverless hosting.
- Node's package ecosystem is larger; some third-party integrations (payment providers,
  transactional email) have better-maintained JavaScript SDKs than Kotlin ones, and may
  need to be driven through their REST APIs directly.
- Sharing a module between client and server couples their release cycles more tightly
  than a versioned wire contract would. A breaking domain change requires deploying both.
- Presigned-URL flows are more moving parts than proxying uploads through the API, and
  require correct expiry and scope handling to avoid leaking access.
- Apache is a less common reverse proxy for this role than Nginx, so more community
  examples will need translating.

## Alternatives considered

**Node.js and TypeScript.** The original plan. Rejected primarily because it forfeits the
shared domain model, which is the single largest benefit available from having chosen
Kotlin Multiplatform. Types would have to be kept in sync by hand or generated from an
OpenAPI document, and drift would surface at runtime.

**A backend-as-a-service such as Supabase.** Genuinely attractive: it is real Postgres, and
it supplies authentication, storage, and Row Level Security without building them. Rejected
for now because it does not share the domain model either, and because the long-term
ambition of supporting other photographers argues for keeping the server owned rather than
rented. Worth revisiting if server operations become a drag on feature work.

**A managed synchronisation engine such as PowerSync.** Compatible with this decision
rather than opposed to it — it reads Postgres by logical replication and routes writes
through the application's own API. Deferred rather than rejected: the domain is still
moving, and committing to a vendor's conflict semantics before entities have settled would
be premature. Its Kotlin Multiplatform SDK's wasm support would need verifying first.

## Migration signals

Revisit this decision when:

- Cold-start latency or hosting cost makes a JVM server the wrong shape for the deployment.
- A required third-party integration exists only as a JavaScript SDK and proves impractical
  to drive over REST.
- Client and server release cycles need to diverge, at which point the shared module should
  be replaced by a versioned wire contract.
- Operating authentication, storage, and backups outweighs the benefit of owning them.
