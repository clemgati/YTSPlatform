# ADR 0013: Event photo delivery, as a separate product on its own domain

- Status: Accepted, not yet built
- Date: 2026-08-08

## Context

`ROADMAP.md` has carried a milestone called *Collaboration* since it was 0.8.0, renumbered
six times as releases overtook it. It reads: client proofing, selections and approvals, the
object storage they need, and roles for second shooters.

That entry understates what is wanted, and the difference matters enough to write down
before anything is built. What is wanted is **two things**:

1. A **client-facing delivery site**, on its own domain, where a studio's booked clients log
   in to review, proof and approve.
2. **Events** — a studio photographs an event and attendees receive their photographs on
   their phones, close to as they are taken. Not one kind of event: corporate headshot days,
   conferences, weddings, parties, public festivals. The reference product is
   `SpotMyPhotos.com`.

Yellow Track today is a studio's private tool. Every screen is behind a studio sign-in, the
only outward act is emailing a document to a client (ADR 0011), and 0.6.0 explicitly ruled
out ingesting photographs at all:

> Copying files in — as opposed to checking what is already there — remains a card-reader
> job and is not planned.

Both of the things above break that. They put a stranger-facing surface on a system whose
sign-up is already open, and the second requires the ingest path that was ruled out.

### What the reference product does

Read from `spotmyphotos.com` on 8th August 2026, because guessing at a competitor is how
you build the wrong thing:

- Attendees **register by QR code** and then **submit a selfie** to be matched to their
  photographs. It is opt-in, which is what makes the biometrics defensible.
- Delivery by **SMS, email or WhatsApp**.
- About **2.5 seconds** from shutter to a phone.
- Ingest is solved partly by **selling hardware**: a mobile tethering kit and a Wi-Fi SD
  card kit, alongside tethered capture and cameras' own Wi-Fi.

That last point is the one worth pausing on. The hardest requirement here has no pure
software answer, and the incumbent charges for the answer.

## Decision

### 1. It is a separate product, on `yellowtrackphotos.com`

Not a tab in the studio application and not a route in the existing web build.

The two have different audiences, different session lengths and different failure
tolerances. A studio application may take a moment to start; a stranger holding a phone at
a conference will not wait for a multi-megabyte wasm bundle before seeing a photograph. The
studio application is also where a studio's whole business lives, and the least attractive
thing to put a public, unauthenticated surface on.

Separate deployable, separate origin. What it shares is stated in decision 2.

### 2. It shares the database and the server, not the front end

One Postgres and one API process, with the new surface as its own routes.

The alternative — a second service with its own store — means either duplicating the studio,
client and session tables or calling across a boundary for every read. Both are worse than
sharing a database that already has row level security on `studio_id` doing the tenanting.

The row level security policies are the reason this is safe rather than convenient: an event
route that forgets to name its studio returns **nothing**, which is the behaviour ADR 0009
was built for and `RowLevelSecurityTest` holds.

### 3. The first version is QR sign-up and email delivery, with no biometrics

An attendee scans a QR code, gives an email address, and receives their photographs by
email. No selfie, no face matching, no SMS, no WhatsApp.

Each of the things left out brings a whole regime rather than merely more code:

- **Face matching** brings a vendor, a per-face cost, and biometric consent law. Illinois
  BIPA carries statutory damages per violation and has produced nine-figure settlements;
  Texas and Washington have their own. A corporate headshot day in Chicago is precisely the
  scenario those statutes were written about, and a public festival is the one where nobody
  in the frame agreed to anything. Opt-in makes it defensible — it does not make it free,
  and it requires a written retention and destruction policy that would have to survive the
  thirty-day account purge.
- **SMS** brings a vendor, per-message cost, 10DLC registration in the United States, and an
  opt-out regime that must be honoured.

Email is already built, already proven, and — since the SNS work of 8th August — is the one
delivery channel whose success can actually be *observed* rather than assumed.

### 4. An event has a gallery, and stations are opened inside it

"Event" is not one thing. A corporate headshot day, a conference floor, a wedding reception
and a public festival are the same product to a studio and different problems here, because
*"which of these photographs are mine?"* has a cheap answer at a fixed station and no cheap
answer at all in a crowd.

The first draft of this decision made those two exclusive shapes and a studio chose one when
it created an event. That was wrong, and a wedding shows why: it is roaming all evening with
formal groups shot in a corner for twenty minutes. Forcing it to be one or the other means
either losing personal delivery for the family groups or pretending the dance floor is a
booth.

So an event is not a mode. **Every event has a gallery, and a station is something a
photographer opens inside it and closes again.**

**The gallery** is the default destination. Registered attendees receive what the studio
publishes to it. This is what a roaming photographer produces, and it is the whole of a
festival or a conference floor.

**A station** is a period, not an event type. While it is open, an attendee holds a **slot**;
the photographer shoots and advances; frames captured during that slot belong to that slot
and reach **only that person**. Closing the station returns everything to the gallery.

A photograph is therefore routed by asking one question: *was a slot open on the source this
arrived from?* If yes it is personal, if no it belongs to the event.

That question needs a **source** to be identifiable, which is a real requirement rather than
a detail: a station is bound to an ingest source — a watched folder, and through it one
photographer's camera — so a second photographer roaming the same wedding does not have
their candids swallowed by the first photographer's open slot. An event with two
photographers and one station is the case that breaks a naive implementation.

Slot pairing is how a photo booth already works and needs neither biometrics nor a QR card
held in frame. Its cost is honest: one deliberate act from the photographer between
subjects, and a mis-advanced slot sends one person's headshot to another. That is a
**privacy incident, not a glitch**, so:

- Advancing the slot is an explicit action, never inferred from a timer.
- Photographs are held against a slot and are **not delivered until the slot is closed**,
  which leaves a moment in which a mistake is recoverable.
- A slot's contents are visible to the photographer before they are sent.
- Nothing reaches the gallery either until the studio publishes it. An event is not an
  unreviewed feed of whatever came off a camera.

What this still does not do is give each attendee **their own** photographs from the roaming
half. That is stated rather than hidden: it is exactly what selfie matching exists to do, and
there is no cheap substitute for it. A shared gallery is the honest offer until that decision
is taken, and it is what a festival wants anyway.

This decision is the one most likely to be revisited, and decision 8 says on what signal.

### 5. Ingest is a folder the desktop application watches

The photographer's existing tethered-capture software writes to a folder. The desktop
application watches that folder and uploads what appears.

Chosen because it is the only path that adds no hardware and reuses what exists: the desktop
build already opens drives and counts files for the 3-2-1 backup verification, so reading a
directory is not new ground. It covers tethered capture, camera Wi-Fi that writes to disk,
and an SD card in a reader.

It does not cover a photographer working from a phone with no laptop. That is what the
incumbent's hardware kits are for, and it is out of scope rather than solved badly.

### 6. Photographs live in object storage, and the studio's purge reaches them

S3, written by the server, served to attendees by time-limited presigned URLs so that
neither the browser nor the attendee ever holds a durable public link.

The thirty-day deletion promise in ADR 0009 currently reaches rows in Postgres. It must
reach objects too, or the promise becomes false the moment this ships — and it would fail
quietly, which is the failure mode this codebase keeps meeting.

### 7. An attendee is not an account

An attendee gives an email address and receives a link. They do not choose a password, and
nothing in this product asks them to.

Booked clients reviewing a gallery **are** a different case and will want real logins, since
they return over weeks. That is deliberately not decided here: this ADR covers the events
half, and an attendee who is photographed once at a conference should not be made to hold a
credential for it.

### 8. What would make this wrong

Written now, while it is cheap to admit:

- **Slot pairing is mis-set often enough to matter.** One wrong delivery is a privacy
  incident. If it happens more than rarely in real use, the answer is opt-in selfie matching
  and the regime that comes with it — not more care.
- **Attendees will not type an email address on a phone at an event.** If sign-up conversion
  is poor, the mechanism is wrong however clean it is.
- **Folder-watching does not survive a real venue** — a laptop asleep, a full card, a
  photographer who does not use tethered capture. Then ingest needs the hardware answer, and
  that is a business decision as much as an engineering one.

## Consequences

### Positive

- A first version with no biometric exposure, no SMS vendor, and no hardware to sell.
- The public surface is on its own origin and its own deployable, so a mistake there cannot
  take the studio application with it.
- Object storage arrives shaped by a real consumer, which is exactly why 0.7.0 deferred it.

### Negative

- A second front end to build, style and deploy.
- Delivery is not instant. Holding photographs until a slot closes deliberately trades the
  incumbent's 2.5 seconds for a window in which a misdirected headshot can be caught.
- No parity with SpotMyPhotos, and this should not be described as though there were. The
  gap is specific: at a **roaming** event the incumbent gives each attendee their own
  photographs and this gives them the event's gallery, because the difference between those
  two is exactly the selfie matching left out of decision 3.

### Neutral

- `studio_member.role` has existed since the first migration and stays unused. Roles for
  second shooters are a change to the tenancy model and belong in their own decision.

## Alternatives considered

**A route in the existing web application.** One codebase and one design system, but a
multi-megabyte wasm bundle before an attendee sees a photograph, and a public path through
the sign-in shell of the application holding every studio's business.

**Capability links instead of any registration.** Simplest of all, and wrong here: the point
of an event is that the studio does not know who will attend, so somebody has to say who
they are before anything can be sent to them.

**Selfie matching from the start,** as the incumbent does. Rejected for the first version
only, on regulatory exposure rather than difficulty. Decision 8 names the signal that would
change it.

**One pairing mechanism for every event.** Rejected once it was clear that "event" covers
both a photographer at a fixed station and a photographer moving through a crowd. Slot
pairing is right for the first and impossible for the second; a shared gallery is right for
the second and insulting for the first, where the whole point is that a person receives
their own headshot.

**Two exclusive event modes, chosen at creation.** This was the first draft of decision 4 and
survived about an hour. A wedding is roaming all evening with formal groups shot in a corner,
so choosing once means either losing personal delivery for the family groups or pretending
the dance floor is a booth. A station opened *inside* an event costs nothing extra and covers
both.

## Migration signals

Revisit when any of the following is true:

- Slot mis-pairing produces a real misdirected delivery.
- A studio wants attendees at a **roaming** event to receive only their own photographs.
  That is selfie matching, and there is no cheaper answer — this is the signal that buys the
  regime in decision 3 rather than an argument for more pairing cleverness.
- Fewer than half of attendees complete sign-up at an event.
- A studio asks for delivery to a phone number rather than an inbox, more than once.
- Ingest fails at a venue for a reason folder-watching cannot fix.
