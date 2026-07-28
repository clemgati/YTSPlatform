# Yellow Track Platform Architecture

> **Superseded.** The active architecture document is
> [ARCHITECTURE_V2.md](ARCHITECTURE_V2.md). This file is kept for the reasoning it
> records; where the two disagree, V2 is correct.

> **Build with intention. Create without friction.**

**Status:** Superseded by ARCHITECTURE_V2.md
**Version:** 0.1
**Last Updated:** 2026-07-22

---

# Purpose

This document describes the architectural principles and organization of
Yellow Track Platform.

It explains how the platform is structured today and the direction it is
expected to evolve over time.

Architecture exists to make change easier.

---

# Guiding Principles

The architecture should:

- Separate responsibilities clearly.
- Keep business logic platform independent.
- Minimize coupling.
- Maximize maintainability.
- Support incremental evolution.

---

# High-Level Architecture

```
                Applications
        ┌─────────┼─────────┐
        │         │         │
    Android      iOS     Desktop
        │         │         │
        └─────────┼─────────┘
                  │
              Shared Module
                  │
    ┌─────────────┼─────────────┐
    │             │             │
  Core         Domain       Features
```

Applications compose the experience.

Shared contains the product.

---

# Dependency Direction

Dependencies always point inward.

```
Applications
      │
      ▼
Features
      │
      ▼
Domain
      │
      ▼
Core
```

Core never depends on Domain.

Domain never depends on Features.

Platform applications should avoid containing business logic.

---

# Responsibilities

## Core

Provides reusable platform capabilities.

Examples:

- Design System
- Navigation
- Preferences
- Resources
- Utilities
- Logging

Core should remain stable.

---

## Domain

Represents the business concepts of the platform.

Examples:

- Client
- Session
- Studio
- Gear
- Lighting Recipe

Domain contains business rules.

Domain does not know how data is stored.

---

## Data

Responsible for persistence and external systems.

Examples:

- Database
- Preferences
- File Storage
- Network APIs

Data implements contracts defined by the Domain.

---

## Features

Features provide complete user workflows.

Examples:

- Dashboard
- Clients
- Sessions
- Studio
- Settings

Features compose Domain and Core.

---

# Platform Applications

Android

Provides Android-specific integration.

iOS

Provides iOS-specific integration.

Desktop

Provides desktop-specific integration.

Platform applications should remain intentionally thin.

---

# Design System

Visual consistency is provided through a shared design system.

It should include:

- Colors
- Typography
- Icons
- Spacing
- Components
- Themes

The design system should evolve independently from features.

---

# Navigation

Navigation should be shared whenever practical.

Business logic should never depend on navigation frameworks.

---

# Evolution Strategy

The architecture is expected to evolve.

Refactoring is encouraged when it improves:

- clarity
- maintainability
- testability

Avoid introducing abstractions before they are justified.

---

# Architectural Principles

- Prefer composition over inheritance.
- Favor immutable state.
- Keep APIs small.
- Make illegal states difficult to represent.
- Optimize for readability.
- Document important decisions.

---

# Summary

Architecture exists to support the product—not the other way around.

Every architectural decision should reduce future complexity while enabling
future growth.
