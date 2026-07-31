# Yellow Track Platform Architecture

**Version:** 1.0  
**Status:** Active  
**Last Updated:** July 2026

---

# Vision

Yellow Track Platform is a Kotlin Multiplatform application platform designed
to power multiple desktop, mobile, and web experiences from a shared codebase.

The primary goals are:

- Shared business logic
- Native user experience
- Clean modular architecture
- Long-term maintainability
- Feature independence
- Testability
- Platform scalability

The architecture favors simplicity over cleverness and evolution over
premature optimization.

---

# Project Structure

```
yellow-track-platform/

androidApp/
desktopApp/
iosApp/
webApp/

shared/

    app/

    core/

        common/        implemented
        designsystem/  implemented
        ui/            implemented
        model/         implemented
        data/          implemented
        database/      implemented
        navigation/    implemented
        testing/       implemented
        network/       planned — arrives with the Ktor server
        preferences/   planned

    feature/

        dashboard/
        clients/
        sessions/
        ledger/
        studio/
        settings/

docs/
```

---

# Module Responsibilities

## shared:app

Application composition root.

Responsible for:

- App()
- AppShell
- AppState
- AppDestination
- Routing
- Dependency composition

The app module owns application flow.

---

## shared:core

Reusable platform functionality.

Core modules contain no business-specific functionality.

Core modules may be consumed by any feature.

---

### core:designsystem

Reusable UI primitives.

Examples:

- Theme
- Colors
- Typography
- Shapes
- Spacing
- Buttons
- Cards
- Dialogs
- Top App Bar
- Loading Indicator

The design system must never know about business concepts.

---

### core:ui

Reusable application UI patterns.

Examples:

- UiState
- LoadingContent
- EmptyContent
- ErrorContent
- StatefulContent

The UI module builds on the Design System.

---

### core:common

Primitives with no domain meaning.

- `Money` — integer minor units plus an explicit currency, never a floating-point number
- UUID v7 generation, so records can be created offline
- `AppClock`, injected rather than called statically so tests can control time
- `DateFormats`
- Platform IO dispatchers

---

### core:model

Shared domain models. See `docs/DOMAIN_MODEL.md` for the full entity graph.

Implemented: `Contact`, `Client`, `ClientContact`, `Project`, `Session`,
`ServiceTemplate`, `AuditMetadata`, `StudioId`.

**This module must depend on neither Compose nor SQLDelight.** That constraint is what
allows the Ktor server to depend on the same module, so that one definition of every
entity is compiled into both the client and the server, and contract drift becomes a
build failure rather than a production bug.

---

### core:data

Repository contracts **and** their implementations, plus the mapping between database
rows and domain objects.

Repositories live here rather than inside a feature because more than one feature needs
them: Dashboard aggregates clients, projects, and sessions, and features must not depend
on one another.

Reads are exposed as `Flow`, so a change made on one screen appears on every other.

---

### core:database

Persistence.

Contains:

- SQLDelight schema (`.sq`) and generated queries
- Platform driver factories for Android, iOS, desktop, and web
- Versioned schema snapshots under `src/commonMain/sqldelight/databases`

Two consequences worth knowing:

- The generated API is **asynchronous** (`generateAsync`), because the web worker driver
  is. This keeps one API shape across all four targets rather than forking the data layer.
- Upserts are expressed as `INSERT OR IGNORE` followed by `UPDATE`, not
  `ON CONFLICT DO UPDATE`. SQLite gained UPSERT in 3.24, which Android only ships from
  API 30, and this project supports API 24.

---

### core:export

Documents that leave the application.

Contains:

- `Sheet` — a printed document described once, so the same content can be rendered more
  than one way without the versions drifting
- `buildCallSheet` — the shoot day as the people working it receive it
- Renderers to HTML and to plain text
- `DocumentSink` — where a rendered document ends up, implemented per platform in the same
  way as `DatabaseDriverFactory`

Depends on `core:model` and nothing else — no Compose, no SQLDelight — so the same
renderer can run on the Ktor server when documents are mailed rather than saved.

Two decisions worth knowing:

- **HTML, not PDF.** A PDF library would need a per-platform implementation on four
  targets and would decide the format for everything that follows. An HTML page opens on
  any phone and prints to PDF from the browser, which is where a PDF was going to be made
  anyway.
- **The page is self-contained.** Styles are inline and nothing is fetched. It is opened
  at a venue with no signal, on a phone that has only the attachment.

---

### server

The API in front of Postgres, deployed as a JAR behind Apache — see
`docs/adr/0007-ktor-server-over-cloud-postgres.md`.

Plain Kotlin/JVM rather than a Kotlin Multiplatform module: nothing here ships to a phone,
and a single-target module keeps the server off the Apple half of CI entirely.

Depends on `:shared:core:model` and nothing else from the client tree. That dependency is
the whole argument for a Kotlin server over a Node one: one definition of every entity is
compiled into both sides, so adding a field is a compile error rather than a runtime
surprise. `SharedModelContractTest` proves the model actually crosses the wire — inline
value classes, money as minor units, instants, and nulls that mean something.

The JSON configuration is deliberate rather than default, because it decides what happens
when the two ends are briefly *not* the same build: unknown keys are ignored so an older
client survives a rolling deploy, defaults are written out so the reader cannot fill in a
different one, and nulls stay explicit so a tombstone cannot vanish in transit.

#### The schema, twice

The Postgres schema lives in `src/main/resources/db/migration` and is applied by Flyway. It
mirrors the SQLDelight schema the clients carry, which means the same twenty-five tables
are written twice in two dialects.

The compiler keeps `core:model` honest and can do nothing for this, so `SchemaDriftTest`
does it instead: it reads the committed SQLDelight snapshot as an ordinary SQLite file,
applies the real migrations to a real Postgres, and compares them column by column. It
reads the *highest-numbered* snapshot, so a new client migration widens what is compared
automatically and fails until the server catches up.

Three divergences are deliberate, and the test asserts they are the only ones:

- `outbox` is device-only. It is the queue of local mutations awaiting upload; the server
  is what they are uploaded *to*.
- Every synced table carries a `server_seq` no client has — the pull cursor of ADR 0008.
- SQLite `INTEGER` becomes `bigint`, except `is_*` columns, which become `boolean`. SQLite
  has no boolean type, so the prefix is what tells a flag from a count.

`server_seq` is assigned by a trigger on insert **and update**, from one sequence shared by
every table. Sharing it is what lets a client hold a single cursor across entities; firing
on update is what stops an edited row from sitting behind a cursor that has already passed
it, which is the silent loss ADR 0008 exists to prevent.

Running the server tests needs a local Postgres — see `docs/CONTRIBUTING.md`. The drift
test fails rather than skips without one, because a drift check that quietly does not run
still looks green while the two schemas part company.

#### Who a request is, and what it can see

Authentication and the tenant boundary are separate mechanisms, and deliberately so — see
`docs/adr/0009-accounts-authentication-and-tenant-isolation.md`.

A `studio` is the tenant that `studio_id` has always meant. An `account` is a person, joined
to studios through `studio_member`, which carries a role that is `Owner` for everyone until
0.8.0. Passwords are Argon2id in the PHC string format, so the cost parameters travel with
the hash and can be raised later without invalidating anyone's password. A session is an
opaque random token stored only as its SHA-256, revocable because the application is
offline-first and its sessions are therefore long-lived.

The boundary itself is Postgres's, not the application's. Every business table has
`ENABLE` **and** `FORCE ROW LEVEL SECURITY` with a policy comparing `studio_id` against
`current_setting('app.studio_id', true)`. `Database.inStudio` sets it for the length of one
transaction; `Database.unscoped` deliberately does not.

Two properties are worth knowing before changing any of this:

- **It is fail-closed.** The missing-ok `current_setting` yields NULL when unset, and
  `studio_id = NULL` is NULL rather than true. A query that forgets its studio returns
  nothing rather than everything, which turns the expensive mistake into a visible one.
- **The role matters more than it looks.** Superusers and `BYPASSRLS` roles are exempt from
  every policy, and a Homebrew Postgres makes the developer a superuser. So *every*
  transaction issues `SET LOCAL ROLE yellowtrack_app` first, whatever it connected as.
  Without that, all of this is inert on precisely the machines it is written on.

`RowLevelSecurityTest` holds both, and was checked by breaking them.

The authentication tables sit outside the mechanism, because a policy keyed on
`app.studio_id` cannot guard the lookup that decides what `app.studio_id` should be. That
hole is argued for in ADR 0009 decision 7; `Accounts.kt` is the only code inside it.

---

### core:network

Networking.

Contains:

- Ktor
- DTOs
- Serialization
- API clients

---

### core:preferences

User preferences.

Examples:

- Theme
- Units
- Locale
- User settings

---

### core:navigation

Navigation state, as plain Kotlin.

Contains:

- `BackStack` — an immutable, never-empty navigation stack

Independent of Compose and of any platform navigation framework, per ADR 0005, so that
navigation behaviour is testable without a UI. `AppState` adapts it into observable
Compose state.

---

### core:testing

Test support shared by every module.

Contains:

- Fakes for the repository contracts in `core:data`
- `TestAppClock` — a clock tests can move
- `TestData` — domain builders whose every parameter has a default

Consumed only by test source sets. Note that `core:testing` depends on `core:data`, so
`core:data`'s own tests deliberately build their own fixtures to avoid a dependency cycle.

---

# Features

Each feature owns its business logic.

Features never depend directly on another feature.

Shared concepts belong in Core.

Feature structure:

```
feature/

    clients/

        ClientsRoute.kt          public entry point
        ClientDetailsRoute.kt

        presentation/

            list/
                ClientsScreen.kt
                ClientsViewModel.kt
                ClientsUiState.kt
                mapper/          domain → presentation model
                model/           presentation-only types
                component/       screen-specific composables
                preview/
            details/
```

Features no longer carry their own `data/` or `domain/` packages. Persistence and
repository contracts live in `core:data`, because more than one feature needs them —
Dashboard aggregates clients *and* sessions — and features must not depend on each other.

A feature keeps its own presentation models and mappers. `ClientSummary` is shaped for a
list row and is not a domain type.

---

# Presentation Layer

Every feature follows the same presentation architecture.

```
Route

↓

ViewModel

↓

UiState

↓

Screen
```

---

## Route

Responsibilities:

- Obtain ViewModel
- Collect state
- Pass actions
- Compose Screen

Routes are the public entry point of a feature.

---

## ViewModel

Responsibilities:

- Presentation logic
- State transformation
- User actions
- Repository interaction

ViewModels never render UI.

---

## UiState

Immutable representation of UI.

Every screen receives one state object.

Example:

```kotlin
DashboardUiState(...)
```

---

## Screen

Pure rendering.

Screens:

- receive state
- render UI
- emit actions

Screens never:

- access repositories
- create ViewModels
- perform dependency injection

---

# Design Principles

## Feature First

Business logic belongs inside features.

Only shared concepts belong in Core.

---

## Composition Root

Only the App module composes features.

---

## Stateless UI

Composable screens should remain stateless.

---

## Immutable State

UI state is immutable.

State changes produce new state.

---

## Public API

Every feature exposes only its Route.

Internal implementation details remain internal.

---

# Dependency Rules

Allowed:

```
App

↓

Features

↓

Core
```

Forbidden:

```
Feature

↓

Feature
```

Features communicate through Core.

---

# Long-Term Goals

The architecture should support:

- Android
- iOS
- Desktop
- Web

Future goals include:

- Offline-first support
- Synchronization
- Cloud integration
- Plugin architecture
- AI-assisted workflows

without requiring major architectural changes.

---

# Philosophy

Yellow Track Platform values:

- Simplicity
- Readability
- Consistency
- Maintainability
- Explicit architecture

Every new module should have one clear responsibility.

Architecture should evolve intentionally through documented decisions rather than accidental growth.

## Non-Goals (Architecture v1)

The following are intentionally out of scope for Architecture v1:

- A custom navigation framework
- A plugin system
- Dynamic feature loading
- Event bus/message bus
- CQRS/Event Sourcing
- Microservices-inspired layering
- Premature abstraction of repositories or ViewModels

These ideas may be revisited in future ADRs if real requirements emerge.
