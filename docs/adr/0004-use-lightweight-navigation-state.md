# ADR 0004: Use lightweight navigation state for the initial shell

- Status: Accepted
- Date: 2026-07-21

## Context

Yellow Track Platform currently has five top-level destinations and no nested
routes, route arguments, deep links, or meaningful back-stack requirements.

Adding a navigation framework now would increase dependencies and architecture
before those capabilities are needed.

## Decision

Use a small shared `AppState` object to manage the selected top-level
destination.

Render a sidebar on expanded layouts and bottom navigation on compact layouts.

## Consequences

### Positive

- No additional navigation dependency
- Easy to understand and test
- Shared consistently across Android, iOS, and desktop
- Supports responsive top-level navigation

### Negative

- Does not provide a back stack
- Does not support route arguments or deep links
- Will require evolution as feature navigation becomes more complex

## Migration signals

Adopt a navigation framework when the platform requires:

- Nested destinations
- Back-stack restoration
- Deep links
- Typed route arguments
- Multiple navigation graphs
