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
  the application is still to come; the mail transport it was waiting for now exists in the
  server (ADR 0010), so this is wiring rather than a missing piece
- ✓ Studio details, which every document carries — the Settings screen has claimed since
  0.1.0 that there was nothing to configure, and an invoice with no name on it is not an
  invoice

## 0.7.0 — Cloud

Scoped as a **vertical slice**: the whole path working end to end for `Client`, `Project`
and `Session` only. Sync is the one feature here whose bugs are invisible — they discard
work on a device nobody is looking at — so it is proved against real conflicts on three
entities before the remaining eighteen follow mechanically. Infrastructure is provisioned
by nobody yet, so development runs against a local Postgres — Homebrew rather than Docker,
so there is a database to leave running and point `psql` at, see `docs/CONTRIBUTING.md` —
and deployment waits until there is something worth deploying.

- ✓ Sync semantics decided before any of it is built — see
  `docs/adr/0008-synchronisation-semantics.md`, accepted once the schema below was built on it
- ✓ Ktor server sharing `core:model` — deployed on EC2 behind Apache with a Let's Encrypt
  certificate, serving `api.yellowtrackstudios.com` against a Postgres on its own volume
- ✓ Postgres schema through Flyway, with a test that it has not drifted from SQLDelight's —
  twenty-five tables mirrored, compared column by column against the committed SQLDelight
  snapshot, and the three deliberate divergences asserted to be the only ones. `server_seq`
  is assigned on insert *and* update, which was checked by breaking it. `sync_state` and
  `sync_conflict` are not here: both need a matching client migration, so they land with the
  synchronisation itself rather than ahead of it
- ◐ Accounts, authentication, and Row Level Security on `studio_id` — see
  `docs/adr/0009-accounts-authentication-and-tenant-isolation.md`. A studio signs up, signs
  in with an Argon2id-hashed password, and gets a revocable token; every business table is
  behind a Postgres policy that returns **nothing** when a request forgets to name its
  studio, rather than returning everything. Proved by breaking it three ways. The client
  wiring is in and **all four platforms have signed in against the deployed server** —
  desktop, Android, iOS and the browser build.

  Two things about that were only learned by deploying. Migrations must run as the schema's
  owner and requests as `yellowtrack_app`, which owns nothing: one credential cannot do
  both, because the role that owns a table is exempt from its own policies. And the role
  has to be created by hand, since the owner deliberately holds no `CREATEROLE`
- ✓ Synchronisation for `Client`, `Project` and `Session`, landing on a schema that has been
  ready for it since 0.3.0. The server reconciles: a device pulls everything past its cursor
  in one ordered pass across all three tables, pushes what it has, and gets back what became
  of each row. Conflicts are detected on `version`, resolved by arrival, and **the losing
  version is kept in full** so a studio can read back what reconciliation discarded;
  tombstones beat concurrent edits, and the discarded edit is kept too. Checked by breaking
  it three ways, including the one that matters most — a cursor stepping past rows nobody
  would ever be sent again. The device half is in too: mutations queue to the `outbox` that
  has been waiting unused since the first migration, the drain collapses three offline edits
  into one upload and **re-reads** rather than sending what was queued, pulled rows are
  applied without being queued straight back, and the cursor advances only after the page it
  describes is written. Conflicts travel down, so the discarded version reaches the device.
  Checked by breaking the ordering, the re-enqueue guard and the collapsing. **Settings now
  shows what was discarded** — which entity, when, and the fields that actually moved, with
  the losing value beside the one that was kept — so ADR 0008 decision 3's condition on
  last-write-wins is finally being met rather than merely stored for, and the Dashboard
  carries a banner so a studio finds out without going looking, which is the half of
  decision 3 that decides whether the other half is worth having. `core:network` now carries
  the real transport, and `SyncOverHttpTest` runs it against the real routes — the two halves
  had never actually spoken before that. The wire contract lives in `core:model` and is
  compiled into both sides, so it can no longer drift the way two hand-kept copies could.
  Each platform now keeps its token where that platform keeps credentials — Keychain on
  iOS, a keystore-wrapped preference file on Android, an owner-only file on desktop,
  `localStorage` in a browser, which `isHardwareBacked` is honest about rather than
  implying a protection browsers do not offer. **All four have now been run**: every
  platform has signed in against the deployed server and kept its session
- ✓ Password reset, and the mail transport 0.6.0 also wanted — see
  `docs/adr/0010-password-reset-by-emailed-code.md`. A code rather than a link, because
  there is no web front end for a link to land on. Requesting one answers the same whether
  or not the address has an account; a completed reset revokes every session. Proved against
  a real SMTP server: two requests, identical answers, one email actually sent, the code
  read out of the delivered message, old password refused, new password accepted, reuse
  refused, two sessions revoked. The application has the screen too: "I have forgotten my
  password" on the sign-in form, an address, then the code and a new password, with the
  wording kept as non-committal as the server's answer. **Driven live, whole loop:** a
  revoked token signed the device out by itself, the reset was asked for and the code read
  out of the delivered email, the new password was set, every session was revoked, and
  signing in with it came back to the Dashboard
- ✓ **The conflict path has been watched working against a real server.** Two devices were
  put on version 2 of the same client with different names; the server detected it, kept the
  displaced version in full, and the device pulled it down. The Dashboard showed "1 change
  was overwritten", Settings narrowed two whole payloads to the one field that moved —
  Kept: *Renamed on the desktop*, Set aside: *Renamed on the laptop* — and dismissing it
  did not reopen on the next sync. That is ADR 0008 decision 3, the condition the whole
  last-write-wins choice rests on, holding outside a test for the first time
- ✓ Synchronisation actually runs, and has been watched doing it. A client typed into the
  desktop application reached Postgres under the signed-in studio, and a row inserted
  server-side arrived in the application — both directions, against a real server. The
  studio is now the signed-in one rather than the placeholder constant `StudioContext` had
  returned since 0.3.0, without which every push would have been refused as another
  studio's row
- ✓ A way in. The application opens on sign-in until a session exists, one form for both
  signing in and starting a studio, and it says plainly when the device cannot store the
  session securely rather than implying it can
- ◐ Deployment. `docs/DEPLOYMENT.md` covers one EC2 instance running Apache, Postgres and
  the server, with SES for mail — written for that shape rather than generically, because
  the three things that fail *silently* are all shape-specific: connecting as a superuser
  makes every row level security policy inert, SES's sandbox makes password reset appear to
  work and never arrive, and same-box Postgres means one lost instance is one lost business.
  The code side is done: the server URL is generated from the build rather than hardcoded to
  loopback, CORS is configurable for the browser build, and `/ready` reports whether the
  database and mail are actually reachable. **It is provisioned and running**: Postgres 18
  on its own EBS volume, Apache terminating TLS, systemd, daily backups to S3 with a
  restore rehearsed rather than assumed, and SES sending. `scripts/verify-deployment.sh`
  checks the failures that do not announce themselves
- ✓ **The remaining twenty-two entities**, so a studio signing in on a second device gets
  its whole business rather than three tables of it: contacts and their attachments,
  invoices and payments, quotes, contracts, expenses, mileage, crew, shots, releases,
  deliverables, post-production tasks, gear, packing, storage, media copies, lighting
  recipes, service templates, and the studio profile every document is built from.

  Four defects surfaced while doing it, each invisible from inside the application and each
  now guarded by a test written after the fact rather than before:

  - **Deletes had never propagated.** Push re-read each queued row through a repository, and
    every repository read filters `deleted_at IS NULL` — so a deleted row came back absent,
    was taken for one that had never existed, and its outbox entry was discarded. Anything
    deleted on one device stayed on every other one indefinitely
  - **Entities could be declared and never pulled**, by being left out of the list the pull
    is built from
  - **Entities could be declared and never pushed**, by being left out of the read that
    fills the push
  - **Entities could be wired end to end and never queued**, because their repository did
    not enqueue — so a device pulled them from other devices and never sent its own

  Adding an entity means threading it through four layers, and each has its own way of
  dropping one silently. There is now a guard per layer

- ✓ Pages that stand alone. A page ordered by `server_seq` is not one a device can apply:
  an edit bumps the sequence, so a parent edited after its own child arrives a page later
  and the child fails a foreign key — identically on every retry, because the cursor only
  advances once a page is written. Each entity declares what it references and a pull closes
  each page over its parents. That is what made it safe to **enforce foreign keys on the
  devices**, which the schema had declared and nothing had ever checked

- ✓ Per-studio singletons keyed by their studio. `studio_profile`, `codb_profile` and the
  seeded `service_template` rows took generated ids, which is why none of them could
  synchronise: two devices would each create their own, and the second push would violate a
  unique index rather than merge. Migrations 15 and 16 rewrite what is already on devices,
  and a template the studio has renamed keeps its own id — it has become theirs

- ✓ Failures that say what happened. Three separate afternoons were spent on a healthy
  server that looked broken, so: the clients default to the deployed server rather than
  loopback, transport and parse failures are classified rather than falling through to
  "something went wrong here", and a pull carries the tables the server reconciles so a
  device can say when it is talking to a server older than itself. That last one is the
  cause of the worst of them — `ignoreUnknownKeys` means an old server **discards entities
  it has never heard of and answers successfully**, so every screen reads "Up to date"
  while a category of the studio's work stays on one device

- **Moved to 0.8.0 — object storage for media, via presigned URLs.** Nothing consumes it:
  no entity in the domain model holds an image or attachment, and `media_copy` records where
  files sit on the studio's *own* drives, which 0.6.0 said would stay a card-reader job.
  The thing that needs it is client proofing, and that is 0.8.0 — where the gallery will
  decide the shape of it rather than a guess made a milestone early

## Before another studio uses it

Not a milestone so much as the gap between *working* and *fit to hand to someone else*. It
is listed before 0.8.0 because every item here blocks a real user more than a proofing
gallery does, and because none of it was written down until the thing was actually deployed
and used.

**In the product**

- **Editing a saved record.** Carried from 0.4.0 and now the largest functional hole: a
  studio can raise an invoice but not correct a mistyped amount, or fix a payment recorded
  against the wrong booking. Someone will need this on their first day
- **Emailing a document.** Quotes and invoices render and can be shared; sending one from
  inside the application is wiring, since the mail transport exists
- **The share sheet on Android and iOS** is compiled and has never been run. Sign-in has
  now been exercised on both, so the session stores are proved — this is not
- **Two studio names.** `studio.name` is fixed at sign-up and shown in Settings → Account;
  `studio_profile.name` is the editable one every document carries. They can disagree
  permanently. One of them should stop existing, and `adoptStudioName` — a workaround from
  when the profile could not travel — should probably go with it
- **Account deletion and data export.** The application holds other people's clients,
  addresses and payment histories, with no way to give that back or remove it

- **Most of what the studio enters still cannot be removed.** Of the forty-five write
  methods the repositories declare, eleven were never called from any screen — and ten of
  those were deletes. A booking, shoot day, lead, quote, contract, cost or journey can still
  be created and never removed. Gear, lighting recipes, post-production tasks, deliverables
  and invoices *can* be, which makes it inconsistent rather than simply absent. The data
  layer has every path, and synchronisation carries tombstones correctly; nothing calls most
  of it
  - ✓ **A payment** can be taken off the invoice it was put against — the case that hid
    itself, since a misattributed payment settles its invoice and a settled invoice leaves
    the money-owed list
  - ✓ **A client** can be removed when nothing is booked against it. Bookings hold the
    account in place and say so, because `Session.projectId` and `Invoice.projectId` cannot
    be null: shoot days, invoices and payments hang off a booking, never off the client. So
    an account with no bookings genuinely has nothing behind it, and a cascade here would be
    the destruction of a year's accounts by one press rather than a correction
  - ✓ **A booking** can be removed when nothing at all is attached, and what is attached is
    named and counted — "2 invoices and 1 shoot day on it" rather than a bare refusal.
    Nothing cascades: eight kinds of record point at a booking and six cannot exist without
    one, so taking its invoices and payments along would delete the record of money that
    actually changed hands
  - ✓ **A shoot day** can be removed when nothing was recorded on it. The refusal matters
    more here than anywhere else: a backup row is the only record of *where the client's
    photographs are*, and a talent release is the written permission to use somebody's face.
    Neither is recoverable from anywhere else in the application, so both are named first
  - **The chain now closes for a booking with shoot days on it** — remove the day, then the
    booking, then the client — because everything a shoot day holds (backups, releases,
    crew, shots, packed gear) already had a way off the screen. Deliverables,
    post-production tasks and draft invoices did too
  - **Quotes, contracts, costs and journeys still have no way off.** A booking carrying any
    of them is stuck, and so is the client above it. These are the last four links, and
    unlike shoot days they are each a single row with nothing beneath them — the guard
    pattern will not be needed

- **Service templates can only ever be the four that are seeded.** `saveTemplate` is never
  called, so a studio cannot add its own package or change a default's price — while the
  pricing floor measures packages against the floor. The one screen where a studio's own
  offering should live has no way to put it there

**In the operation**

- **Nothing watches the server.** No alert when the process dies, the disk fills, renewal
  fails, or backups stop. Today the studio finds out first
- **SES is in the sandbox.** Confirmed, not suspected. `mail:true` says configured, not
  permitted: until production access is granted, a password reset for anyone but a verified
  address answers `202` and never arrives — which ADR 0010 makes deliberately
  indistinguishable from success, so nobody would ever report it. This is the hard blocker
  on a second studio, and it is an AWS review rather than something to build
- ✓ **Backups run, and the restore rehearses itself.** `yellowtrack-backup.timer` writes a
  dump nightly; `yellowtrack-restore-check.timer` rebuilds the newest one weekly into a
  scratch database and exits non-zero if fewer tables come back than the schema has.
  `verify-deployment.sh` checks the *result* rather than the schedule, because a failed unit
  nobody looks at is not much better than no unit. Units in `docs/DEPLOYMENT.md`
- **No way to install it.** No TestFlight, no Play track, no hosted web build
- **One instance, no staging.** Every deploy goes straight to what studios are using, and
  there is nowhere to catch a bad migration first

**In what has actually been proved**

- **One studio has ever existed.** Tenant isolation is enforced by Postgres and proved by
  tests that break it deliberately, but two real studios have never shared this server
- **Synchronisation runs every five minutes** and at no other time — not when the
  application comes to the foreground, not after a write, and not at all while it is
  closed. The interval is a guess its own comment asks to revisit once somebody has used
  this on a shoot day

## 0.8.0 — Collaboration

- Client proofing, selections, and approvals — and the object storage they need, moved here
  from 0.7.0 because the gallery is what decides its shape
- Second shooters and editors, with roles

## 0.9.0 — Release Candidate

- Accessibility, performance, and migration validation
- Localisation — `DateFormats` is English-only today

## 1.0.0 — Launch

- Production-ready Studio OS
- Stable data model and documented upgrade path
