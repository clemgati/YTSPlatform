# ADR 0002: Start with packages before additional Gradle modules

- Status: Accepted
- Date: 2026-07-20

## Context

The generated project contains Android, desktop, iOS, and shared modules. A large module graph at the beginning would increase Gradle complexity before the platform has enough code to justify those boundaries.

## Decision

Keep the generated module structure during Genesis.

Organize shared code with clear packages first. Extract additional Gradle modules only when there is a demonstrated need for dependency isolation, independent testing, build performance, ownership, or reuse.

## Consequences

### Positive

- Faster onboarding
- Simpler Gradle configuration
- Easier compatibility with official tooling
- Lower early maintenance cost

### Negative

- Package boundaries are not enforced by Gradle
- Some refactoring will be required when modules are later extracted

## Extraction signals

Consider a new Gradle module when:

- A package needs a narrower dependency surface.
- Multiple applications consume the same capability independently.
- Build times benefit from isolation.
- Tests require a clear boundary.
- A feature has stable ownership and APIs.
