# Yellow Track Platform

A cross-platform software ecosystem for photographers and videographers, built with Kotlin
Multiplatform and Compose Multiplatform.

> Status: Active development
> Current milestone: `0.3.0 Bedrock`
> Next milestone: `0.4.0 Ledger`

## Vision

Yellow Track Platform exists to help photographers spend less time managing their workflow
and more time creating exceptional images.

The platform will support a family of products built on shared domain logic, data, design,
and integrations:

- **Studio OS** — desktop workflow and studio management
- **Studio Mobile** — preflight, gear, lighting, and session tools
- **Studio Client** — proofing, selections, releases, and delivery
- **Studio Intelligence** — analytics, recommendations, and future AI-assisted tools

Yellow Track Studios runs four business lines at once — weddings and events, commercial
brand content and video, real estate and product, and portraits and personal branding. The
data model is designed so that one schema serves all four: they differ in the values held
by a `ServiceTemplate`, not in code.

## Supported platforms

All four targets build and run from the same shared codebase:

- Android
- iOS
- Desktop (JVM)
- Web (Kotlin/Wasm)

## Technology

- Kotlin Multiplatform, Compose Multiplatform
- SQLDelight for local persistence on all four targets
- Koin for dependency injection
- kotlinx-datetime, kotlinx-serialization, kotlinx-coroutines
- Gradle Kotlin DSL, with convention plugins in `build-logic`

Additional platform technologies are introduced through documented architecture decisions.

## Repository status

The **Bedrock** milestone replaced the sample data the application originally ran on with
a real domain model and a local database.

Working today:

- Clients, projects, and sessions persisted locally and surviving a restart
- Client search across account names, contact names, and companies
- Dashboard and Sessions reading real data
- Service templates seeded per business line

Two modelling decisions shape everything above, and both are deliberate:

- **A client is an account, not a person.** A wedding client is a couple; a commercial
  client is a company whose brief-giver, approver, and payer are three different people.
  People attach to accounts through a role.
- **A project is the booking; a session is a day of shooting.** A wedding is one project
  containing the engagement shoot *and* the wedding day. Without that split, "what did that
  wedding actually earn me?" has no answer.

Not yet built: the money layer (quotes, contracts, invoices, expenses), shoot-day tools,
the post-production pipeline, and the server. See the [Roadmap](docs/ROADMAP.md).

## Architecture

The client is local-first. Core studio workflows must work with no connection, because the
venues where shoot-day tools earn their keep — barns, warehouses, basements — routinely
have no signal.

The planned backend is a Ktor server behind an Apache reverse proxy, over cloud Postgres.
Because the server is Kotlin, it depends on the same `:shared:core:model` module as the
client, so a change to `Session` is a compile error on both sides rather than a production
bug. Media never passes through Postgres or the API; it goes to object storage via
presigned URLs. See [ADR 0007](docs/adr/0007-ktor-server-over-cloud-postgres.md).

Synchronisation is not implemented. The schema is already shaped for it — client-generated
UUID v7 keys, `studio_id` on every table, soft deletes, and an outbox table — so that it
can arrive without a migration. See
[ADR 0006](docs/adr/0006-sync-ready-multi-tenant-schema.md).

## Project structure

```text
androidApp/          desktopApp/          iosApp/          webApp/

build-logic/         convention plugins

shared/
    app/             composition root, shell, routing
    core/
        common/      Money, UUID v7, clock, date formatting, dispatchers
        model/       domain entities — no Compose, no SQL, shared with the server
        data/        repository contracts and SQLDelight implementations
        database/    SQLDelight schema and per-platform drivers
        designsystem/
        ui/
        navigation/
        testing/
    feature/
        dashboard/   clients/   sessions/   studio/   settings/

docs/
```

## Building

```bash
./gradlew build                      # all targets, plus tests and ktlint
./gradlew :shared:core:data:desktopTest   # repository tests against in-memory SQLite
```

Android builds need an SDK location in `local.properties` (`sdk.dir=...`), which is
git-ignored.

`android-minSdk` is 24. SQLite only gained `ON CONFLICT DO UPDATE` in 3.24, which Android
ships from API 30, so upserts are written as `INSERT OR IGNORE` plus `UPDATE` inside a
transaction. Raising minSdk to 30 would let that be simplified.

## Documentation

Yellow Track Platform is guided by a set of living product and engineering documents:

- [Product Vision](docs/PRODUCT_VISION.md)
- [Domain Model](docs/DOMAIN_MODEL.md) — the whole business, mapped
- [Architecture](docs/ARCHITECTURE_V2.md)
- [Engineering Handbook](docs/ENGINEERING.md)
- [UI Principles](docs/UI_PRINCIPLES.md)
- [Roadmap](docs/ROADMAP.md)
- [Architecture Decision Records](docs/adr/)
- [Product Decision Records](docs/decisions/)
- [Changelog](docs/CHANGELOG.md)

## License

Copyright © 2026 Clement Ngati. All rights reserved.

This repository is publicly viewable for evaluation and discussion. No permission is
granted to copy, modify, distribute, sublicense, or commercially use the software without
prior written permission.
