# Roadmap

The roadmap uses semantic versioning and milestone codenames.

## 0.1.0 — Genesis

- Compose Multiplatform bootstrap
- Android, iOS, and desktop targets
- Project identity and documentation
- Architecture decision records
- Engineering standards
- Initial foundation package
- CI, formatting, and static analysis
- Application shell

## 0.2.0 — Blueprint

- Design system
- Navigation architecture
- Dependency injection
- Logging abstraction
- Persistence architecture
- Database schema versioning
- Repository contracts

## 0.3.0 — Workflow

- Client profiles
- Session creation
- Folder generation
- Session lifecycle
- Preflight and shot lists

## 0.4.0 — Studio

- Gear inventory
- Packing lists
- Lighting recipes
- Backdrops and modifiers
- Maintenance tracking

## 0.5.0 — Intelligence

- Workflow analytics
- Smart defaults
- Gear and lighting recommendations
- Portfolio gap tracking

## 0.6.0 — Automation

- Desktop workflow launcher
- Export jobs
- Delivery package creation
- Supported application integrations

## 0.7.0 — Collaboration

- Client proofing
- Favorites and selections
- Releases and approvals
- Shared session state

## 0.8.0 — Cloud

- Account model
- Secure synchronization
- Device coordination
- Backup and recovery

## 0.9.0 — Release Candidate

- Accessibility review
- Performance review
- Data migration validation
- Product polish
- Public testing

## 1.0.0 — Launch

- Production-ready Studio OS
- Stable data model
- Documented upgrade path
- Release and support process


---

# Current plan

The sequence below supersedes the version list above, which was written before the domain
existed. The largest change is that **money moved forward**: the original roadmap reached
1.0 without quotes, contracts, invoices, or expenses, which are what decide whether a
photography business survives. See `docs/DOMAIN_MODEL.md`.

## 0.3.0 — Bedrock ✓

- ✓ Domain model mapped across the whole business
- ✓ `Client` as an account; `Project` as the booking, `Session` as a day inside it
- ✓ Local SQLite persistence on all four targets, with a sync-ready schema
- ✓ Repositories in `core:data`, exposed as `Flow`
- ✓ Clients list, detail, and search on real data
- ✓ Sessions list on real data
- ✓ Service templates per business line
- ✓ Test infrastructure: repository, ViewModel, and navigation tests

## 0.4.0 — Ledger ✓

The biggest gap in the original roadmap.

- ✓ Lead capture with source attribution and **first-response time**, surfaced on the
  Dashboard oldest-first
- ✓ Invoice, retainer, and payment, with payment state derived rather than stored
- ✓ Expenses and mileage, linked to a project for job costing or left as overhead
- ✓ **Cost of doing business** → minimum viable session price, with each package measured
  against the floor
- ✓ Quote and contract domain models, tables, and usage licensing
- ✓ First schema migration, with data-survival tests against the committed v1 artefact
- ✓ Quote and contract repositories, with quotes surfaced on the Ledger and accepting one
  raising the invoice that collects it
- ✓ Create forms for leads, quotes, invoices, expenses, and payments
- ✓ Contract creation and signing from a screen, with the licence priced deliberately and
  the date held only once the retainer is paid
- ✓ Line editing, so quotes and invoices carry as many lines as the work has, with
  quantities and per-line tax
- ◐ Editing and deleting existing records — invoices can be sent, voided, and discarded,
  with a sent document never deleted so its number stays used. Editing a saved record, and
  correcting a cost or a misrecorded payment, is still to come

## 0.5.0 — Shoot Day

- ✓ Add and edit client, project, and session — all three can be created and corrected,
  with bookings listed on the client's own page
- ✓ Shot lists with grouping, for family formals
- ✓ Locations with computed golden hour and sun position — calculable offline from
  latitude, longitude, and date, stored against the session and shown on it
- ✓ Call sheets, crew, and talent releases — crew with per-person call times and talent
  releases, on a session page that reads as a call sheet, which can now be sent to the
  people working the day

## 0.6.0 — Pipeline

- ✓ Ingest and 3-2-1 backup tracking across storage volumes — the rule is checked per
  shoot and says what is missing, the studio's drives are a register rather than free text,
  so marking one failed immediately drops the copy count on every shoot it held, and a copy
  with a path is **read** rather than taken on trust: the application opens the drive,
  counts the files, and refuses to record a verification when it finds nothing. Copying
  files in — as opposed to checking what is already there — remains a card-reader job and
  is not planned
- ✓ Post-production tasks with **estimated versus actual hours**, entered on a booking's
  own page and feeding the pricing floor, which now measures rather than assumes
- ✓ Deliverables with revision rounds and turnaround SLA, both checked against what the
  contract actually promised
- ✓ Gear inventory with serials and purchase prices, packing lists, maintenance — the
  Studio tab totals what the studio paid for what it still owns and names the gear that
  would lose a claim; the kit list lives on the shoot day it belongs to, ticked packed in
  the morning and back at midnight
- ✓ Lighting recipes — set-ups written down in the terms they are dialled in, so the
  three-light headshot is a starting point rather than a rebuild from memory
- ◐ Documents out of the app. The shoot day leaves as a self-contained web page or as
  text pasted into a message; invoices and quotes leave as pages a client can read and
  print, carrying the studio's details, its tax number and how to pay it. Android and iOS
  hand the file to the system share sheet, saving it first so a sheet that fails to appear
  costs nothing — though **neither has been run**, only compiled, so treat the sheet itself
  as unproven until someone opens the app on a phone. Emailing a document without leaving
  the application is still to come and needs a mail transport, which belongs with the
  server in 0.7.0
- ✓ Studio details, which every document carries — the Settings screen has claimed since
  0.1.0 that there was nothing to configure, and an invoice with no name on it is not an
  invoice

## 0.7.0 — Cloud

Scoped as a **vertical slice**: the whole path working end to end for `Client`, `Project`
and `Session` only. Sync is the one feature here whose bugs are invisible — they discard
work on a device nobody is looking at — so it is proved against real conflicts on three
entities before the remaining eighteen follow mechanically. Infrastructure is provisioned
by nobody yet, so development runs against Postgres in Docker and deployment waits until
there is something worth deploying.

- Sync semantics decided before any of it is built — see
  `docs/adr/0008-synchronisation-semantics.md`
- Ktor server sharing `core:model`, behind Apache, over cloud Postgres
- Postgres schema through Flyway, with a test that it has not drifted from SQLDelight's
- Accounts, authentication, and Row Level Security on `studio_id`
- Synchronisation for `Client`, `Project` and `Session`, landing on a schema that has been
  ready for it since 0.3.0
- Object storage for media, via presigned URLs

## 0.8.0 — Collaboration

- Client proofing, selections, and approvals
- Second shooters and editors, with roles

## 0.9.0 — Release Candidate

- Accessibility, performance, and migration validation
- Localisation — `DateFormats` is English-only today

## 1.0.0 — Launch

- Production-ready Studio OS
- Stable data model and documented upgrade path
