# Contributing

Yellow Track Platform is in early development. Contributions should preserve a stable, understandable, and reviewable codebase.

## Local setup

The client targets need only a JDK and the Android SDK. The `:server` module additionally
needs a Postgres to test against, because `SchemaDriftTest` compares the server schema
against the clients' and cannot do that without a database:

```sh
brew install postgresql@18
brew services start postgresql@18
createdb yellowtrack_dev
createdb yellowtrack_test
```

That test fails rather than skips when there is no database. This is deliberate: a drift
check that quietly does not run still reports green while the two schemas part company.

The defaults assume Postgres on the loopback address owned by the account running the
build, which is what the Homebrew formula produces. Override with `YELLOWTRACK_TEST_DB_URL`,
`YELLOWTRACK_TEST_DB_USER` and `YELLOWTRACK_TEST_DB_PASSWORD`; the server itself reads
`DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`.

Note that the Homebrew role is a **superuser**, and superusers are exempt from every row
level security policy in the schema. Nothing needs doing about that locally — every
transaction drops to `yellowtrack_app` before it touches business data — but it is why that
role exists, and why connecting the server as a superuser in production would silently
disable the tenant boundary. The migration creates the role without `LOGIN`; deployment
grants it separately, so no credential is implied by anything in this repository:

```sql
ALTER ROLE yellowtrack_app LOGIN PASSWORD '...';
```

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
