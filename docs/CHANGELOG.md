# Changelog

All notable changes to Yellow Track Platform will be documented here.

The project follows semantic versioning.

## Unreleased — 0.4.0 Ledger

The money layer. The largest gap in the original roadmap, which reached 1.0 without
quotes, contracts, invoices, or any notion of what a job costs to deliver.

### Added

- **Cost of doing business** — annual overhead plus a take-home target, grossed up for
  tax and divided by realistically sellable days, giving the least a job may be sold for.
  Includes a pricing screen that measures each service template against that floor and
  names any priced below it
- `Lead` — enquiries with source attribution and a first-class `firstResponseAt`, plus an
  "awaiting your reply" section on the Dashboard, ordered oldest first
- `Invoice`, `Payment`, and shared `LineItem` with per-line tax; retainer, balance, full,
  and additional invoice kinds
- `Expense` and `Mileage`, where a null project link means overhead and a set one means a
  cost of that job — one table answering both *what does a year cost to run* and *did this
  booking make money*
- `Quote`, `Contract`, and `UsageLicense` with media, territory, duration, exclusivity,
  and a computed renewal date
- `ProjectMargin` for job-level profitability
- Ledger destination, showing money owed, the pricing floor, and costs
- Exact money parsing from typed input, never routed through a floating-point value
- **Schema migration 1 → 2**, purely additive, with the `2.db` snapshot committed
- Migration tests that load the committed `1.db` artefact, insert real rows, upgrade, and
  assert the rows survive — `verifyMigrations` only checks schema shape, not data

### Changed

- Dashboard now surfaces unanswered enquiries above the day's schedule, because an
  unanswered message is the only thing on that screen that gets worse purely by being
  left alone
- `Money` gained exact division and fractional multiplication for rates and distances

### Added — writable ledger

- `YTFormDialog`, `YTTextField`, and `YTDropdownField` in the design system, so a feature
  can present a create form without the app module learning about "add expense"
- **Log an enquiry** and **mark it replied** from the Dashboard. The reply stamp is
  written once and never overwritten: the figure that predicts bookings is time to
  *first* response, not to the most recent one
- **Record a cost** from the Ledger, where the project field decides overhead versus job
  cost and the category suggests which
- **Record a payment** against an outstanding invoice, prefilled with the balance but
  editable, since a retainer now and a balance later is how bookings are actually paid
- Write-path tests proving that recording overhead raises the pricing floor, that a cost
  charged to a job does not, and that a part payment leaves the remainder overdue

### Added — proposals

- `QuoteRepository` and `ContractRepository`, completing the money layer's data access.
  Contracts carry their usage licence as JSON and it survives a round trip with its
  renewal date intact
- **Out with clients** on the Ledger: quotes awaiting a decision, expired ones first, and
  contracts awaiting signature. An unanswered quote is an unpaid invoice one step earlier,
  so it sits directly beneath money owed
- **Send a quote** and **raise an invoice**, both continuing the studio's own numbering
  rather than restarting at one — the suggestion is derived from the highest number
  already used, so a deleted document never causes a reissue
- **Accepting a quote raises the invoice that collects it**, carrying the agreed lines
  across untouched. Re-entering them by hand is where the figure a client agreed to and
  the figure they are billed diverge
- An invoice raised on acceptance is a *draft*: it owes nothing and cannot go overdue, so
  accepting never puts an unreviewed figure into money owed
- `Quote.accepted`, `declined`, and `sent`, which stamp status and date together — a quote
  marked Accepted with no acceptance date cannot say when the price was agreed
- Expired is derived from the validity date and never written back, so extending a lapsed
  quote's date revives it rather than freezing it as expired
- Ledger fakes in `core:testing` for invoices, quotes, contracts, expenses, cost of doing
  business, and service templates
- 38 tests across the proposal repositories, the quote-to-invoice conversion, the ledger
  write paths, and document numbering
- **Off-screen rendering**, so a screen can finally be looked at. `LedgerScreenRenderTest`
  rasterises the Ledger to `build/render/ledger.png` through `ImageComposeScene`, without
  opening a window or needing screen recording. The `kmp.compose` convention now puts
  Skia's native binary on every UI module's desktop test classpath, so any feature can do
  the same. It found the first thing anyone has seen: a screen renders its own text but
  no background, so it must be composed inside the shell's surface to be legible

### Fixed

- The project dropdown in the cost form rendered `${project.name} — ${it.displayName}`
  literally instead of the booking and client names
- **Settings showed the wrong screen entirely**: the heading read "Clients", the badge
  read "Genesis" — a milestone codename retired at 0.1.0 — and the body was the scaffold
  string "Manage your Settings!!!!". It now names itself and says plainly what is not
  built yet, pointing at the pricing basis on the Ledger, which is the one setting that
  does exist. Its source also sat at `kotlin/presentation/` rather than under its package
  directory, which is why it escaped the move that relocated every other feature
- **Sessions lost its header when empty.** The title, badge, and description rendered only
  on the populated branch, so a studio with nothing booked saw an unlabelled block of
  centred text and could identify the screen only by the sidebar highlight. Clients
  already kept its header when empty; Sessions now matches. This also makes the
  `0 -> "No sessions"` badge case reachable, which until now was dead code

### Known gaps

- Quotes and invoices are created with a single line; multi-line documents need line
  editing, which is its own piece of work
- Contracts can be stored and read but not yet created or signed from a screen
- Clients, projects, and sessions still cannot be created in the app
- Editing and deleting existing records is not yet possible from any screen
- Dates are typed as `2026-07-28` text rather than picked from a calendar
- `DateFormats` remains English-only
- All six tabs have now been seen running on desktop, but no screen has been seen on
  Android, iOS, or the web, and no dialog has been opened by a person. The three form
  dialogs added this release have been reasoned about and tested, not looked at

## Unreleased — 0.3.0 Bedrock

### Added

- Domain model covering the whole business, in `docs/DOMAIN_MODEL.md`
- Architecture decision record for the sync-ready, multi-tenant schema (ADR 0006)
- Architecture decision record for the Ktor server over cloud Postgres, sharing
  `core:model` with the client (ADR 0007)
- `core:common` — `Money` as integer minor units with an explicit currency, UUID v7
  generation, an injectable clock, shared date formatting, platform IO dispatchers
- `core:model` — `Contact`, `Client`, `ClientContact`, `Project`, `Session`,
  `ServiceTemplate`, and shared audit metadata, free of Compose and SQL so the future
  Ktor server can depend on the same module
- `core:database` — SQLDelight schema and drivers for Android, iOS, desktop, and web,
  with audit and tenant columns on every table and an outbox table ready for sync
- `core:data` — repository contracts and SQLDelight implementations exposing `Flow`
- `core:testing` — fakes, a controllable clock, and domain builders
- `core:navigation` — an immutable, framework-independent back stack, with tests
- Local persistence: clients, projects, and sessions survive a restart
- Client search across account names, contact names, and companies
- Sessions screen backed by real data, grouped into upcoming and past
- Service templates seeded per business line — wedding, brand video, real estate, headshots
- `YTSearchField` design-system component
- Convention plugins in `build-logic`, replacing the four-target block duplicated across
  ten modules
- 89 tests, including repository tests against a real in-memory SQLite database

### Changed

- `Client` is now an account rather than a person, with people attached through
  `ClientContact` and a role. A wedding client is a couple; a commercial client is a
  company whose brief-giver, approver, and payer are three different people
- `Project` introduced as the booking, with `Session` as a scheduled block inside it. A
  wedding is one project containing the engagement shoot and the wedding day
- ViewModels now extend `androidx.lifecycle.ViewModel` and observe repositories reactively
- Repositories moved from inside the clients feature into `core:data`, so that Dashboard
  and Sessions can use them without depending on another feature
- `AppState` replaced its single `selectedClientId` field with a typed back stack
- Dashboard reads real data instead of a hand-written sample object

### Fixed

- ViewModels created a `CoroutineScope` that was never cancelled, leaking a coroutine on
  every disposal
- `ClientsRoute` and `ClientDetailsRoute` constructed their own repository inside a
  composable while the Koin module sat empty
- Sessions and Studio screens both rendered the heading "Clients"
- The dashboard feature module declared the namespace `feature.clientdetails`
- `AppShell` silently rendered nothing for the Sessions, Studio, and Settings destinations
- The Kotlin/Native linker ran out of heap when linking the iOS release framework

### Removed

- `DashboardSampleData` and the hardcoded per-client sample metadata in the client mappers
- `InMemoryClientRepository` and the feature-local `ClientRepository` contract

### Corrected

Earlier entries in this changelog described work that had not been implemented. The
following claims were removed because no such code existed:

- "Shared framework-independent navigation engine" — `core:navigation` contained only a
  build file until this release
- "Immutable navigation back-stack state"
- "Navigation behavior and application-state tests"

`ARCHITECTURE_V2.md` also listed `core:data`, `core:database`, `core:network`,
`core:preferences`, and `core:testing` as though they existed. Three of them now do;
`core:network` and `core:preferences` remain planned.

## 0.1.0 — Genesis

Planned foundation release.
