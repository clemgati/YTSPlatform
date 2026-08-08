# Changelog

All notable changes to Yellow Track Platform will be documented here.

The project follows semantic versioning.

## How to read this file

**This file lapsed after 0.6.0 and was not kept through the nineteen releases that
followed.** `docs/ROADMAP.md` carried the account instead, in more detail than this ever
did, and remains the better record of *why* each thing was done and what is still unproved.

That is worth stating rather than quietly backfilling, because the two halves below are not
the same kind of document:

- **The release index** was reconstructed from git history on 8th August 2026. The versions,
  the dates and the headline of each release are accurate — they come from the commits.
  The detail is not the detail somebody would have written on the day, and it does not
  pretend to be.
- **The 0.3.0 to 0.6.0 entries** below it are the original ones, written at the time and
  left as they were. Their "Unreleased" headings are now wrong in a specific way: all of
  that work shipped in **1.0.0** on 4th August 2026. The heading is kept so the entries are
  not silently re-dated.

Anything after 1.11.0 belongs in the release index, written when it ships.

## Release index

Reconstructed from git history. One line per release; `docs/ROADMAP.md` has the reasoning.

| Version | Date | What shipped |
| --- | --- | --- |
| 1.11.0 | 2026-08-08 | Reconciling when the application is looked at again — a foreground signal per platform, proved on a phone |
| 1.10.0 | 2026-08-08 | A reconnect trigger for the desktop, measured end to end after three false readings |
| 1.9.1 | 2026-08-07 | A failure keeps its throwable, and the desktop writes to a log file beside its database |
| 1.9.0 | 2026-08-07 | The figures behind the pricing floor became adjustable, and the profit the working omitted is shown |
| 1.8.0 | 2026-08-07 | The browser keeps its database across reloads; the mobile sidebar scrolls; the version shows before sign-in |
| 1.7.1 | 2026-08-07 | The crash 1.7.0 shipped: `registerNetworkCallback` needs a permission the manifest never declared |
| 1.7.0 | 2026-08-07 | A returning connection brings a sync forward; the offline-saving promise withdrawn from the screens |
| 1.6.0 | 2026-08-07 | Clients write through the server, and the conflict machinery is deleted — ADR 0012 finished |
| 1.5.0 | 2026-08-07 | Bookings, sessions and enquiries write online; 154 manufactured conflicts cleared, twice |
| 1.4.1 | 2026-08-06 | The rest of the ledger writes through the server |
| 1.4.0 | 2026-08-06 | Invoices and payments are written online and awaited — ADR 0012 step 1 |
| 1.3.3 | 2026-08-06 | A write brings the next sync forward; a pull stops overwriting work that has not been sent |
| 1.3.2 | 2026-08-06 | The client's own address is offered when emailing a document |
| 1.3.1 | 2026-08-06 | Converting an enquiry can open its booking too |
| 1.3.0 | 2026-08-05 | An enquiry that is won becomes a client without retyping what it already said |
| 1.2.1 | 2026-08-05 | A document records that it was emailed, and the row says so |
| 1.2.0 | 2026-08-05 | A studio emails a quote or an invoice to its client, from its own name — ADR 0011 |
| 1.1.1 | 2026-08-05 | The thirty-day deletion window got a door: signing in recognises the state and offers the studio back |
| 1.1.0 | 2026-08-05 | Account deletion with a window, data export, and something that watches the server |
| 1.0.1 | 2026-08-04 | An address is checked for shape at sign-up; the web vhost stops serving a cached old build |
| 1.0.0 | 2026-08-04 | Launch — an enquiry to a paid invoice, on four platforms, against one server |

## Unreleased — 0.6.0 Pipeline

### Added

- **Android and iOS hand a document to the system share sheet.** A call sheet reaches a
  second shooter, and an invoice reaches a client, without the file having to be found in a
  folder first
- **Saving happens first, and unconditionally.** Presenting a share sheet touches window
  hierarchies and content providers, which fail in ways that compile perfectly — so the
  document is written before anything that can fail, and the saved location is reported even
  when no sheet appears. A studio that pressed *Send* ends up with the file either way. Two
  tests pin that ordering
- The button says what the platform will actually do: *Send it* where there is a sheet,
  *Save as a web page* where there is not. Labelling both the same would promise a share
  sheet that was never coming
- Android shares through a `FileProvider` scoped to the folder the documents are written
  to — handing another application a `file://` URI has thrown since Android 7, and a
  provider that shares more than the thing being shared is a way to leak the database. The
  authority is derived from `packageName` rather than repeated, so it cannot drift from the
  manifest
- `androidx.core` is held at 1.16.x: from 1.17 it requires compileSdk 37, and AGP 9.0.1
  recommends 36 as its maximum. It is used for `FileProvider`, which has not changed

### Known gap

- **Neither share sheet has been run, only compiled.** The manifest merge, the provider
  authority and the paths declaration were checked statically, which rules out the usual
  failures — but no one has opened this application on a phone, so the sheet appearing is
  unproven. The save-first ordering exists precisely because that is true

- **"Verified" now means the application read the drive.** Since the backup work landed it
  has meant somebody pressed a button. A drive can fail silently and a folder can be moved,
  so a tick recorded without reading anything is a backup nobody has checked wearing the
  label of one that has been. A copy can now carry a path; *Check now* opens it, walks it,
  and records how many files were found
- **A failed read is not a verification.** A drive nobody plugged in leaves the previous
  result standing rather than stamping today's date on it — "checked today and found
  nothing" must never read as a check that passed. An empty folder is reported as an empty
  folder, which is the failure a studio most needs told and the one it is least likely to
  suspect
- A count and a tick stay distinguishable. Where the application read the drive the row
  says so — "2,481 files read" — and where a studio ticked a cloud copy by hand there is a
  date and no count, so a tick never borrows the authority of a count
- The browser has no filesystem, and says so rather than reporting every backup missing.
  `VolumeInspector` is implemented per platform in the same shape as `DatabaseDriverFactory`
  and `DocumentSink`, and declares whether the device can read at all
- **Migration 13 → 14**, purely additive and all three columns nullable: existing copies
  have no path, were never read, and keep reading as ticked-by-hand rather than as failed
  checks

- **A register of the studio's drives.** Copies have carried a free-text volume name since
  earlier in this milestone, so one drive named on twelve shoots was twelve unrelated
  strings. A studio could ask whether one wedding was safe; it could not ask the question
  that actually gets asked the moment it matters — *this drive has died, what was on it?*
- Where a drive lives belongs to the drive, not to each copy on it. A drive kept at a
  relative's house is offsite for every shoot on it, and asking a studio to remember that
  per copy is asking it to get it wrong
- **The rule now knows about dead drives.** A copy on a failed or lost volume is not a copy,
  so `BackupHealth` excludes it — and *counts* it rather than merely subtracting it, because
  the difference between "you have two copies" and "you had three and one is on a dead
  drive" is the difference between a studio that acts today and one that does not. A lost
  drive also stops counting as the offsite copy, which is exactly the case where a studio
  would otherwise believe 3-2-1 was still satisfied
- Where the register knows nothing about a volume, the copy is trusted. Absence of a record
  is not evidence of failure, and every studio that has not built a register would otherwise
  be told it had lost everything
- Recording a copy now picks a drive from the register, with "somewhere else" still
  available — building a register is not made a precondition of recording a backup, because
  the copy exists whether or not the drive has been catalogued
- **Migration 12 → 13**, purely additive: existing copies keep their names and get a null
  `volume_id`, which reads as "not in the register" rather than as a broken reference.
  Nothing is migrated into volumes automatically — matching drives by free text would join
  "Red Samsung T7" and "red samsung t7" or fail to, and guessing wrong would attach a shoot
  to a drive it was never on

- **The starting templates no longer carry invented prices.** A studio found four packages
  waiting for it on first run, priced in dollars — figures the application made up, in a
  currency the studio had not chosen, which the Ledger then measured against that studio's
  real pricing floor and reported as under- or over-priced. The comment above them already
  said every figure was "a placeholder the studio is expected to replace"; they are now
  absent rather than hoped-about. What is seeded is the shape of the work — duration,
  sessions, deliverables, turnaround — which is the same in every country
- The pricing screen already handled a template with no price: it shows the minimum the
  floor requires for those days and leaves what the studio charges blank, which is the
  honest order to fill the two in

- **The application was silently dollars-only.** `CurrencyCode` has carried a comment since
  it was written saying a studio's currency "is a per-studio setting rather than a global
  constant" — and it was a global constant: a hardcoded default argument on six ViewModels,
  settable nowhere. Every price, total, and figure on screen said dollars whatever the
  studio actually charged, and once invoices started leaving the building in the same
  milestone, so did they
- The currency now lives on the studio profile, beside the name and the tax number, with
  one fallback in one place rather than a default repeated per ViewModel — which is exactly
  how it became a global constant the first time. **Migration 11 → 12** adds the column,
  defaulted to `USD` so every existing row keeps working
- **Money in two currencies cannot be added, and now the Ledger says so.** `Money` refuses
  to add pounds to dollars, which is right; `buildMoneyOwed` was summing across every
  outstanding invoice without checking, so the first studio to change currency would have
  taken the whole screen down. The totals now cover what matches and report what they leave
  out — a total quietly missing an invoice is worse than one that says it is short. The
  expense and proposal summaries had filtered this way from the start; this one had not,
  which went unnoticed while every figure in the application was hardcoded to dollars
- Found by a test that hung rather than failed: the exception was caught into an error
  state, so waiting for a success that never came looked like a slow test

- **Studio details, at last.** The Settings screen has said since 0.1.0 that there was
  nothing to configure and that studio details "arrive with the account model". They are
  needed sooner: documents started leaving the building in this same milestone, and accounts
  are not until 0.7.0. `StudioProfile` holds one row per studio — name, address, contact,
  tax registration number, how to pay, and a footer
- **Only the name stops a document.** A client who cannot tell who an invoice is from does
  not pay it, and the studio finds out when the money does not arrive. Everything else is
  reported as a gap the client will merely notice — an invoice with no way to pay it is a
  common and expensive omission, and in most jurisdictions one with no tax number cannot be
  claimed against
- **Invoices and quotes as documents a client can read.** Every figure is taken from the
  `Invoice` itself, which computes it from its own lines and payments — nothing is
  recomputed for the page, because a document arriving at a different total from the screen
  it was sent from would be the worst bug this application could have
- An overpayment reads as "Overpaid $1,000.00" rather than a negative balance due, because a
  client reading *minus one thousand due* starts an argument. Bank details are printed only
  while something is still owed: on a settled invoice they buy a refund, an apology and an
  afternoon. A quantity of one is not printed at all — "1 × $4,000.00" beside a total of
  $4,000.00 is a line the reader has to check before discarding
- The document model now separates lines that belong together from prose. An address
  rendered as paragraphs took a third of the page and read as seven unrelated facts; caught
  by looking at the rendered invoice rather than the code
- **Migration 10 → 11**, purely additive, with a unique index on `studio_id` enforced in the
  schema rather than in code: two profiles for one studio would put two different names on
  two invoices, and a sync conflict is exactly how that would happen

- **Documents can leave the application.** Everything built so far has been readable only
  by the person holding the laptop. A new `core:export` module renders a document from the
  domain and a `DocumentSink` decides where it goes, per platform, in the same shape
  `DatabaseDriverFactory` already uses
- **Call sheets, which 0.5.0 has been waiting on.** The session page has read as a call
  sheet since that milestone and has never been able to send one. It now carries where to
  be, when, who else is coming, and what was promised — and deliberately carries nothing
  about the money, because this document leaves the studio and a second shooter has no
  business seeing what the wedding cost. A test asserts that absence rather than trusting it
- **Copy as text is offered before save as a file**, because that is what actually happens:
  a second shooter is sent a message, not an attachment, and a sheet that has to be
  downloaded and opened is one that gets read at the venue rather than the night before
- **HTML rather than PDF.** A PDF library needs a per-platform implementation on four
  targets; an HTML page opens on any phone and prints to PDF from the browser, which is
  where the PDF was going to be made anyway. The page is self-contained — inline styles,
  no scripts, nothing fetched — because it is opened at a venue with no signal
- The sheet always states which time zone it means, unlike the screen it came from. A
  screen is read by the person who typed the times in; this is read by a second shooter
  flying in, and conditioning that line on the *sender's* device zone would decide what a
  stranger needs to know from where the laptop happened to be
- Both golden hours are printed, but only the one falling inside the hours being shot is
  emphasised. On a two-o'clock wedding a 5:49 AM window in bold makes the 7:55 PM window
  harder to find, and that is the one decision the sheet exists for
- Where the file landed is reported back on screen. A document nobody can find was not
  saved, and silence is how that happens. On Android and iOS it is written to a folder the
  Files app can see; handing it to a share sheet needs an `Activity` or a
  `UIViewController` and is deliberately not faked
- No studio name appears on the sheet: there is no `Studio` entity until accounts arrive in
  0.7.0, and printing a placeholder on a document that leaves the building would be worse
  than printing nothing

### Fixed

- **The web build could not render a shoot day.** `kotlinx-datetime` resolves zone ids on
  wasm through `@js-joda/core`, which ships with no zone data, so `TimeZone.of(...)` threw
  `Invalid zone ID` for every zone — and every session carries one. The database is now
  imported at start-up. Found because the call-sheet tests were the first in `commonTest`
  to name a zone, so they ran on wasm and failed there

- **The Studio tab is a screen at last.** It has been a placeholder since 0.1.0, naming
  gear inventory, packing lists and lighting recipes as things that "arrive in a later
  milestone". They have arrived
- **An inventory that answers the question a claim turns on.** Nobody keeps a gear list to
  remember what cameras they own; they keep it so that a break-in a year from now can be
  settled — which turns on serial numbers and prices, not on names. The screen leads with
  the insured total and with what would lose the claim: priced gear with no serial number,
  named
- **The total says what it is.** It is what the studio *paid*, not what replacement would
  cost, and the line under it says so. A studio that insures a 2019 body for its 2019 price
  is underinsured in a way it discovers only when it claims. Gear with no price recorded is
  counted separately, so a short total is never read as a complete one
- Sold or written-off gear leaves the insured total, because insuring a camera sold two
  years ago is money spent for nothing. Gear at the repair shop stays on it — it is still
  the studio's, and can still burn with the shop. So does lost gear, which is exactly what
  gets claimed for
- Servicing is reported only for gear the studio has serviced at least once. A reflector is
  not overdue a shutter count, and a list that is wrong about half its rows gets ignored
- **Packing lists live on the shoot day, not in a drawer.** Packed and returned are tracked
  separately because they are ticked at opposite ends of the day: packing in a calm studio
  in the morning, returning in the dark at the end of a fourteen-hour wedding — which is
  when a light stand gets left behind a curtain and is not missed until the next booking
- Ticking something back in also marks it packed. At midnight the only thing being checked
  is what came off the van, and refusing the tick because nobody ticked the morning box
  would teach a studio to stop using the list. Unticking *packed* clears the return, since
  gear that never left cannot have come back
- Only gear in service is offered for a kit list: a body at the repair shop cannot be
  packed, and offering it would put a line on the list that can never be ticked
- **Lighting recipes.** The same three-light headshot gets rebuilt from memory a hundred
  times and comes out slightly different each time. Written down it is a starting point
  that takes ten minutes instead of forty
- Power, distance and position are free text on purpose. A power reading is "1/4" on one
  light and "6.3" on another, and a position is "camera left, 45°, just above eye line" — a
  normalised figure would have to be translated back before anyone could dial it in
- **Schema migration 9 → 10**, purely additive: `gear_item`, `packing_entry` and
  `lighting_recipe`, `10.db` committed. A recipe with no lights yet stores `[]` rather than
  null, so nothing has to decode defensively, and a new packing entry is neither packed nor
  returned — adding something to the list is not the same as putting it in the van

- **3-2-1 backup checking per shoot.** Three copies, on at least two kinds of storage, at
  least one away from the building. Each clause guards a different way of losing a wedding:
  one copy fails, two copies of the same kind fail together, and everything in one room
  burns or is stolen at once
- **The card in the bag is not a backup.** A camera card is the original, and counting it
  would let a studio believe it had three copies when it had one and two cards. It is
  listed, and excluded from the count
- **What is missing is listed in the order it should be fixed** — a second copy anywhere
  beats a third, and getting one out of the building beats spreading copies across more
  drives in the same room. A studio with a single copy is not told its copies are all
  alike, because the advice it needs is above that
- Cloud and offsite drives count as away without being marked so. Copies are recorded as
  *unchecked*: a drive can fail silently, so a backup nobody has opened is a backup nobody
  knows they have, and that count is surfaced separately from the rule
- The rule lives in `core:model` as `BackupHealth`, not in a screen — it is a fact about
  the studio's data, and the answer should be identical wherever it is asked
- **Schema migration 8 → 9**, purely additive: a new `media_copy` table, `9.db` committed

- **Deliverables, checked against the contract.** `Contract.turnaroundDays` and
  `Contract.revisionRounds` have been stored since 0.4.0 and compared against nothing. A
  studio that has agreed to both and tracks neither finds out it is late when the client
  says so, and gives away a fourth revision on a two-revision contract because nobody was
  counting
- **The due date is computed, not typed.** It is the last shoot day plus the turnaround the
  contract promises, and the row says so — "Due 29 Sept — 45 days after the shoot". Asking
  a studio to work out its own deadline is asking it to get it wrong. A date set by hand
  overrides it
- **The round that exhausts the allowance says the next one is chargeable**, which is the
  moment the money is still recoverable. Going past it is stated plainly, and rounds are
  counted even where the contract sets no limit — a round given away free is still a round
  that happened, and a studio that stops counting loses the evidence
- Work already signed off is never late, however long it took: the question is what is owed
  now, not what to feel bad about. A booking with no contract says so rather than implying
  a promise nobody made
- **Schema migration 7 → 8**, purely additive: a new `deliverable` table, `8.db` committed,
  with the revision count defaulting to zero rather than null so every comparison holds

- **Post-production tasks**, held against the booking rather than a shoot day, with what
  each was expected to take and what it actually took. One wedding produces one cull and
  one edit however many days were shot
- **The pricing floor now measures instead of assuming.** `LedgerMapper` has assumed since
  0.4.0 that an hour with a camera drags two more hours of culling, editing, and admin
  behind it — every minimum price the studio has ever been shown rested on that guess, and
  the code said so in a comment promising it would become a measurement "when
  post-production hours are tracked in the Pipeline milestone". It now does
- The rules for trusting the measurement are as important as the measurement. Fewer than
  three finished tasks and the assumption stands, because a floor built on one unusual edit
  is worse than one built on a stated guess. Unfinished work is excluded — a task half done
  has not overrun, and counting it would flatter every open job. Finished tasks with no
  hours recorded contribute nothing
- The pricing screen says which it is: *"Measured from your finished work"* or *"Assumes
  every hour shooting takes 2.0 more in post"*. A studio that does not know the number is a
  guess has no reason to distrust a price built from it
- **Schema migration 6 → 7**, purely additive: a new `post_task` table, `7.db` committed,
  with hours stored as fractions since half hours are the normal unit of this work

- **A booking has a page of its own** — the third detail screen, alongside the client and
  the shoot day. It carries the agreed value, the dates it was enquired about and booked,
  its shoot days, and its post-production. Selecting a booking on a client's page opens it
  rather than opening a form
- Post-production hours are entered here, which closes the loop: the Ledger can only
  measure what someone has recorded, and until now nothing could record it
- The estimate is asked for when work is added, not when it is finished. An estimate
  written afterwards is a memory of how long it felt, and it agrees with the actual every
  time — which would make the comparison worthless
- Finishing work requires the hours it took. A task closed without them tells the pricing
  floor nothing, and the floor is the only reason any of this is tracked. Reopening one
  clears what it claimed to have taken
- An overrun is reported only once work is finished — a task half done has not overrun, it
  is simply unfinished — and a quarter of an hour either way is not reported at all, since
  flagging six minutes would teach the studio to stop reading the figure
- Editing a booking moved from the client page to the booking's own page, which removed the
  duplicate write path that had appeared between the two

### Superseded gap

- **Nothing can enter a task yet.** The data layer, the measurement, and the pricing
  integration are all in place and tested, but post-production belongs to a booking and
  there is no project page to put it on. Until that exists the measurement cannot fire, and
  the floor keeps using the assumption

## Unreleased — 0.5.0 Shoot Day

- **Talent releases** — permission from the people in the photographs, which is what makes
  a usage licence deliverable. A studio can sign a contract granting a client worldwide
  rights and still have no lawful way to hand the images over, because the people in them
  never agreed to it. The licence is the promise; these are whether it can be kept
- **Refused is a state, not an absence.** Someone who has said no is not the same as
  someone who has not been asked, and the difference decides whether a photograph may be
  used at all. A refusal is counted separately from what is still outstanding, and clears
  any signing date it previously carried
- **A release marked signed is not always a release that would stand up.** A child's is
  void without the parent or guardian named, and one with no date cannot say when
  permission was given — which is exactly the question asked when it is challenged. Both
  are reported on the row rather than left looking like permission the studio holds
- Added as pending rather than signed, because "I have their permission" is a claim about
  a piece of paper that either exists or does not
- **Schema migration 5 → 6**, purely additive: a new `talent_release` table, `6.db`
  committed, with tests covering a version-five database keeping its crew
- **Crew, each with their own call time.** That last part is the feature: hair and make-up
  are called hours before the photographer, the videographer arrives after, and a call
  sheet giving everyone the same time is a call sheet nobody can use. The list is ordered
  by call time, which is the order the morning actually happens in
- Someone with no time of their own reads "with the crew, 12:30 PM" rather than a blank,
  so nobody has to guess whether they were forgotten or are simply due with everyone else.
  A missing time sorts last, since it means *whenever* and not *before everyone*
- An unreadable call time is refused outright: a call sheet with a wrong time on it is
  worse than one with none
- Crew are held per session rather than as a studio directory, because that is how the work
  arrives — a second shooter booked for one wedding, a make-up artist the client brought. A
  directory of regulars would link to `Contact` and can arrive later without moving this
- **Schema migration 4 → 5**, purely additive: a new `crew_member` table, `5.db` committed,
  with tests covering a version-four database keeping its shot lists
- **Shot lists, grouped.** The photographs promised for a day, gathered under headings and
  ticked off as they are taken. Grouping is the whole feature: a photographer works a group
  at a time and releases people once their group is done, so each group shows what it still
  owes and says *done* when it owes nothing — which is the moment eleven relatives can be
  told they are free to go
- Groups keep the order they were written in rather than being sorted, because that order
  is a decision about who stands where. A shot remembered late lands at the end of *its own
  group*, not the bottom of the list, since filing it at the bottom is how a group gets
  called back after it was released
- Group names are free text: the groupings that matter are the ones a particular family
  has, and no list the studio ships would survive meeting one
- **Schema migration 3 → 4**, purely additive: a new `shot` table, with the `4.db` snapshot
  committed and tests covering a version-three database keeping its sessions and coordinate
- **A session detail screen**, reached by selecting a shoot day. It carries when, where,
  the notes, and the light — and it is what shot lists, crew, and call sheets will hang
  from, none of which fit in a dialog
- **The light panel**: blue hour, golden hour, sunrise, solar noon with the sun's height,
  sunset, and the evening windows, in the session's own zone, with the sun's height and
  bearing at the moment the shoot starts. The bearing is a compass point rather than a
  number, because "in the south-south-west" is what someone standing in a field can act on
  and 203° is not. Without a coordinate the panel says what it would do if given one,
  rather than rendering nothing
- Editing and moving a day moved from the list to that screen. The list navigates; the
  detail screen edits — which is also where there is room to show what an edit affects,
  since changing the date moves the golden hours with it
- **A session can carry where it happens**, and the light is worked out from it. The
  coordinate is optional and nullable: a studio portrait has no use for the sun's position,
  and demanding one to save a session would charge every booking for what only outdoor work
  needs. Half a coordinate is refused — one field alone would put the shoot on the
  Greenwich meridian
- The golden hour appears on the session as it is typed and on the row afterwards, in the
  session's own zone
- **Schema migration 2 → 3**, purely additive: two nullable columns on `session`, with the
  `3.db` snapshot committed. Tests cover a version-two database keeping its sessions, a
  coordinate surviving a round trip, and — for a studio that skipped a release — a
  version-one database being brought all the way to the current schema in one go
- **Golden hour, blue hour, sunrise, sunset, and the sun's bearing**, computed from a date
  and a coordinate with no network involved. A call sheet is written in an office and read
  in a field, and the field is where the signal is not
- Azimuth is included because it answers the question actually asked on a recce — which
  way the light will come from, and what will be backlit
- The poles are answered rather than failed on: inside the Arctic circle a day may have no
  sunrise because the sun never sets, or because it never rises, and `isPolarDay` and
  `isPolarNight` tell those two apart instead of returning a missing value for both
- `GeoCoordinates` refuses a transposed pair rather than producing a plausible-looking
  sunset from a latitude of 151°
- Checked against published almanac times for London at both solstices, Sydney in the
  southern summer, and the equator at both equinoxes — independent values, with a stated
  two-minute tolerance rather than one widened until the tests passed

### Fixed

- **A unit conversion in the equation of time**, found because those almanac checks
  failed. The correction was divided by degrees-per-radian where it must be multiplied,
  making it about three thousand times too small. It read as a sunrise a couple of minutes
  out — and only in the months where the correction is large, so the June solstice check
  passed while November was sixteen minutes wrong. There is now a test pinning solar noon
  at Greenwich in November and February, which fails if the conversion is reversed again

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

- **A session can be scheduled**, completing the chain a studio actually works down:
  client, then booking, then the days inside it. The booking comes first on the form
  because a session belonging to nothing cannot be costed, invoiced, or answered for
- **A wedding running past midnight is entered exactly as it reads.** An end time at or
  before the start is taken as the following morning rather than refused — 14:00 to 01:00
  is eleven hours, not an error, and rejecting it would make the commonest job in the
  business unenterable. The form shows the resulting hours back, so a genuine typo
  announces itself as an implausible duration
- The zone is stored with the session rather than assumed on read, which is what the model
  has asked for since 0.3.0: a destination wedding booked from home and a shoot straddling
  a daylight-saving boundary both come out wrong when local time is treated as unambiguous
- Crew call times resolve against the shoot's own day, so 12:30 for a 14:00 start is the
  morning of the shoot rather than the morning after

- **A session can be corrected, and a date can be moved — and those are not the same
  thing.** Selecting a session opens it for editing; a checkbox says the date itself moved.
  Correcting edits in place. Moving keeps the original day on the calendar as *Postponed*
  and schedules the new one beside it, which is what `SessionStatus.Postponed` has meant
  since 0.3.0: "the original block is kept for history". A client who moved a date twice in
  a fortnight is a fact about that booking, and a studio charging a reschedule fee needs
  the record of what moved
- Editing resolves times against the **session's own zone**, not the device's, so
  correcting the start of a destination wedding from home does not shift it by the offset
  between the two clocks. A moved day keeps the zone of the day it replaces
- Cancelling needs no separate action: the status is on the form, and a cancelled day stays
  on the record rather than vanishing from it

- **A client's bookings are listed on their own page**, with what each is worth and where
  it stands. A booking is the unit everything else hangs from — a contract, a set of
  invoices, and the days in the diary all point at one — so a client whose bookings were
  invisible was a client whose money and calendar could not be reached from their own page
- **A booking can be corrected**, which is chiefly how a job moves from Enquiry to Booked:
  the transition the contract and retainer rules are built around
- `bookedAt` is stamped the first time a booking reaches a status that holds studio time,
  and never cleared afterwards. **A cancelled job keeps the date it was booked on purpose**
  — that date is what a cancellation fee is measured against, and clearing it would destroy
  the evidence at the moment it is needed. A later edit does not restamp it either

### Fixed

- Selecting a session used to do nothing — `onSessionSelected` was wired to an empty lambda
  in the app shell. It opens the session now, and the app module no longer carries a
  callback for a detail screen that does not exist
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
