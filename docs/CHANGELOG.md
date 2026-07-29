# Changelog

All notable changes to Yellow Track Platform will be documented here.

The project follows semantic versioning.

## Unreleased — 0.5.0 Shoot Day

### Added

- **A client can be taken on from the app.** Until now the Client, Project, and Session
  tables could only be written by test code: `saveClient`, `saveProject`, and
  `saveSession` had existed since 0.3.0 with no caller anywhere above the data layer, so a
  real studio opening the app reached a permanently empty database. Every form in the
  money layer attaches to a booking, which meant none of them could be used at all
- The account and its first contact are captured together, because they arrive together —
  an enquiry gives a name and an email in the same breath. A contact is only built when
  something identifies a person: an empty contact would look reachable in every list while
  being nobody
- **A booking can be opened against a client**, which is what makes the whole money layer
  reachable: a quote, an invoice, and a contract all attach to a booking, and until one
  could be created none of those forms could be used at all
- A booking is the job, not the shoot day — a wedding is one booking containing an
  engagement shoot and the wedding day. It carries one contract, one set of invoices, and
  one answer to whether the job made money
- The status defaults to Enquiry rather than Booked, because Booked means something
  specific here: a contract signed and a retainer paid. A studio that starts every job at
  Booked loses the one distinction that says which dates are actually held
- The status stamp is written with the status rather than after it, so a booking recorded
  as Booked can always say when the date was taken. `enquiredAt` is set even for a job
  entered already booked, since the enquiry is what a booking rate is measured against
- Either an account name or a person's name will do. `Client.displayName` already falls
  back from one to the other, and a blank account name is left blank rather than copied
  from the contact — copying it would freeze the name against a later rename

- **A client can be corrected.** The same form serves taking one on and editing it, so a
  field cannot come to mean one thing on the way in and another on the way back. Editing
  carries across everything the form does not show: an account may hold a partner, a
  planner, and an accounts-payable contact, and rebuilding the contact list from the one
  person on screen would silently delete the other three
- Clearing a single email or phone removes that entry rather than the list, so a contact
  with a work number and a personal one does not lose both to an edit of one

### Fixed

- **"Edit Client" was a button that did nothing.** It has sat on the client detail page
  since 0.3.0 wired to an empty lambda in the app shell. It now opens the form. Editing
  lives inside the feature, like every other form here, so the app module no longer needs
  to know what "edit client" means
- **"Archive Client" has been removed rather than left dead.** `Client` has no archived
  state to set, so there was nothing the button could do. A control that silently ignores
  a press is worse than one that is not offered; it can return when there is a model
  behind it
- **The Clients empty state has invited "add your first client" since 0.3.0 with no way to
  accept.** It now carries the button. The first attempt at this did not work and looked
  as though it did: `EmptyContent` fills the height it is given and centres within it, so
  a button placed after it in the parent column was pushed off the bottom of the screen.
  Rendering the page is what caught it. `EmptyContent` now takes an `action` slot, so the
  way out of an empty state sits inside it where it belongs

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
  already used rather than from a counter that could drift away from the documents
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

### Added — contracts

- **Draw up a contract** from the Ledger, with the terms that decide arguments —
  cancellation, rescheduling, and weather — opening prefilled with an ordinary position
  rather than blank. A photographer asked to compose a cancellation clause inside a dialog
  leaves it empty, and the empty clause is the one that loses the argument six months later
- **Usage licensing**, folded away by default and opened deliberately for commercial work.
  A blank duration is a perpetual grant, so the form says plainly what that forecloses, and
  an *unreadable* duration rejects the contract rather than quietly becoming perpetual —
  the one place a typo could give away every future fee from the same work
- **Send** and **record a signature**, the signature carrying who signed and the date they
  signed rather than the date it was typed in, because that is the date that decides
  whether a cancellation falls inside the notice period. Signing twice never moves it
- **A signature alone does not hold a date.** The section is now *Dates not yet held*, and
  a contract stays on it until it is signed *and* its retainer invoice is settled, which is
  what `Contract.isBindingWith` has said since 0.3.0 without anything ever calling it. Each
  row names the step it is stuck on — unsent, unsigned, or waiting on money — and an unsent
  contract sorts first, being the only one nobody but the studio is holding up
- `YTChipField` in the design system, for choosing several values from a fixed list
- 21 tests across drawing up, licensing, sending, signing, and what actually holds a date

### Added — line editing

- **Quotes and invoices carry as many lines as the work has.** Coverage, a second shooter,
  and an album are three figures a client wants to see separately, and collapsing them into
  one total is how a studio loses the argument about what was included
- `LineItemsEditor`, shared by both forms, with a **running total** computed by exactly the
  rule that will store the document — so the figure watched while typing and the figure the
  client is sent cannot diverge. Tax appears as its own line only when there is some
- **Quantity**, which `LineItem` has modelled since 0.3.0 without anything ever setting it.
  Three extra hours at $250 is now three hours at $250, not a $750 line that no longer says
  what it was
- **One bad line rejects the whole document.** Saving with the unreadable line quietly
  dropped would bill a client for less than the studio entered, with nothing on screen
  saying so. The last remaining line cannot be removed either — a document with no lines
  has no figure
- Parsing lives in one place, `NewLineItem.toLineItem`, called by both the form and the
  ViewModel, so there is a single answer to what counts as a valid line

### Added — correcting the books

- **A draft invoice is visible at last.** Accepting a quote raises one, deliberately as a
  draft so an unreviewed figure never lands in money owed — but money owed was the only
  list of invoices on the screen, so the invoice collecting an accepted booking appeared
  nowhere at all and could never be sent. *Raised but not sent* now lists them, oldest
  first, since the one waiting longest is work agreed longest ago and still not billed
- **Send** a draft, stamping the issue date at the moment of sending rather than
  backdating it to when the draft was raised: the clock a client is held to runs from the
  demand they actually received
- **Void** a sent invoice. Voiding rather than deleting is what keeps the numbering
  honest — the row stays, so its number is never handed to a second document, and a client
  holding INV-008 can always be shown what INV-008 was
- **Refuses to void an invoice with money against it.** Cancelling it would take a payment
  the studio actually received out of its books; the remedy for money received in error is
  a refund, recorded. The row does not offer the option rather than failing when pressed
- **Discard** a draft outright, which is safe for exactly the reason voiding is not: it
  has never been sent, nobody holds a copy, and its number may go to the next document.
  Anything that has left the studio is refused

### Changed

- The 0.4.0 note claiming a deleted document never causes a reissue has been corrected. It
  was vacuously true when nothing could be deleted; now that drafts can be, the rule that
  actually holds is the one above — sent documents are voided and keep their numbers, and
  only an unsent number, which no one ever saw, is released
- `YTFormDialog` may now grow to 560dp before scrolling, up from 420dp. Found by looking:
  at 420dp the contract form showed five of its fourteen fields in a window with room for
  far more. The cap was never what protected the buttons — Material's dialog clamps its own
  content, confirmed by rendering into a 280dp scene, shorter than any phone, where both
  buttons stayed put

### Known gaps

- A contract records that it was signed, not the signature itself; there is no document to
  countersign and `documentReference` stays empty until media hosting exists
- Clients, projects, and sessions still cannot be created in the app
- Invoices can be sent, voided, and discarded, but **no record can yet be edited after it
  is saved** — a cost with a typo, or a payment recorded against the wrong invoice, still
  cannot be corrected or removed. Expenses and payments are not listed anywhere on the
  Ledger, only totalled, so there is nothing to act on even once editing exists
- A sent invoice is deliberately not editable: the remedy is to void it and raise another,
  which is now possible. A *draft's* lines, though, could reasonably be edited and cannot
- Dates are typed as `2026-07-28` text rather than picked from a calendar
- `DateFormats` remains English-only
- All six tabs have been seen running on desktop, and the contract, signature, and quote
  dialogs have now been rasterised and looked at, which is what found the form height cap.
  The expense, invoice, and payment dialogs still have not been seen, no screen has been
  seen on Android, iOS, or the web, and nothing has yet been driven by a person rather than
  rendered
- Lines can be added and removed but not reordered, and an existing document's lines still
  cannot be edited after it is saved — that waits on editing existing records
- A fourteen-field contract still belongs on a screen rather than in a dialog on a phone,
  which is the revisit `YTFormDialog` has always said it was waiting for

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
