# Yellow Track Platform Domain Model

> **Build with intention. Create without friction.**

**Status:** Living Document
**Version:** 1.0
**Last Updated:** 2026-07-28

---

# Purpose

This document maps the whole business Yellow Track Studios runs, so that entities can be
implemented incrementally against a graph that does not need rework.

Modelling on paper is cheap. Migrating a live multi-tenant Postgres database is not.

Yellow Track Studios operates four business lines simultaneously — weddings and events,
commercial brand content and video, real estate and product, and portraits and personal
branding. Their data needs diverge sharply. This model is designed so that one schema
serves all four, rather than four feature areas serving one each.

Only a subset of this map is implemented today. See **Implementation status** at the end.

---

# Foundational conventions

## Every entity carries the same base columns

| Column | Type | Purpose |
| --- | --- | --- |
| `id` | UUID | Client-generated, v7 (time-sortable, index-friendly) |
| `studioId` | UUID | Tenant scope. What Postgres Row Level Security will key on |
| `createdAt` | Instant | Audit |
| `updatedAt` | Instant | Audit, and the basis of last-write-wins sync |
| `deletedAt` | Instant? | Soft delete. Sync requires tombstones — a hard delete cannot propagate |
| `version` | Int | Optimistic concurrency and conflict detection |

**Client-generated IDs are non-negotiable.** A photographer creating a session on a plane,
in a basement venue, or in a field with no signal must produce a permanent, valid ID with
no server round trip. Sequences and auto-increment cannot do this.

**`studioId` on every table from row zero.** This is what allows a second photographer to
use the platform later without a migration, and what makes Postgres RLS a one-line policy
per table rather than an application-wide audit.

## Money is never a floating-point number

```kotlin
Money(minorUnits: Long, currency: CurrencyCode)
```

Currency is stored explicitly, not assumed. Commercial and destination-wedding work
crosses currencies.

## Time is always zoned

A `Session` has a start and end that are meaningless without a zone. A destination wedding
booked from home, a client in another country, and a shoot that crosses a DST boundary all
break naive local times. Store the instant and the zone.

---

# The two modelling decisions that shape everything

## 1. A Client is an account, not a person

The naive model — `Client(firstName, lastName)` — fails immediately against real work:

- A **wedding** client is a couple. Two people, both decision-makers, frequently with
  different surnames, often with a planner who does most of the actual communicating.
- A **commercial** client is a company. A marketing manager briefs you, a creative director
  approves, and an accounts-payable address you have never spoken to pays the invoice.
- A **real estate** client is an agent who books repeatedly on behalf of different owners.

So `Client` is an account. `Contact` is a person. They join through a role.

```
Client (account)  ──< ClientContact >── Contact
                       role: Primary | Partner | Planner | Billing
                           | Creative | OnSite | Assistant
```

This also means a `Contact` can belong to more than one `Client` — the wedding planner who
refers you five times a year is one person across five accounts, and that is exactly the
relationship you want visible.

## 2. A Project is the booking; a Session is a day of shooting

Treating the session as the unit of booking is the most common modelling error in this
domain, and it makes job profitability unanswerable.

- A **wedding** is one engagement containing an engagement shoot *and* the wedding day.
- A **commercial job** is one engagement containing a scout day, a shoot day, and a pickup day.
- A **real estate** listing may be one visit, but a **product** campaign is several.

`Project` is the commercial container: one client, one contract, one quote, one invoice
set, one profit-and-loss. `Session` is a scheduled block of shooting inside it.

```
Client ──< Project ──< Session ──< Shot / CrewAssignment / GearCheck / Release
             │
             ├──< Deliverable ──< Gallery
             ├──  Quote, Contract, UsageLicense
             ├──< Invoice ──< Payment
             └──< Expense, Mileage
```

Without this split, "what did that wedding actually earn me?" has no query.

## 3. ServiceTemplate is how one model serves four business lines

Rather than branching the schema per business line, a `ServiceTemplate` supplies the
defaults for a kind of work:

- default session duration and number of sessions
- default shot list
- default gear kit / packing list
- default deliverables (counts, formats, aspect ratios, turnaround)
- default pricing package and add-ons

A Wedding template, a Brand Video template, a Real Estate template, and a Headshot
template then differ in data, not in code.

---

# Entity groups

## Tenancy and identity

| Entity | Notes |
| --- | --- |
| `Studio` | Tenant root. Name, currency, timezone, tax/business details, branding |
| `User` | Auth identity |
| `StudioMember` | User ↔ Studio with a role: Owner, Associate, SecondShooter, Editor, Assistant |

Roles matter earlier than expected: second shooters and editors need to see a session and
its shot list without seeing the client's invoice.

## Connect — the client relationship

| Entity | Notes |
| --- | --- |
| `Contact` | A person. Names, emails, phones, addresses, socials, notes, tags |
| `Client` | An account. Individual or company |
| `ClientContact` | Join with role (see above) |
| `Lead` | Source, `receivedAt`, **`firstResponseAt`**, status, lostReason, desiredDate, budgetRange, referredBy |
| `Interaction` | Timeline of calls, emails, meetings against a contact, lead, or project |

`Lead.status`: `New → Contacted → ConsultScheduled → ProposalSent → Won | Lost`

Two fields here carry disproportionate weight:

- **`firstResponseAt`** — inquiry response time is the single strongest predictor of
  booking rate in this industry. Measuring it changes behaviour.
- **`source`** — Instagram, referral, Google, directory, repeat client, vendor referral.
  Without attribution you cannot tell which marketing effort is worth repeating, and
  most photographers spend years guessing.

## Create — the work

| Entity | Notes |
| --- | --- |
| `Project` | The booking. Client, status, service template, value, dates |
| `Session` | A shooting block. Start/end **with timezone**, status, call time, locations |
| `ServiceTemplate` | Defaults per kind of work (see above) |
| `Location` | Address, lat/lng, parking, power availability, permit required + cost, indoor/outdoor, access notes |
| `Shot` | Description, reference image, must-have flag, **group**, captured/skipped |
| `CallSheet` | Video: crew call times, schedule blocks, weather, nearest hospital |
| `CrewAssignment` | Person, role, rate, call time, confirmed |
| `Release` | Model or property release, per person or property, per session |

Notes from practice:

- `Shot.group` exists for **family formals**. "Bride + Grandmother" is a shot that takes 40
  seconds if it is on a list and 10 minutes of shouting into a crowd if it is not. Grouped,
  ordered shot lists are the difference between a calm and a chaotic wedding hour.
- `Location.permitRequired` and `permitCost` matter for commercial and real-estate work.
  Being shut down by a building manager is expensive and entirely preventable.
- `CallSheet.nearestHospital` is not decoration — it is standard on professional call
  sheets and often contractually required on crewed shoots.
- `Release` blocks portfolio use. An image without a signed model release cannot be
  published, and discovering that after publishing is a legal problem.

## Craft — gear and technique

| Entity | Notes |
| --- | --- |
| `GearItem` | Make/model, **serial**, purchase date + price, current value, warranty, insurance policy, condition, shutter count, firmware, owned vs rented |
| `GearKit` | Packing template, per service template; checked state per session |
| `MaintenanceRecord` | Sensor cleaning, calibration, repair, next-due date |
| `MemoryCard` | Label, capacity, first-use date, **retirement date**, current contents |
| `LightingRecipe` | Diagram, modifiers, power settings, distances, camera settings, result reference |

- **Serial numbers and purchase prices are an insurance claim.** After a theft, an insurer
  wants a documented inventory. Assembling one afterwards is impossible.
- **Cards should be retired on a schedule**, not on failure. A card that fails mid-wedding
  is an unrecoverable event, and tracking first-use date is how you retire them in time.
- `LightingRecipe` is where a new photographer's learning compounds — recording what
  produced a result you liked converts luck into repeatability.

## Post-production — the pipeline where photographers drown

| Entity | Notes |
| --- | --- |
| `MediaBatch` | Session → card → destination volumes, checksum verified, copy count |
| `StorageVolume` | Drive label, type (working SSD, NAS, archive, cloud), capacity, free space, last verified |
| `PostTask` | Cull, edit, retouch, colour, export, deliver. Assignee, **estimated vs actual hours** |
| `Deliverable` | Quantity, formats, aspect ratios, resolution, due date, **revision rounds allowed vs used**, turnaround SLA |
| `Gallery` | Link, expiry, download count, client favourites and selections |
| `ArchivePolicy` | When RAWs are purged, where the archive lives, retention period |

- `MediaBatch` + `StorageVolume` together answer **"is this wedding backed up in three
  places yet?"** — the actual 3-2-1 rule. Losing a wedding's files ends a photography
  business, and the window of exposure is the hours between card and third copy.
- **`PostTask.actualHours` is the most valuable number in the system.** It is the input to
  effective hourly rate, and it is the number that reveals a "profitable" package is
  actually paying below minimum wage once culling and editing are counted.
- **`Deliverable.revisionRoundsAllowed` vs `revisionRoundsUsed`** is the primary defence
  against scope creep in video work. Unlimited revisions is how a profitable video job
  becomes a loss.
- `Deliverable.aspectRatios` — modern brand work is delivered 16:9, 9:16, *and* 1:1. Being
  explicit prevents the "can you also send it vertical?" reshoot.

## Money — the layer that decides whether the business survives

| Entity | Notes |
| --- | --- |
| `PriceList` / `Package` / `AddOn` | Base price, included deliverables, overage rates |
| `Quote` | Line items, validity window, accepted date |
| `Contract` | Template, signed date, signature record, cancellation, reschedule, weather clause, turnaround SLA |
| `UsageLicense` | Media, territory, duration, exclusivity, renewal date |
| `Invoice` / `InvoiceLine` / `Payment` | Retainer vs balance, due dates, method, late fees |
| `Expense` | Category, deductible flag, receipt image, linked to a Project for job costing |
| `Mileage` | Trips per project |
| `CodbProfile` | Annual fixed costs + target salary + billable days → **minimum viable session price** |

- **`UsageLicense` renewals are recurring revenue most photographers never invoice for.**
  A commercial image licensed for one year, in one territory, for web only, is a renewal
  conversation twelve months later. Nobody has that conversation without a reminder.
- **`Expense` linked to a Project is job costing.** Second shooter fees, parking, props,
  permits, travel, and album costs come out of a specific job's revenue. Untracked, the
  margin is imaginary.
- **`Mileage` is systematically forgotten**, and it is deductible. It is pure recovered money.
- **`CodbProfile` is the most valuable calculation in the platform and almost no competitor
  offers it.** Cost of Doing Business — annual fixed costs plus target salary divided by
  realistically billable days — yields the price below which a session loses money. New
  photographers underprice because they have never computed this number.

## Improve — insight

| Entity | Notes |
| --- | --- |
| `PortfolioEntry` | Image, tags, session type, where published, release status, usage rights |

Metrics worth deriving, in rough order of usefulness:

1. **Effective hourly rate** — revenue ÷ (shoot + cull + edit + admin + travel hours).
   The number that changes pricing decisions.
2. **Lead conversion rate** and **median first-response time**, correlated.
3. **Revenue by lead source** — where bookings actually come from, versus where effort goes.
4. **Average project value** by service template.
5. **Seasonality** — booking density by month, which is how you plan cash flow.
6. **Repeat and referral rate** — the cheapest revenue there is.
7. **Portfolio gaps** — service types you want to sell but cannot show.

---

# Dependency direction

`core:model` holds these entities as pure Kotlin data classes with `@Serializable`.

It must depend on **no Compose and no SQL**. That constraint is deliberate: it is exactly
what allows the future Ktor server to depend on the same module, so that adding a field to
`Session` becomes a compile error on both client and server rather than a production bug.

---

# Implementation status

| Group | Status |
| --- | --- |
| Base columns, `Money`, ID factory | Milestone 0.3.0 |
| Contact, Client, ClientContact, Project, Session, ServiceTemplate | Milestone 0.3.0 |
| Lead, Quote, Contract, UsageLicense, Invoice, Payment, Expense, Mileage, CodbProfile | Milestone 0.4.0 ✓ |
| Interaction | Not yet implemented |
| Location, Shot, CallSheet, CrewAssignment, Release | Milestone 0.5.0 |
| MediaBatch, StorageVolume, PostTask, Deliverable, Gallery | Milestone 0.6.0 |
| GearItem, GearKit, MaintenanceRecord, MemoryCard, LightingRecipe | Milestone 0.6.0 |
| Studio, User, StudioMember, sync | Milestone 0.7.0 |

Entities not yet implemented still live on this map. The base columns and tenant scope are
applied from the first table onward so that later groups arrive without a migration.
