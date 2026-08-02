# ADR 0009: Accounts, authentication, and tenant isolation in the database

- Status: Accepted
- Date: 2026-07-30

## Context

`studio_id` has been on every row since the first migration. ADR 0006 put it there and said
why: the schema should be multi-tenant before it needs to be, because retrofitting a tenant
column onto a live database is the kind of migration that goes wrong quietly.

It has never referred to anything. `LocalStudioContext` hands out a constant —
`00000000-0000-7000-8000-000000000001` — and every one of the twenty-five tables is scoped
by a tenant that does not exist as a row anywhere. That was correct while the database sat
on one photographer's laptop, where the only studio is the one holding the laptop.

Synchronisation is what makes the tenant real. The moment ADR 0008's server holds two
studios' rows in one Postgres, "which studio" stops being a formality and becomes the only
thing standing between one photographer's client list and another's. That is not a bug
class; it is a disclosure of client contact details, signed contracts, and financial
records belonging to people who never agreed to be in this database at all.

Four properties of this application shape the decision.

**The application is offline-first.** `docs/VISION.md` requires shoot-day tools to work
without a connection. A device may be offline for a working day and reconcile that evening,
so a session that expires while the photographer is in a barn with no signal is a session
that expires at the worst possible moment.

**A studio is usually one person, but not always, and not forever.** ADR 0008 already noted
the owner-on-a-laptop and second-shooter-on-a-phone case. 0.8.0 puts second shooters and
editors on the roadmap explicitly, with roles.

**There is no infrastructure yet, and no operator.** Nobody is on call. Whatever is chosen
has to be safe when it is running unattended against a database nobody is watching.

**Isolation bugs are invisible from the inside.** A query that forgets its `WHERE studio_id`
returns *more* rows, not fewer. It does not throw, it does not look wrong in a list, and
the studio that sees another's booking has no way to report it as anything but confusion.
This is the same property that made synchronisation dangerous in ADR 0008, and it deserves
the same answer: put the safety somewhere that cannot be forgotten.

## Decision

### 1. The studio is the tenant, the account is a person, and membership joins them

Three tables rather than one. `studio` is the tenant row that `studio_id` has been pointing
at since the first migration. `account` is a person who can sign in, identified by email.
`studio_member` joins them and carries a role.

A single `account.studio_id` column would be smaller today and is what one person on one
laptop actually needs. It is rejected because 0.8.0 adds second shooters and editors, and
the difference between the two shapes is the difference between an additive change and a
data migration over every account that exists by then. The join table also makes the
one-person case cost nothing at request time: which studio a request acts as comes from the
session token, not from a lookup.

`role` exists now and is `Owner` for everybody. A column with one value is not yet doing
work, but it is the place the work goes, and naming it now stops 0.8.0 from having to
decide where roles live while it is also deciding what they mean.

### 2. Email and password now, shaped so federated identity can be added later

`account` carries a nullable `password_hash`, and nothing about the table assumes a password
is how the person authenticates. A `federated_identity` table — provider, subject, account —
can be added without reshaping anything, and an account with a null hash and a federated
identity is a valid account rather than a broken one.

Sign in with Apple and Google are worth having and are not worth having *first*: the
redirect flow has to be solved separately on Android, iOS, desktop, and wasm, and offering
any social login on iOS obliges Sign in with Apple as well. That is four platform problems
and a store requirement, none of which teach anything about whether the tenant boundary
holds.

### 3. Passwords are hashed with Argon2id, in pure Java

Argon2id is the current recommendation, and the parameters are stored in the hash string so
they can be raised later without invalidating existing passwords.

The implementation is BouncyCastle's, which is pure Java. The usual Argon2 binding for the
JVM is JNI over a native library, which would mean a native artefact per platform in CI and
a deployment that can fail for reasons unrelated to the code. Password hashing is not where
this project should be spending its first native dependency.

### 4. Sessions are opaque random tokens, stored as digests, and revocable

A token is 256 bits from a secure random source. What is stored is its SHA-256 digest, not
the token, so a copy of the database is not a set of live sessions — the same reasoning that
makes storing a password hash rather than a password obvious.

**Not JWTs.** A JWT is attractive here because it needs no lookup, and that is exactly its
problem: a stolen token stays valid until it expires, and the only remedies are short
lifetimes or a revocation list that reintroduces the lookup the JWT was chosen to avoid.
Short lifetimes are precisely what an offline-first application cannot have. A device that
has been in a field all day should reconcile that evening without a sign-in prompt, so
sessions here are long-lived — and a long-lived token that cannot be revoked is a key that
cannot be taken back from a stolen phone.

The lookup a JWT saves is one indexed read on a primary key, on the same connection the
request already needs for its data.

### 5. Tenant isolation is enforced by Postgres, not by the application

Every business table gets `ENABLE ROW LEVEL SECURITY` and a policy comparing `studio_id`
against `current_setting('app.studio_id', true)`. Each request opens a transaction, issues
`SET LOCAL app.studio_id`, and runs its queries inside it.

This is deliberately belt-and-braces. The application will also scope its queries, and that
scoping will be correct nearly all of the time. "Nearly all" is the problem: there is no
review process that reliably catches one missing `WHERE` clause in a codebase that will
eventually have hundreds of queries, and the failure returns extra rows rather than an
error.

The setting is read with the missing-ok form, which yields `NULL` when nothing has been set.
`studio_id = NULL` is `NULL`, not `true`, so a query that never set the studio matches
nothing. **A forgotten `SET LOCAL` returns zero rows rather than every row.** Fail-closed is
the entire point of putting this in the database; a policy that failed open would be
decoration.

### 6. The application connects as a role that cannot bypass its own policies

Postgres exempts superusers, roles with `BYPASSRLS`, and — by default — the table's own
owner from row level security. Migrations run as the owner, so if the application used the
same role, every policy in decision 5 would be silently inert.

So there are two roles. Migrations run as the owner. The application connects as
`yellowtrack_app`, which owns nothing, has no `BYPASSRLS`, and holds only `SELECT`,
`INSERT`, `UPDATE`, `DELETE`. Every table additionally gets `FORCE ROW LEVEL SECURITY`, so
the policies apply to the owner too and a future change that runs application queries as the
owner does not quietly open everything.

This is the decision most likely to be undone by accident, because everything appears to
work when it is wrong. It has a test that asserts the application role cannot read another
studio's rows, and a second that asserts it cannot read them with the studio unset.

### 7. The authentication tables are not under studio policies

`account`, `auth_session`, `studio` and `studio_member` are reachable by the application
role without a studio being set, because they are what *establishes* the studio. A policy
keyed on `app.studio_id` cannot guard the lookup that decides what `app.studio_id` should
be.

Hiding the password hash from the application by policy would also hide it from the only
code with a reason to read it, which is the code that checks it. The hash is Argon2id, so
reading the table is not reading the passwords.

The consequence is honest and worth stating plainly: **the tenant boundary is enforced by
the database for business data, and by the application for authentication.** The
authentication path is small, has no user-supplied query fragments, and is the part of this
system most worth reviewing line by line.

## Consequences

### Positive

- A missing `WHERE studio_id` returns nothing instead of another studio's data. The
  expensive mistake becomes a visible one.
- Sessions can be revoked, so a lost phone is a solved problem rather than a wait.
- Long-lived sessions suit a device that is offline for a working day, without the usual
  trade against revocability.
- Argon2 parameters can be raised later without invalidating anybody's password.
- `studio_id` finally references a row, so the orphan tenant that has been implicit since
  the first migration becomes a foreign key.
- 0.8.0's roles are an additive change rather than a reshaping.

### Negative

- Every request now needs a transaction with a `SET LOCAL` before it touches business data.
  A handler that forgets returns nothing, which is a visible failure — but it is still a
  failure mode that did not exist before.
- Two database roles means deployment has to provision both, and a misconfiguration where
  the application connects as the owner disables the protection while appearing to work.
  Tested, not trusted.
- Row level security costs a predicate on every query against every business table. On a
  photographer-sized dataset this is not measurable, but it is real and it grows.
- The authentication tables sit outside the mechanism that protects everything else, which
  is a deliberate hole in an otherwise uniform story.
- Password reset needs a mail transport that does not exist yet, so an account that loses
  its password currently cannot recover it. That gap is real and is called out on the
  roadmap rather than papered over.
- Sign-up creates a studio implicitly. Joining an existing studio needs invitations, which
  arrive with roles in 0.8.0.

## Alternatives considered

**Application-scoped queries only, without row level security.** What almost every
application does, and it works right up until one query is written wrong. The failure is
silent, returns extra rows, and in this domain discloses another business's clients and
contracts. Rejected for the same reason ADR 0008 rejected silent last-write-wins: the
cheapness is real and the failure is unobservable from the inside.

**JWTs.** Addressed in decision 4. Rejected on revocation, which offline-first makes worse
rather than better.

**A schema or database per tenant.** The strongest isolation available — a query cannot
cross a boundary it cannot address. Rejected as disproportionate for a business of
one-person studios: migrations would have to run per tenant, connection pooling fragments,
and cross-tenant analytics become impossible. Worth revisiting only if a studio ever needs
its data physically separated for a contractual reason.

**bcrypt.** Fine, widely deployed, and would have avoided a new dependency. Argon2id is
preferred by current guidance and is memory-hard, which is the property that matters against
the hardware an attacker actually rents. The dependency is pure Java, so the cost is a jar.

**A managed authentication provider — Auth0, Clerk, Supabase Auth.** Would remove
password storage, reset flows, and most of this ADR. Rejected for now on the same grounds
ADR 0007 rejected a managed backend: it moves the identity of every studio into a third
party before there is a single user, and the parts it removes are the parts already
understood. It stays cheap to reverse, because decision 2 keeps the account free of the
assumption that a password is how anyone signs in.

## Migration signals

Revisit this decision when:

- Studios need to invite people rather than each sign-up creating a studio — at which point
  membership stops being implicit and decision 1's join table starts earning its keep.
- Anyone asks for social sign-in, which decision 2 is shaped to allow without a migration.
- The authentication path grows past what is reviewable by reading it, at which point the
  hole in decision 7 needs closing with a separate role or a `SECURITY DEFINER` door.
- Row level security shows up in query plans as a cost rather than a predicate.
