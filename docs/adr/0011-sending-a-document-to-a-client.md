# ADR 0011: Send a document to a client as the studio, from one address

- Status: Accepted
- Date: 2026-08-05

## Context

A studio raises an invoice and then stops. `A-PORTRAIT-SHOOT.md` ends its twentieth step
with *"Save writes the document to a file to attach to an email. The application does not
send it"* — the most repeated task in the product, finishing one step short of done.

ADR 0010 said this was next: it paid for a mail transport and noted that emailing an invoice
"was deferred with the note that mail belongs with the server". `docs/ROADMAP.md` has been
calling the remainder wiring. It is not, and this ADR exists because of the difference.

**Every message the server sends today goes to a studio.** A password reset is from Yellow
Track to its own account holder: one sender, one relationship, one reputation. Sending an
invoice inverts that. The message goes **to somebody who has never heard of Yellow Track**,
about money, on behalf of a business whose name is on the document and whose address is not
in this system's control.

Three things follow, and none of them are wiring.

**Alignment decides whether it arrives.** SES signs with DKIM for a domain verified in the
account. Putting a studio's own address in `From:` produces a message signed by
`yellowtrackstudios.com` and claiming to be from `harbourline.photography` — no alignment, and
an invoice in a spam folder is worse than an invoice not sent, because the studio believes it
was sent.

**A wrong sender reads as fraud.** A client of Harbourline Photography receiving a demand for
money from an unfamiliar address is looking at exactly what a phishing attempt looks like.

**Bounces stop being cosmetic.** "The invoice never arrived" is a money conversation, and
`MailHealth` sees SMTP-level failures only — SES accepts a message and bounces afterwards,
out of band.

## Decision

### 1. One sender address, owned by this deployment

Every message leaves from a single mailbox on the verified domain, configured as
`DOCUMENT_FROM`. For this deployment that is `clement@yellowtrackstudios.com`.

The alternative — sending as each studio's own address — fails the alignment test above and
would need every studio to add DNS records to a domain many of them do not have. A studio
using a Gmail address could never be sent as at all, and that is most of them.

So: one address, aligned, verified, and deliverable.

### 2. The studio's name goes in the display name, and its address in `Reply-To`

```
From:     Harbourline Photography <clement@yellowtrackstudios.com>
Reply-To: ada@harbourline.photography
```

The display name is what a client actually reads in an inbox list, so the message is from
the studio where it counts. Mail clients render this as *Harbourline Photography via
yellowtrackstudios.com*, which is honest — a third party did send it — and is the same
shape every mailing list and invoicing product uses.

`Reply-To` is the load-bearing half. Without it a client's reply lands in the mailbox of
whoever owns the sending address rather than the studio waiting to be paid, and the first
person to discover that is a photographer who did not get an answer about an invoice. Both
values come from `studio_profile`, which already holds `name` and `email`.

**A studio with no email in its profile cannot send.** The document screens already refuse to
issue a document with gaps in the profile; this joins that list rather than sending something
nobody can reply to.

### 3. The studio is copied on everything it sends

Every message adds the studio's own address, so the studio holds a copy of exactly what its
client received.

This is not a convenience. The application does not keep the rendered body, and a studio
asked *"what did you actually send me"* six weeks later otherwise has nothing to show.

### 4. The document goes in the body, not as an attachment

Documents already render as HTML — the format `0.6.0` chose so a file opens on a second
shooter's phone without this application. The same HTML becomes the message body.

An attachment would be the more obvious choice and is worse here: attachments from unfamiliar
senders are the most-filtered thing in mail, several of the clients being written to are
phone mail apps that render attachments badly, and it would mean a MIME multipart builder in
a codebase whose mail layer is deliberately "put a message on a socket".

A plain-text alternative part is included, from the renderer that already exists for pasting
into messages.

### 5. Sending is recorded on the document, and is not a state change

An invoice already moves between states, and *sent* is one of them — set by the studio when
it decides the figure is right. Emailing does not move it. A studio that emails an invoice
twice has emailed it twice, not sent it twice, and the number it owes is unchanged.

What is recorded is `last_emailed_at` and the address it went to, so a screen can say *"sent
to ada@example.com on 5 August"* rather than leaving the studio guessing whether it pressed
the button.

### 6. A failed send is reported, unlike a password reset

ADR 0010 decision 6 has a failed reset logged and never surfaced, because saying otherwise
would reveal which addresses have accounts. **None of that applies here.** The studio typed
the address, already knows its own client exists, and is entitled to know the message did
not go.

So a send that SMTP refuses fails loudly, in the studio's own words, on the screen it was
pressed from. The two mail paths differ deliberately and this ADR is where that is written
down.

### 7. Bounces are still not watched, and that is a bounded gamble

Nothing subscribes to the SES bounce topic. A message SES accepts and then fails to deliver
looks like success to this application, and the studio finds out when nobody pays.

Accepted for now because the volume is small and the addresses are typed by a studio that
deals with the person. It is recorded rather than hidden, and decision 8 names when it stops
being acceptable.

### 8. One reputation, and what would make that wrong

Every studio's client mail leaves from one address on one domain, so **one studio with a
stale client list can sink deliverability for all of them.** That is the case SES tenants
exist for, and the reason not to use one today is that there is one studio and a dedicated
reputation needs volume to be worth anything.

Revisit when any of these is true:

- more than a handful of studios send client mail from this deployment
- the SES console reports a bounce rate approaching 5% or complaints approaching 0.1%
- a studio asks for its own domain on the envelope, which is a real request from anybody
  large enough to care about branding

The first is a tenant per studio. The last is per-studio domain verification, which is a
product decision about who Yellow Track is to a studio's client, not a configuration change.

### 9. Sending as a studio is a spam vector, and stays closed while sign-up is open

Anybody who can create a studio can send mail from `yellowtrackstudios.com` to any address
they like, with a business name of their choosing. At four known studios that is not a risk;
with open sign-up it is an open relay with a nice interface.

**Sign-up is currently open.** Rate limiting per studio and per day is required before this
ships, and is part of the implementation rather than a follow-up — the alternative is
finding out from AWS.

## Consequences

### Positive

- The most repeated task in the product finishes where a studio expects it to
- Mail arrives, because it is signed by the domain it claims to come from
- Replies reach the studio, and the studio holds a copy of what its client saw
- The transport, its health reporting and its failure logging already exist and are proved
- The two mail paths differ where they should, and the difference is written down

### Negative

- A client sees *via yellowtrackstudios.com*, which is a smaller brand than a studio's own
  domain and is the honest description of what happened
- One reputation shared by every studio, until decision 8 fires
- A bounce is invisible, on the messages where it matters most
- `DOCUMENT_FROM` is a person's mailbox in this deployment, so replies that ignore
  `Reply-To` — and some clients do — reach the wrong human. A role address costs nothing to
  move to later and is the obvious first change

### Neutral

- A studio with no email in its profile cannot send until it fills that in, which is the
  same rule the documents themselves already have

## Alternatives considered

**Send as the studio's own address.** The obvious answer and the one that fails: no DKIM
alignment, so the invoices most worth sending are the ones most likely to be filtered.
Available later per studio, with DNS records, for studios that have a domain and want this.

**Attach a PDF.** No PDF renderer exists here, deliberately — `0.6.0` chose HTML precisely to
avoid a per-platform rendering dependency. Adding one to send mail is a large bill for a
worse-delivering message.

**Hand it to the device's mail client instead.** A `mailto:` with the body, opened on the
studio's own machine, sends as the studio with perfect alignment and needs no server at all.
Rejected because `mailto:` bodies are size-limited and cannot carry HTML, and because a
studio on a phone with no mail account configured gets nothing. Worth reconsidering as a
*second* route rather than the only one.

**Wait for tenants and per-domain verification.** The correct end state, and it needs volume
this deployment does not have. Building it now would mean asking one studio to add DNS
records to make an invoice send.

## Migration signals

- SES reports bounces above 5% or complaints above 0.1% — decision 8, immediately
- A second studio starts sending client mail — decision 8's tenant question becomes real
- A studio asks why its clients see another company's domain — per-studio verification
- Anybody replies to `DOCUMENT_FROM` about a studio's invoice — the role-address change in
  Consequences, and evidence `Reply-To` is being ignored by some client worth handling
