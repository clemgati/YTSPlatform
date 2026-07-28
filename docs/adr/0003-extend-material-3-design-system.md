# ADR 0003: Extend Material 3 for the Yellow Track Design System

- Status: Accepted
- Date: 2026-07-21

## Context

Yellow Track Platform requires a consistent cross-platform visual language.
Building an entirely custom component system immediately would require
reimplementing accessibility, interaction states, focus behavior, and platform
adaptation.

## Decision

Use Material 3 as the underlying component and accessibility foundation.

Create the Yellow Track Design System by defining branded color, typography,
shape, and spacing tokens and by exposing Yellow Track component wrappers.

## Consequences

### Positive

- Retains established interaction and accessibility behavior
- Supports Android, desktop, iOS, and future Compose targets
- Provides a consistent branded API
- Allows progressive customization

### Negative

- Some Material visual behavior remains underneath Yellow Track components
- Platform-specific refinement may still be required
- Wrapper components must avoid hiding useful Material capabilities

## Guardrails

- Application features should prefer Yellow Track components.
- Raw Material components may be used inside the design-system package.
- Do not wrap components unless the wrapper adds a meaningful standard.
- Preserve escape hatches through modifiers and focused parameters.
