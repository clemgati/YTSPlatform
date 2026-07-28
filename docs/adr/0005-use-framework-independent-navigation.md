# ADR 0005: Use a framework-independent navigation engine

- Status: Accepted
- Date: 2026-07-22

## Context

Yellow Track Platform needs shared navigation behavior across Android, iOS, and
desktop.

The current requirements are limited to:

- Typed destinations
- A navigation back stack
- Back navigation
- Top-level destination changes
- Shared navigation behavior across platforms

The application does not yet require nested graphs, typed route arguments, deep
links, or persisted navigation state.

## Decision

Implement navigation as a small shared Kotlin state machine.

The navigation engine will:

- Remain independent of Compose and platform APIs
- Maintain an immutable navigation-state snapshot
- Expose focused navigation operations
- Be adapted into observable Compose state by `AppState`

A third-party navigation framework will not be introduced at this stage.

## Consequences

### Positive

- Navigation behavior is easy to test
- Core navigation remains platform independent
- No additional dependency is required
- The implementation is easy to understand
- Future requirements can guide framework selection

### Negative

- Route arguments are not supported
- Deep links are not supported
- Back-stack restoration is not supported
- More complex workflows may require migration later

## Migration signals

Reevaluate the navigation approach when the platform requires:

- Nested navigation graphs
- Typed route parameters
- Deep-link handling
- Saved-state restoration
- Multiple coordinated back stacks
- Platform-specific navigation integration
