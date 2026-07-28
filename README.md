# Yellow Track Platform

A cross-platform software ecosystem for photographers, built with Kotlin Multiplatform and Compose Multiplatform.

> Status: Active development  
> Current release: `0.1.0 Genesis`  
> Current milestone: `0.2.0 Blueprint`

## Vision

Yellow Track Platform exists to help photographers spend less time managing their workflow and more time creating exceptional images.

The platform will support a family of products built on shared domain logic, data, design, and integrations:

- **Studio OS** — desktop workflow and studio management
- **Studio Mobile** — preflight, gear, lighting, and session tools
- **Studio Client** — proofing, selections, releases, and delivery
- **Studio Intelligence** — analytics, recommendations, and future AI-assisted tools

## Supported platforms

The initial foundation targets:

- Android
- iOS
- macOS/Desktop

## Technology

- Kotlin Multiplatform
- Compose Multiplatform
- Gradle Kotlin DSL

Additional platform technologies will be introduced through documented architecture decisions.

## Repository status

The project is currently in the **Genesis** milestone. The focus is project identity, architecture, engineering standards, and a stable cross-platform foundation.

Business features have not yet been implemented.

## Project structure

```text
androidApp/
desktopApp/
iosApp/
shared/
docs/
.github/
```

The project will remain intentionally simple while the architecture develops. New Gradle modules will be introduced only when they provide a clear ownership or dependency benefit.

## Documentation

Yellow Track Platform is guided by a set of living product and engineering
documents:

- [Product Vision](docs/PRODUCT_VISION.md)
- [Engineering Handbook](docs/ENGINEERING.md)
- [Architecture](docs/ARCHITECTURE.md)
- [UI Principles](docs/UI_PRINCIPLES.md)
- [Roadmap](docs/ROADMAP.md)
- [Architecture Decision Records](docs/adr/)
- [Product Decision Records](docs/decisions/)
- [Changelog](docs/CHANGELOG.md)

## License

Copyright © 2026 Clement Ngati. All rights reserved.

This repository is publicly viewable for evaluation and discussion. No permission is granted to copy, modify, distribute, sublicense, or commercially use the software without prior written permission.
# YTSPlatform
