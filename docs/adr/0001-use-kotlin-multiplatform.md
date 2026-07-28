# ADR 0001: Use Kotlin Multiplatform

- Status: Accepted
- Date: 2026-07-20

## Context

Yellow Track Platform is intended to support desktop, Android, iPhone, and iPad experiences while sharing business rules, persistence contracts, networking, and selected user interface code.

The primary developer has extensive Kotlin and Android experience. Maintaining completely separate Swift and Kotlin implementations would increase development time and create a risk of inconsistent behavior across products.

## Decision

Use Kotlin Multiplatform as the shared application foundation.

Use Compose Multiplatform for shared user interface code where it provides a high-quality experience. Platform-specific user interfaces and integrations remain acceptable when they provide meaningful product benefits.

## Consequences

### Positive

- Shared Kotlin domain and data code
- Shared coroutines and Flow-based state management
- Shared Compose components across supported targets
- Lower duplication across Android, desktop, and Apple products
- Strong alignment with the primary developer’s experience

### Negative

- Some platform APIs require native adapters
- Apple-specific polish may require Swift or native interop
- Tooling compatibility must be managed carefully
- Cross-platform abstractions can become harmful if used indiscriminately

## Guardrails

- Do not force sharing when native implementation is clearly better.
- Keep domain code independent of Compose and platform APIs.
- Document major cross-platform trade-offs through additional ADRs.
