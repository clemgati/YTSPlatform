# ADR 0010: Reset a password with an emailed code, over SMTP

- Status: Accepted
- Date: 2026-07-31

## Context

ADR 0009 chose email and password, and left recovery out: "password reset needs a mail
transport that does not exist yet, so an account that loses its password currently cannot
recover it". That is a dead end rather than a rough edge. A studio locked out of its own
bookings has no route back at all, and unlike most gaps in this project it cannot be worked
around by doing something else.

A mail transport was also the thing 0.6.0 stopped short of. Documents leave the application
as files handed to a share sheet; emailing an invoice from inside the application was
deferred with the note that mail "belongs with the server in 0.7.0". So this pays for two
things.

Two properties of this application constrain the design.

**There is no web front end.** Every client is native — Android, iOS, desktop, wasm — and
there is no page for a reset link to land on. The server serves JSON and a health check.

**The account-existence question from ADR 0009 applies here too.** Sign-in was made
deliberately uninformative so it could not be used to ask who has an account. A reset
endpoint that answers differently for a known and an unknown address hands that back.

## Decision

### 1. The email carries a code the person types, not a link they click

A link needs somewhere to land. Building a web page to host it means a second front end, a
second deployment, and a second place the studio's session can be established — for a
product whose every client is native.

So the email carries a short code, and the application has a screen that takes the email
address, the code, and the new password. It works identically on four platforms and on a
phone with no default browser configured, and it survives the email being read on a
different device from the one being reset.

The cost is that a code is easier to mistype than a link is to click, and that it is
phishable in the way any code is — somebody talked into reading it aloud has given away
their account. Against that, a link in an email is the more familiar phishing vector, and
the code is single-use and short-lived.

### 2. Codes are single-use, short-lived, and stored as digests

Stored as a SHA-256 digest for the same reason session tokens are (ADR 0009 decision 4): a
copy of the database should not be a set of working credentials.

Consumed on first successful use, so a code read from an inbox somebody else also has does
not stay valid. Expires in one hour: long enough to find the email, short enough that an
old message in a mailbox is not a standing key.

Requesting a new code invalidates any outstanding one. Two live codes for one account is
one more than anybody needs and one more chance for the older one to be the leaked one.

### 3. Requesting a reset always answers the same way

The endpoint answers 202 whether or not the address has an account, and whether or not the
mail was actually sent. Anything else is the account-existence oracle ADR 0009 closed at
sign-in, reopened at a different door.

The consequence is that a studio that mistypes its address gets no error and no email, and
has to work out why. That is a genuine cost and it is the smaller one: the alternative
tells anybody who asks which of a town's photographers has an account here.

### 4. A completed reset revokes every session

If the password was reset because somebody else had it, leaving that person signed in on
their own device defeats the exercise entirely. Revocation is the point of the reset, not a
side effect of it.

This signs the studio out of its own other devices too, which is the correct trade and is
worth saying out loud in the interface rather than discovering.

### 5. Mail goes over SMTP, not a provider's HTTP API

SMTP works with every provider, so choosing one later is configuration rather than code,
and there is no provider to choose yet — nothing is deployed. It also runs locally: a
capture server on a laptop is a real SMTP server, so the development path exercises the
same code as production rather than a logging stub that proves nothing.

A provider API would buy deliverability reporting and webhooks. Neither matters before the
first user, and both are reachable later without changing what calls them.

### 6. A failed send is logged, never surfaced

Decision 3 means the caller cannot be told, so the failure has to go somewhere a person
will look. It is logged as an error; a reset nobody receives is otherwise perfectly silent
on both ends.

## Consequences

### Positive

- An account that loses its password can be recovered, which it currently cannot.
- The mail transport 0.6.0 wanted for sending documents now exists and is shared.
- No web front end, no second deployment, no second session-establishing surface.
- Development runs against a real SMTP server rather than a stub, so the send path is
  exercised rather than mocked.

### Negative

- A mistyped address is silent, by construction. The studio sees success and no email.
- A code is more work than a link, and is phishable by being read aloud.
- Resetting signs the studio out everywhere, including devices it is holding.
- SMTP credentials become a deployment concern, and a wrong one fails at send time rather
  than at boot.
- The server now depends on something outside itself being reachable. Sending is best
  effort and does not block the answer, so a mail outage is invisible to callers and
  visible only in the log.

## Alternatives considered

**A reset link to a hosted page.** The familiar design, and the reason it is rejected is
that it needs a web front end this product does not have. It becomes the obvious choice the
day there is one — client proofing in 0.8.0 may well bring it, and this decision should be
revisited then rather than defended.

**A signed token with no server-side row** — a JWT-shaped reset. Needs no table and no
lookup, and cannot be revoked or consumed, so a code stays valid for its whole lifetime
however many times it is used. Rejected for the same reason ADR 0009 rejected JWTs for
sessions.

**Magic-link sign-in instead of passwords entirely.** Removes reset by removing passwords.
Genuinely attractive and rejected in ADR 0009 for a reason that still holds: every sign-in
would then need a working mailbox and a connection, and this application is used at venues
that have neither.

**No reset at all; support resets by hand.** Honest for a product with one user and
unworkable at ten. There is nobody to mail.

## Migration signals

Revisit when:

- A web front end exists — client proofing is the likely cause — at which point a link is
  better than a code and this decision should be reversed rather than extended.
- Deliverability becomes a question anybody is asking, which is when a provider API's
  reporting starts to earn its keep.
- Codes are being mistyped often enough to show up as failed resets, which would argue for
  a longer code with a checksum rather than a shorter one.
