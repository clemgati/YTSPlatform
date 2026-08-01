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
