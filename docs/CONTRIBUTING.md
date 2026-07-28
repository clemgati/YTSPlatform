# Contributing

Yellow Track Platform is in early development. Contributions should preserve a stable, understandable, and reviewable codebase.

## Workflow

1. Create or reference a GitHub issue.
2. Create a focused branch from `main`.
3. Make the smallest complete change that satisfies the issue.
4. Run the relevant builds and tests.
5. Open a pull request with validation notes.
6. Merge only after required checks pass.

## Branch names

Examples:

```text
feature/design-system
feature/session-domain
docs/project-foundation
fix/desktop-launch
chore/gradle-update
```

## Commit messages

Use conventional commit prefixes:

```text
feat:
fix:
docs:
test:
refactor:
chore:
build:
ci:
```

Examples:

```text
docs: establish project vision and roadmap
chore: bootstrap Compose Multiplatform project
feat: add session creation use case
```

## Definition of done

A change is complete when:

- It builds for affected targets.
- Tests pass.
- Formatting and static analysis pass.
- Public behavior is documented.
- Architecture changes include an ADR when appropriate.
- No secrets, client assets, or private business data are committed.

## Pull requests

Pull requests should include:

- Summary
- Motivation
- Technical approach
- Validation performed
- Screenshots for visible UI changes
- Follow-up work, if any

## Sensitive information

Never commit:

- API keys
- OAuth secrets
- Signing credentials
- Client photographs without permission
- Client contact information
- Lightroom catalogs
- Financial records
- Local environment files
