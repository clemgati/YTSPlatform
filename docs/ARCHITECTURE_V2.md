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

        designsystem/
        ui/
        model/
        data/
        database/
        network/
        preferences/
        testing/

    feature/

        dashboard/
        clients/
        sessions/
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

### core:model

Shared domain models.

Examples:

- Client
- Session
- Equipment
- Studio
- Project

Only concepts shared by multiple features belong here.

---

### core:data

Repository contracts.

Contains interfaces only.

Implementations live elsewhere.

---

### core:database

Persistence.

Contains:

- Room
- SQL
- DAOs
- Entity mapping

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

# Features

Each feature owns its business logic.

Features never depend directly on another feature.

Shared concepts belong in Core.

Feature structure:

```
feature/

    dashboard/

        presentation/
        domain/
        data/
```

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
