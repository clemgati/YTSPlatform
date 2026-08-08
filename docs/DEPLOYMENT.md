# Deployment

One EC2 instance running Apache, Postgres and the server, with Amazon SES for mail.

Written for that shape specifically rather than generically, because the decisions that
matter here — which database role the server connects as, how memory is divided, where
backups go — are all ones a generic guide would leave to the reader and all ones that fail
quietly when got wrong.

---

## The three things that fail silently

Read these before anything else. Each one leaves a server that starts, answers, and looks
correct.

### 1. The server must not connect to Postgres as a superuser

Every business table is protected by a row level security policy comparing `studio_id`
against the studio the request is acting as. **Superusers are exempt from every one of
them.** Connect as `postgres`, or as whatever role created the database, and all studios
can read each other's clients, invoices and contracts. Nothing throws. Nothing looks wrong.

`ADR 0009` created `yellowtrack_app` for this and deliberately left it without `LOGIN`, so
that granting it is a decision somebody makes here rather than a default nobody notices:

```sql
ALTER ROLE yellowtrack_app LOGIN PASSWORD 'something long from a password manager';
```

Then `DATABASE_USER=yellowtrack_app`. Verify it took, on the running instance:

```sql
-- Must be f | f. Either being t means the policies are inert.
SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'yellowtrack_app';
```

Migrations run as the owner, which is a **separate** variable — Flyway needs to create
tables and `yellowtrack_app` deliberately cannot:

```
MIGRATION_USER=yellowtrack_owner
MIGRATION_PASSWORD=...
```

Leave these unset and both jobs use `DATABASE_USER`, which fails twice over: on a fresh
database `yellowtrack_app` does not exist yet, because migration V2 is what creates it, and
on an existing one the next migration stops at `permission denied for schema public`. That
is why there are two roles here and why only one of them serves requests.

**The first boot is the exception.** Nothing has run yet, so `yellowtrack_app` does not
exist and there is no password to put in `DATABASE_PASSWORD`. Start once with the owner in
*both* pairs, let the migrations create the role, then run the `ALTER ROLE` above, set
`DATABASE_USER`/`DATABASE_PASSWORD` to `yellowtrack_app`, and restart. `verify-deployment.sh`
fails until you have — that check is there because a server left on the owner credentials
works perfectly and enforces nothing.

**Expect `/ready` to answer 503 in between.** A server on the owner's credentials starts,
migrates, and answers `/health`, then fails every query: each transaction issues
`SET LOCAL ROLE yellowtrack_app`, and `SET ROLE` needs *membership* in the target role,
which the owner has no reason to hold. `/ready` names it — `permission denied to set role`
— rather than leaving you with a bare `"database": false` that reads like a network fault.
It clears the moment `DATABASE_USER` becomes `yellowtrack_app`, for which setting its own
role is trivially permitted. Do not fix it with `GRANT yellowtrack_app TO yellowtrack_owner`;
that makes the wrong state work.

### 2. A password reset never says it failed

The reset endpoint answers `202 If that address has an account, a code is on its way`
**whether or not the send succeeded** — deliberately, because saying otherwise would reveal
which addresses have accounts (`ADR 0010` decision 3). Every way mail can fail is therefore
invisible from the outside. The failure goes to the log and nowhere else.

A new deployment starts in the SES sandbox, where mail to any address you have not verified
is rejected: resets appear to work and silently never arrive. Request production access
before anybody needs to reset a password, and until it is granted, treat reset as not
working.

**Production access does not close this hole, it moves it.** Once SES accepts mail to any
address, a send stops failing loudly at the API and starts failing quietly afterwards — a
bounce, a throttle, an expired SMTP credential, or sending suspended over a bounce rate you
were not watching. The reassuring `202` is unchanged throughout.

### 3. Nothing is backed up unless you back it up

Postgres on the same instance as the application means one lost instance is one lost
business. `docs/VISION.md` asks for zero avoidable data loss and this is the most avoidable
kind there is. A `pg_dump` to S3 on a timer, and **a restore you have actually performed
once** — an untested backup is a belief, not a backup.

### 4. A web deploy the browser never sees

The files upload, Apache is healthy, every check passes, and the studio keeps running last
month's application. This one leaves no trace anywhere on the instance, because nothing
went wrong on the instance.

**Only the two large `.wasm` files carry a content hash.** `yellow-track-web.js` — which is
the whole application — plus the numbered webpack chunks and `sql-wasm.wasm` all keep the
same name from one release to the next. So a rule matching `*.js` and saying `immutable`
pins the application in every browser that has ever opened the site, for a year.

`index.html` revalidates correctly and points at the same unchanged name, so it does not
help. Nor does the new `.wasm` hash: it is referenced from *inside* the JavaScript that was
never re-fetched, so the browser does not know it exists. The `<Directory>` block below
revalidates by name and hard-caches only what is hashed, and `deploy-web.sh` now fails if the
served headers disagree with it.

**Fixing the vhost does not release a browser already holding one.** `immutable` means what
it says: it will not revalidate, whatever the server sends next, until the year is up.
Anybody who loaded the site under the old rule needs one hard reload — <kbd>Cmd</kbd> or
<kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>R</kbd> — and never again after that.

---

## Choosing the instance

**Ubuntu 24.04 LTS, arm64, `t4g.small`, gp3 storage.**

**arm64 is safe because nothing in this stack is native.** Ktor, Flyway, Hikari, the
Postgres JDBC driver, Angus Mail and Logback are all pure JVM. The one place a native
dependency nearly appeared was password hashing, and `ADR 0009` decision 3 chose
BouncyCastle's Argon2 over the usual JNI binding to avoid a per-platform artefact in CI.
That decision pays off a second time here: Graviton needs no thought, at roughly 20% less
than the x86 equivalent.

**Ubuntu over Amazon Linux 2023, for one reason: Postgres versions.** The PGDG repository
carries every major version, so production can run the same one as development. Nothing in
the schema needs a recent Postgres — the newest things it uses are `FORCE ROW LEVEL
SECURITY` and the missing-ok form of `current_setting`, both long-standing — so AL2023's
packaged version would work. But `SchemaDriftTest` exists to hold two schemas identical,
and running different majors either side adds a variable to exactly the thing being held
still. Take AL2023 instead if SSM and cloud-init working out of the box matter more; it is
a trade, not a mistake.

**`t4g.small` rather than `micro`, because of Argon2.** Hashing is configured at 64 MiB per
password — that memory is the point, it is what makes the hash expensive to attack in
parallel. But every concurrent sign-in transiently claims 64 MiB on top of a 512 MB JVM
heap and 256 MB of shared buffers. On a 1 GB `t4g.micro` that is tight, and the failure is
Postgres being OOM-killed during a burst of sign-ins. If 1 GB is ever required, the honest
lever is lowering the Argon2 memory parameter — a security decision rather than a sizing
one, and the PHC-format hash means it can be raised later without invalidating anybody's
password.

### In the launch wizard

**File systems: None.** EFS is NFS, and a Postgres data directory on NFS is a known route
to corruption and poor latency; "S3 Files" is object storage presented as a mount, with no
atomic renames; FSx is for other workloads entirely. Backups reach S3 through `aws s3 cp`
from `pg_dump`, not through a mount.

**Storage: two volumes.**

| | Size | Delete on termination |
| --- | --- | --- |
| Root | 16 GB gp3 | Yes — it is only the OS and the application |
| Data | 20 GB gp3 | **No** |

The data volume's flag is the one worth stopping over. The root volume defaults to deleting
on termination, which is right for it; if the data volume inherits that, terminating the
instance destroys the business. Encrypt both — it is free. A separate volume also means the
database can be snapshotted independently and survives rebuilding the instance, which is
the cheapest thing that makes "one lost instance is one lost business" less true.

20 GB is generous: this database holds text rows. No image ever lands in it.

**Security group:**

| Port | From | Why |
| --- | --- | --- |
| 443 | Anywhere | The API |
| 80 | Anywhere | certbot's HTTP-01 challenge, not just redirects |
| 22 | Your own address only | And ideally closed once SSM works |

**Never open 5432.** Postgres listens on localhost and Apache is the only thing that
reaches the server. An exposed database port on a box holding several studios' financial
records is the superuser trap above, but reachable from the internet.

**Key pair: create one, ED25519, `.pem`.** The private half downloads exactly once — AWS
keeps only the public key — so it belongs in a password manager rather than `~/Downloads`.
`chmod 400` it or `ssh` refuses it. The login user is `ubuntu` on Ubuntu and `ec2-user` on
Amazon Linux.

Do not choose "proceed without a key pair" even if you intend to use SSM Session Manager,
which is better and lets port 22 stay shut: the key is what gets you in when the SSM agent
is not running, and that is precisely when you need a way in.

**IAM instance profile:** attach a role with `AmazonSSMManagedInstanceCore` and write access
to the backup bucket. It can be changed on a running instance, so this is tidiness rather
than a one-shot decision.

---

## Preparing the instance

```sh
# Postgres 18 from PGDG, to match development.
sudo apt update && sudo apt install -y curl ca-certificates
sudo install -d /usr/share/postgresql-common/pgdg
sudo curl -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \
  https://www.postgresql.org/media/keys/ACCC4CF8.asc
echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] \
  https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" \
  | sudo tee /etc/apt/sources.list.d/pgdg.list

sudo apt update
sudo apt install -y postgresql-18 apache2 openjdk-21-jre-headless
sudo a2enmod proxy proxy_http ssl headers
```

### The data volume

```sh
lsblk                                    # find it; likely /dev/nvme1n1
sudo mkfs.ext4 /dev/nvme1n1              # only if it is new and empty
sudo systemctl stop postgresql
sudo mkdir -p /mnt/pgdata
sudo mount /dev/nvme1n1 /mnt/pgdata

# Persist across reboots by UUID — device names can move between boots.
echo "UUID=$(sudo blkid -s UUID -o value /dev/nvme1n1) /mnt/pgdata ext4 defaults,nofail 0 2" \
  | sudo tee -a /etc/fstab

sudo rsync -av /var/lib/postgresql/18/main/ /mnt/pgdata/
sudo chown -R postgres:postgres /mnt/pgdata
# Point data_directory at /mnt/pgdata in postgresql.conf, then start again.
sudo systemctl start postgresql
```

`nofail` in the fstab entry is deliberate: without it, an instance whose data volume fails
to attach will not finish booting, and you lose the ability to log in and fix it.

### The database and its two roles

Three objects, created once, as `postgres`. Order matters and none of it is done by the
application.

```sh
# Owns the schema and runs migrations. Never serves a request.
sudo -u postgres createuser yellowtrack_owner --pwprompt
sudo -u postgres createdb yellowtrack -O yellowtrack_owner

# Serves every request, and owns nothing. Created here rather than by the migration:
# CREATE ROLE is cluster-wide, and yellowtrack_owner deliberately has no CREATEROLE.
sudo -u postgres psql -c "CREATE ROLE yellowtrack_app NOLOGIN"
```

V2 creates `yellowtrack_app` only `IF NOT EXISTS`, so doing it here makes that block a
no-op rather than a conflict. Skip this step and the first deploy dies on `permission
denied to create role`, having rolled the migration back.

The alternative is `ALTER ROLE yellowtrack_owner CREATEROLE`, which makes the deploy work
with one fewer command. It is not the trade to make: that is a standing privilege on an
account whose password lives in `/etc/yellowtrack/env`, and the point of `ADR 0009` is that
what is on the instance cannot reshape the cluster. Creating a role once is an act;
granting the power to create roles is a capability.

`yellowtrack_app` gets its `LOGIN` and password later — see *The three things that fail
silently*, above — because the first boot has to happen as the owner.

---

## The instance

Postgres and the server share one box. That is right at this scale and stops being right
when either you want to redeploy the application without taking the database down, or
contention shows up in practice. Both are observable; neither is worth paying for early.

Putting Postgres on a *second self-managed EC2* is worth less than it looks: it buys
resource isolation and not durability, failover, or point-in-time recovery. If what worries
you is losing the instance, the answer is RDS or disciplined backups, not a second box.

### Divide the memory explicitly

The JVM will take what it is allowed and Postgres will be the thing the kernel kills. On a
small instance, set both:

```sh
# In the systemd unit
Environment=JAVA_OPTS=-Xmx512m

# postgresql.conf, on a 2 GB instance
shared_buffers = 256MB
work_mem = 8MB
max_connections = 20        # the pool asks for 10; leave room for psql and pg_dump
```

`max_connections` is worth a moment: the application pool is sized at 10, so a limit of 20
leaves headroom for a maintenance session and a dump running at the same time. Set it below
the pool size and the application fails under exactly the load that made you look.

---

## Deploying

Once the instance is prepared, deploys are one command:

```sh
./scripts/deploy-server.sh yellowtrack
```

The argument is an ssh host. A `~/.ssh/config` alias keeps the address, user and key path
out of this repository:

```
Host yellowtrack
    HostName api.yourdomain
    User ubuntu
    IdentityFile ~/.ssh/yellowtrack.pem
    ServerAliveInterval 60
```

The script builds, uploads with `--delete` so a removed library does not linger on the
classpath, restarts the service, waits for `/health`, and prints `/ready` — which is where
an unconfigured mail host shows up, every deploy, rather than when somebody thinks to look.

It does not run the tests: those need a Postgres and this does not. Deploying an untested
build should be a decision somebody makes, not one a script makes quietly.

## Building by hand

The server is a **JVM application**, not Node — nothing in this stack uses Node except the
web build's toolchain, which runs on your machine and not on the instance. Packages are
installed in *Preparing the instance* above.

Build a distribution locally and copy it up:

```sh
./gradlew :server:installDist
# server/build/install/server/ contains bin/server and lib/
```

---

## Environment

The server reads all of this from the environment. There are no defaults that are correct
here — every one of them is a laptop default.

| Variable | Example | Notes |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/yellowtrack` | |
| `DATABASE_USER` | `yellowtrack_app` | **Never a superuser.** See above |
| `DATABASE_PASSWORD` | | From the `ALTER ROLE` above |
| `MIGRATION_USER` | `yellowtrack_owner` | Owns the tables. Falls back to `DATABASE_USER`, which fails |
| `MIGRATION_PASSWORD` | | Used only at startup, never to serve a request |
| `MAIL_HOST` | `email-smtp.eu-west-1.amazonaws.com` | Your SES region |
| `MAIL_PORT` | `587` | |
| `MAIL_USERNAME` | | SES **SMTP credentials**, not an IAM access key |
| `MAIL_PASSWORD` | | Generated in the SES console alongside the username |
| `MAIL_TLS` | `true` | Defaults to *false*, which is right only for a local capture server |
| `MAIL_FROM` | `no-reply@yourdomain` | Must be on a domain verified in SES |
| `ALLOWED_ORIGINS` | `https://app.yourdomain` | Only needed for the browser build |
| `ALERT_EMAIL` | `you@yourdomain` | Where `watch-deployment.sh` reports a change of state |
| `DELETION_RETENTION_DAYS` | `30` | How long a deleted studio can still be put back. Defaults to 30 |
| `DOCUMENT_FROM` | `clement@yourdomain` | Sends a studio's documents to its clients. Must be on the verified domain — see `ADR 0011` |
| `SES_TOPIC_ARN` | `arn:aws:sns:eu-west-1:123456789012:yellowtrack-ses` | The SNS topic SES publishes bounces to. **Unset means `/ses/notifications` refuses everything** |
| `PORT` | `8080` | Bound to loopback; Apache is the only thing that reaches it |

`MAIL_USERNAME` catches people out: SES SMTP credentials are generated separately in the
console and are not your IAM access key, which fails authentication in a way that reads
like a wrong password.

Verify the domain with **DKIM**, not just the address. Mail from an unauthenticated domain
lands in spam, which is indistinguishable from not sending it.

---

## systemd

```ini
[Unit]
Description=Yellow Track server
After=network.target postgresql.service
Wants=postgresql.service

[Service]
Type=simple
User=yellowtrack
WorkingDirectory=/opt/yellowtrack
# systemd has no trailing comments: anything after the value, including a #, is part of
# the value. This file is chmod 600 and owned by root.
EnvironmentFile=/etc/yellowtrack/env
Environment=JAVA_OPTS=-Xmx512m
ExecStart=/opt/yellowtrack/bin/server
Restart=on-failure
RestartSec=5

# It needs its own directory, a port on loopback and a socket to Postgres, and nothing
# else. Cheap to add now; awkward to add after an incident.
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/yellowtrack

[Install]
WantedBy=multi-user.target
```

The environment file holds the database and SES passwords, so it is `chmod 600` and not in
the repository — which is the same reason `MailConfig` and `DatabaseConfig` read from the
environment rather than a checked-in file.

---

## SES

Do this in an order that respects the one part you cannot hurry: production access is a
review, and it can take a day.

1. **Region.** The SES console's region selector, set to the instance's region. Identities
   are per region, and verifying in the wrong one is starting again.
2. **The domain**, with Easy DKIM. Three CNAMEs to add wherever the zone lives. Leave the
   custom MAIL FROM alone: it wants an MX record, and a domain already serving something
   else is not where to add DNS you do not need.
3. **Your own address**, as a second identity. In the sandbox, this is the only address you
   can send to, so it is what makes the reset loop testable today.
4. **Production access**, requested as early as possible. Transactional password resets to
   account holders who asked for them, low volume, bounces not retried.
5. **SMTP credentials**, from *SMTP settings* — which creates an IAM user and shows the
   password once. These are not AWS access keys. They resemble them closely enough that
   using the wrong pair is common, and the failure is an authentication error that does not
   say which kind of credential it wanted.

```
MAIL_HOST=email-smtp.eu-west-1.amazonaws.com
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...
MAIL_TLS=true
MAIL_FROM=no-reply@yourdomain
```

`MAIL_TLS` defaults to *false* — correct for a local capture server, wrong everywhere else.
SES rejects the connection anyway, so the mistake is loud rather than silent, which is not
true of the next one.

No tenant, and the Essentials plan. SES tenants isolate reputation between customers you
send *on behalf of*; every message here goes from Yellow Track to one of its own account
holders, so there is one sender and one reputation to protect. Pro's dedicated IP would be
worse than the shared pool, not better — a dedicated address needs steady volume to warm,
and password resets will never supply it.

Both change together, and only if Yellow Track ever sends as a studio rather than to one —
an invoice to a studio's client, a session reminder in the studio's name. Then one studio's
stale address list can sink deliverability for all of them, which is the case tenants exist
for. It would want an ADR: it changes who the sender is.

### Mail fails silently, whatever stage you are at

`ADR 0010` has the reset endpoint answer `202 If that address has an account, a code is on
its way` regardless of what happened, because answering differently would tell an attacker
which addresses have accounts. A send that SES refuses is therefore invisible from the
outside: the studio sees the same reassuring sentence and waits for mail that will never
arrive.

Only two things surface it, and both need looking at deliberately:

```sh
curl -s localhost:8080/ready | grep '"mail"'         # false means nothing is even attempted
sudo journalctl -u yellowtrack | grep -i mail        # a refused send is logged, not raised
```

Note what the first one actually proves. `mail:true` means `MAIL_HOST` is set — it is read
from the environment at boot and never from a send. It reads `true` with a wrong password,
an expired credential, and an SES account whose sending has been suspended. It is a check
that mail was *configured*, and there is no check that mail *works*.

This is why `verify-deployment.sh` fails rather than warns on `mail:false`, and why the
first thing to do after production access is granted is to reset a password end to end,
**to an address that was never an SES identity**, and watch it arrive. Resetting to a
verified address proves only what already worked in the sandbox.

### After production access, bounces are yours to watch

In the sandbox a bad address is refused at the API, in the request, where it is logged. In
production SES accepts it and bounces asynchronously — and holds the account to a bounce
rate under 5% and a complaint rate under 0.1%, above which sending goes under review and
then away. Without a subscription the first sign would be resets no longer arriving for
anyone.

The server now listens. `POST /ses/notifications` takes SNS notifications and records them,
and `/ready` reports what they said:

```json
{"mailLastSucceededAt": 1786203028314, "mailLastDeliveredAt": 1786203031002,
 "mailRecentBounces": 0, "mailRecentComplaints": 0}
```

Those first two fields are different claims and the gap between them is the entire point.
`mailLastSucceededAt` means SMTP accepted the message — the credentials work.
`mailLastDeliveredAt` means SES confirmed it *arrived*. Before this, only the first could be
observed, and it was routinely read as the second: a message to a mistyped domain is
accepted happily and bounces a second later, and every check available said mail was fine.

Bounces are recorded and surfaced, **not acted on**. Nothing suppresses sending to an
address that has bounced. That is deliberate: the volume here is small, and a rule that
silently stops sending password resets to somebody would be a worse failure than the one it
prevents. If the counts start climbing, that is a decision for a person.

> **`/ready` is reachable from outside with the vhost in this document.** The Apache config
> below proxies `/` wholesale, so `https://api.yourdomain/ready` answers to anyone — and it
> carries the raw Postgres error text, which names roles and hosts, alongside these counts.
> Nothing here is a credential and the code comments assumed the instance was the only
> caller. If that assumption is worth keeping, restrict it:
>
> ```apache
> <Location /ready>
>     Require local
> </Location>
> ```
>
> `/health` should stay open — it answers `{"status":"ok"}` and nothing else, and a proxy or
> an uptime check needs it.

#### Wiring it up

1. **A topic.** SNS → *Create topic* → Standard, in the instance's region.
   ```sh
   aws sns create-topic --name yellowtrack-ses --region eu-west-1
   ```
2. **Tell SES to publish to it.** In the SES console, the verified *domain identity* →
   *Notifications* → edit *Feedback notifications*, and set Bounce, Complaint and Delivery to
   the topic. Delivery is the one that is easy to skip and is what makes
   `mailLastDeliveredAt` mean anything.
3. **Set `SES_TOPIC_ARN`** in `/etc/yellowtrack/env` to the topic's ARN, and restart. Until
   this is set the endpoint refuses everything and says so in the log — see below for why.
4. **Subscribe the endpoint.**
   ```sh
   aws sns subscribe --topic-arn arn:aws:sns:eu-west-1:123456789012:yellowtrack-ses \
     --protocol https --notification-endpoint https://api.yourdomain/ses/notifications \
     --region eu-west-1
   ```
   SNS immediately posts a `SubscriptionConfirmation`, and the server confirms it by fetching
   the URL inside. Check it took:
   ```sh
   aws sns list-subscriptions-by-topic \
     --topic-arn arn:aws:sns:eu-west-1:123456789012:yellowtrack-ses --region eu-west-1
   ```
   A `SubscriptionArn` of `PendingConfirmation` means the confirmation never arrived or never
   succeeded — the server logs both cases.

#### Why this endpoint is unauthenticated, and why that is not a hole

An SNS HTTPS subscription is a POST from Amazon's network carrying no token of yours. There
is nothing to authenticate *with*, so the message is authenticated instead: every one is
signed, and the server verifies the signature against a certificate fetched from Amazon.

Two details in that sentence are load-bearing, and both are easy to leave out:

- **The certificate URL is checked before it is fetched.** The message names the certificate
  that validates it, so an unconstrained fetch lets the sender supply both halves of the
  proof — and makes a request from inside your instance to a URL a stranger chose.
- **The topic ARN is checked as well as the signature.** A valid Amazon signature proves an
  AWS customer sent it, *not that the customer was you*. Anyone can create a topic and
  publish perfectly-signed notifications describing bounces that never happened. This is why
  an unset `SES_TOPIC_ARN` refuses everything rather than accepting anything Amazon-shaped.

#### What it still cannot see

A message SES accepted and delivered can still be filtered into a spam folder afterwards.
Delivery means it reached the receiving server, not that anybody read it. That gap is not
closable from here, and no field pretends otherwise.

---

## Backups

Two scripts, in `scripts/`. They are not deployed by `deploy-server.sh` — that syncs the
built application and nothing else — so put them somewhere they will survive a deploy:

```sh
scp scripts/backup-database.sh scripts/restore-database.sh yellowtrack:/tmp/
ssh yellowtrack 'sudo install -m 755 /tmp/backup-database.sh /tmp/restore-database.sh /usr/local/bin/'
```

### Off the instance, or it is not a backup

`backup-database.sh` writes to `/var/backups/yellowtrack` and, if `S3_BUCKET` is set, copies
each dump to S3 and says so. Leave it unset and it prints a warning every run, because the
failure worth insuring against is losing the instance, and a dump on that instance's own
volume goes with it.

```sh
aws s3 mb s3://yellowtrack-backups --region eu-west-1
aws s3api put-public-access-block --bucket yellowtrack-backups \
  --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
```

The instance profile from *Choosing the instance* needs `s3:PutObject` on
`arn:aws:s3:::yellowtrack-backups/*` and nothing else. Not `s3:*`, and not `GetObject`: an
instance that can only write cannot be made to hand back every studio's history by anyone
who reaches it.

Add a lifecycle rule expiring objects after 90 days, or the bucket grows forever.

### The timer

`/etc/systemd/system/yellowtrack-backup.service`:

```ini
[Unit]
Description=Back up the Yellow Track database
After=postgresql.service

[Service]
Type=oneshot
User=postgres
Environment=S3_BUCKET=yellowtrack-backups
# The + runs this one step as root: postgres cannot create a directory under /var/backups.
# Here rather than in a setup instruction, so the unit provisions what it needs and there
# is no manual step to leave undone.
ExecStartPre=+/usr/bin/install -d -o postgres -g postgres -m 700 /var/backups/yellowtrack
ExecStart=/usr/local/bin/backup-database.sh
```

`/etc/systemd/system/yellowtrack-backup.timer`:

```ini
[Unit]
Description=Daily database backup

[Timer]
OnCalendar=daily
# Runs on the next boot if the instance was down when it was due. Without this a stopped
# instance silently skips backups for as long as it is stopped.
Persistent=true
RandomizedDelaySec=15m

[Install]
WantedBy=timers.target
```

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now yellowtrack-backup.timer
sudo systemctl start yellowtrack-backup.service   # once, now, rather than waiting a day
sudo journalctl -u yellowtrack-backup -n 20 --no-pager
```

### Restoring, which is the half that matters

```sh
sudo -u postgres restore-database.sh --latest
```

That restores the newest dump into a scratch database, prints the table and row counts,
drops it, and exits non-zero if fewer tables came back than the schema has. Nothing live is
touched, so it is safe to run whenever — which is the point, because the alternative is
finding out during an outage.

Run it now, while the answer being "no" is merely embarrassing.

For a real restore, after `sudo systemctl stop yellowtrack`:

```sh
sudo -u postgres restore-database.sh --latest --into yellowtrack
```

It asks you to type the database name first. `--clean` replaces rather than merges, because
a database half restored from a backup and half left over is a state nobody can reason
about.

### The rehearsal, on a timer

Backing up nightly proves a dump gets written. Nothing there proves it can still come back,
and the failures that stop a dump being restorable — a disk that filled halfway through, a
`pg_dump` that started as Postgres was shutting down — leave a file of about the right size
in about the right place.

`/etc/systemd/system/yellowtrack-restore-check.service`:

```ini
[Unit]
Description=Prove the newest Yellow Track backup can be restored
After=postgresql.service

[Service]
Type=oneshot
User=postgres
# Nothing live is touched: this restores into a scratch database, counts what came back and
# drops it. It exits non-zero when fewer tables return than the schema has, which is what
# makes systemd record a failure rather than a log line nobody reads.
ExecStart=/usr/local/bin/restore-database.sh --latest
```

`/etc/systemd/system/yellowtrack-restore-check.timer`:

```ini
[Unit]
Description=Weekly restore rehearsal

[Timer]
# An hour after the nightly backup, so it rehearses the dump that was just taken rather
# than yesterday's.
OnCalendar=Mon *-*-* 01:00:00
Persistent=true
RandomizedDelaySec=10m

[Install]
WantedBy=timers.target
```

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now yellowtrack-restore-check.timer
sudo systemctl start yellowtrack-restore-check.service   # once, now, rather than waiting a week
sudo journalctl -u yellowtrack-restore-check -n 20 --no-pager
```

You are looking for `Restorable.` and a table count matching the schema.

**A failed unit nobody looks at is not much better than no unit**, so
`verify-deployment.sh` checks the last result rather than only that the timer exists —
`systemctl is-failed` on the service is the difference between a rehearsal that runs and a
rehearsal that passes.

### What a dump does not contain

`/etc/yellowtrack/env`. It holds the database and SES passwords, and a restored database
with no credentials is not a running service. Keep a copy in a password manager — not in
the bucket beside the dumps, where one compromise would yield both.

---

## Being told

Everything above knows how to fail loudly and nothing listens. `verify-deployment.sh` has
always found the failures that do not announce themselves; the backup and restore-check
timers have always exited non-zero. All of it went into the journal, and the studio still
got there first — every time, so far.

`watch-deployment.sh` adds no checks. It runs the ones that exist and emails when the answer
*changes*.

### The one new setting

```
ALERT_EMAIL=you@yourdomain
```

in `/etc/yellowtrack/env`. It sends through the SES credentials already there, so there is
no MTA to install and nothing new to hold a secret — `curl` speaks SMTP.

### The units

`/etc/systemd/system/yellowtrack-watch.service`:

```ini
[Unit]
Description=Check the Yellow Track deployment and report what changed
After=network-online.target

[Service]
Type=oneshot
# Root, because the checks read /etc/yellowtrack/env and query Postgres as a superuser.
# Both are read-only; nothing here writes to the database.
User=root
ExecStartPre=/usr/bin/install -d -o root -g root -m 750 /var/lib/yellowtrack
ExecStart=/usr/local/bin/watch-deployment.sh https://api.yourdomain
```

`/etc/systemd/system/yellowtrack-watch.timer`:

```ini
[Unit]
Description=Check the deployment every fifteen minutes

[Timer]
OnBootSec=5m
OnUnitActiveSec=15m
# Deliberately absent: Persistent=true. Catching up on missed runs after a restart tells
# you what was wrong an hour ago, which is not a thing anybody can act on.

[Install]
WantedBy=timers.target
```

`watch-deployment.sh` and `verify-deployment.sh` must sit **beside each other** in
`/usr/local/bin` — the first runs the second from its own directory.

**`deploy-server.sh` puts them there**, every time it runs, so they cannot drift from the
repository. The instance has no checkout of its own, which is why `sudo install` from a
`scripts/` directory does not work there — there isn't one.

To place them without a full server deploy, from the repository on your own machine:

```sh
scp scripts/watch-deployment.sh scripts/verify-deployment.sh yellowtrack:/tmp/
ssh yellowtrack 'sudo install -m 755 /tmp/watch-deployment.sh /tmp/verify-deployment.sh /usr/local/bin/ &&
    rm -f /tmp/watch-deployment.sh /tmp/verify-deployment.sh'
```

Then, on the instance:

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now yellowtrack-watch.timer
sudo systemctl start yellowtrack-watch.service    # once, now, rather than waiting
sudo journalctl -u yellowtrack-watch -n 40 --no-pager
```

### What it sends, and what it does not

Mail goes out on a **change of state**: healthy to failing, failing to healthy. While
something stays broken it says so once a day and is otherwise quiet. A full disk stays full
for days; at four runs an hour that would be several hundred identical messages, and the
next real alert would arrive underneath them.

The recovery message matters as much as the alarm. Without it the last thing you heard was
that something broke, and silence afterwards is indistinguishable from the watchdog having
died too.

### It cannot report a failure of the thing it reports through

**If mail is what is broken, the mail saying mail is broken does not arrive.** There is no
way around that from a single instance. So instead:

- every run writes its verdict to `/var/lib/yellowtrack/watch-state`
- a failure to send is itself logged at error level
- the unit exits non-zero, so `systemctl status yellowtrack-watch` shows it failed
- the full report is in the journal whether or not anybody could be reached

```sh
sudo systemctl status yellowtrack-watch
cat /var/lib/yellowtrack/watch-state
```

That is the offline answer, and it is worth knowing it exists *before* the day mail stops.

A second instance watching the first is the real fix, and it is the same missing piece as
staging: there is only one box. Recorded in `docs/ROADMAP.md` rather than pretended away.

---

## Apache

The server binds to `127.0.0.1` on purpose: Apache terminates TLS and is the only thing
that reaches it. That is what `ADR 0007` assumed and it is why the JAR has no TLS
configuration of its own.

Write the **port 80** vhost first. The 443 one below is where you end up, not where you
start: its certificate paths do not exist yet, and Apache will not start while they are
missing.

`/etc/apache2/sites-available/yellowtrack.conf`:

```apache
<VirtualHost *:80>
    ServerName api.yourdomain

    DocumentRoot /var/www/html

    # Let's Encrypt fetches a challenge file from this path over plain HTTP, and it must be
    # served from disk. Proxied to the application it is a 404, and certbot then fails a
    # validation in a way that reads like a DNS problem.
    ProxyPass /.well-known/acme-challenge/ !

    ProxyPreserveHost On
    ProxyPass        / http://127.0.0.1:8080/
    ProxyPassReverse / http://127.0.0.1:8080/

    # A device that has been offline for a day pushes its whole outbox at once.
    ProxyTimeout 120
</VirtualHost>
```

```sh
sudo a2enmod proxy proxy_http headers
sudo a2ensite yellowtrack
sudo apache2ctl configtest
sudo systemctl reload apache2

# Prove the proxy before adding TLS, so a certbot failure is only ever about certbot.
curl -s http://api.yourdomain/health          # {"status":"ok"}
```

Ports 80 and 443 have to be open to `0.0.0.0/0` in the security group: the challenge is
Let's Encrypt connecting inbound, so it cannot be narrowed to your own address the way 22
is. 5432 stays shut.

Then, once `dig +short api.yourdomain` returns the Elastic IP and not before — a validation
attempted against a name that does not resolve counts against Let's Encrypt's rate limits:

```sh
# Rehearse the validation. certonly, because --dry-run cannot rehearse installing a
# certificate and refuses to run alongside --apache's installer:
#     --dry-run currently only works with the 'certonly' or 'renew' subcommands
# The authenticator is the same either way, so this exercises the part that fails.
sudo certbot certonly --apache -d api.yourdomain --dry-run

# Then for real, with the installer, answering yes to the HTTPS redirect.
sudo certbot --apache -d api.yourdomain
```

Certbot writes `yellowtrack-le-ssl.conf` beside your file, which is the finished article:

```apache
<VirtualHost *:443>
    ServerName api.yourdomain

    SSLEngine on
    SSLCertificateFile      /etc/letsencrypt/live/api.yourdomain/fullchain.pem
    SSLCertificateKeyFile   /etc/letsencrypt/live/api.yourdomain/privkey.pem

    ProxyPreserveHost On
    ProxyPass        / http://127.0.0.1:8080/
    ProxyPassReverse / http://127.0.0.1:8080/

    ProxyTimeout 120
</VirtualHost>
```

No certificate needs buying: Let's Encrypt is a certificate authority browsers trust like
any other. Paid certificates buy organisation validation, which nobody using a private API
will ever look at.

```sh
sudo certbot renew --dry-run             # renewal, which is not the same as issuance
systemctl list-timers | grep certbot     # what runs it unattended
```

Check renewal once. Issuance working says nothing about whether renewal will, and the way
you find out otherwise is every device losing sync on the same morning, ninety days later.

### A subdomain, not the apex

If the main domain serves something else — a gallery host, a marketing site — this belongs
on `api.` with its own A record. Adding a record cannot disturb the existing ones; editing
the apex or `www` is the only way to take the other service down, so leave them alone.

---

## Changing the version

One line, in the root `build.gradle.kts`:

```kotlin
allprojects {
    version = "0.7.0"
}
```

Everything that shows a version derives from it: what the application displays, the Android
`versionName` and `versionCode`, and the desktop installers. Change it, commit, and run
`release.sh` — the guard there refuses to release a working tree that differs from what CI
will build, so the commit is not optional.

### The one place it is not literal

**macOS will not accept a leading zero.** jpackage refuses with *"the first number in an
app-version cannot be zero or negative"*, and this project is pre-1.0, so a zero major is
carried to 1 for the installer only: **0.7.0 installs as 1.7.0**.

The application still reports 0.7.0, and that is the number to quote. The two agree exactly
from 1.0.0 onward, and releasing 1.0.0 is the way to stop having to explain it.

### Android's second number

`versionCode` has to be an integer that only ever increases, so it is derived from the same
three numbers: 0.7.0 becomes 700, 1.2.3 becomes 10203. Nothing to maintain by hand, and it
cannot go backwards while the version goes forwards.

### iOS

Not wired up. `Info.plist` carries no version, so it takes Xcode's default. Worth fixing
before there is a TestFlight build to confuse anybody with.

---

## Everything, in one command

```sh
./scripts/release.sh yellowtrack https://app.yourdomain
```

Builds and deploys the web application and all three desktop installers, and says where it
has got to as it goes.

Add `--dry-run` to see the five steps without touching anything.

### What it does, and why in that order

1. **Checks the working tree.** Refuses to run with uncommitted changes or an unpushed
   branch. CI builds a commit and this machine builds a working tree, and nothing downstream
   would notice they had diverged — the installers and the site would be two different
   versions of the application, deployed a minute apart, under one name
2. **Starts CI**, because it is the long pole. The installers cannot be built here: jpackage
   only emits the format of the host it runs on, so a `.msi` needs Windows whatever this
   laptop can do
3. **Builds and deploys the web application** while CI works. Two minutes against CI's
   ten to fifteen; in sequence that is ten minutes of watching a progress line
4. **Waits for the installers**, and stops the release if any leg failed rather than
   deploying around it
5. **Publishes the installers** to `/downloads/`

### What it deliberately leaves alone

**The server.** It moves at a different speed to its clients, and a release that restarted
the API every time somebody fixed a label would take the studio offline for no reason. Use
`deploy-server.sh`.

If step 4 or 5 fails, the web application deployed in step 3 is already live and unaffected.
That is the intended failure mode: the half that works stays up.

---

## The web application

Static files on the same instance, served by the same Apache. It is a client like the
desktop and the phones — it talks to the API over HTTPS and keeps its own copy of the data
in the browser — so it needs no process, no port and no database of its own.

```sh
./scripts/deploy-web.sh yellowtrack
```

That builds the production distribution, rsyncs it to `/var/www/yellowtrack`, and then asks
the server what MIME type it is serving the wasm with, which is the one thing that silently
breaks this.

### Why the MIME type is the whole configuration

The application is about 21MB, nearly all of it two WebAssembly binaries. A browser will
only compile those if they arrive as `application/wasm`. Apache does not know that
extension out of the box, so it serves them as `application/octet-stream`, and the result
is a page that loads, shows nothing, and reports the reason only in the developer console.

`deploy-web.sh` fails rather than reporting success when that is wrong.

### The vhost

Create the directory and let the deploying user write to it:

```sh
sudo mkdir -p /var/www/yellowtrack /var/www/yellowtrack-downloads
sudo chown "$USER":"$USER" /var/www/yellowtrack /var/www/yellowtrack-downloads
```

`/etc/apache2/sites-available/yellowtrack-web.conf`, beside the API's vhost:

```apache
<VirtualHost *:80>
    ServerName app.yourdomain
    DocumentRoot /var/www/yellowtrack

    # The reason this file exists. Without it the browser refuses to compile the wasm.
    AddType application/wasm .wasm

    # 21MB uncompressed, about 6MB gzipped. On a phone on mobile data that is the
    # difference between a slow first load and an abandoned one.
    AddOutputFilterByType DEFLATE application/wasm application/javascript text/html text/css

    # The installers, which live outside DocumentRoot on purpose: deploy-web.sh rsyncs
    # the site with --delete, so anything of ours inside it would vanish on the next
    # deployment of the web application.
    Alias /downloads /var/www/yellowtrack-downloads

    <Directory /var/www/yellowtrack-downloads>
        Require all granted
        Options -Indexes
    </Directory>

    <Directory /var/www/yellowtrack>
        Require all granted
        Options -Indexes

        # Revalidate by default, because most of what the build emits is NOT content
        # hashed. Only the two large .wasm files are; yellow-track-web.js, the numbered
        # webpack chunks and sql-wasm.wasm all keep the same name from one release to
        # the next, so anything cached by name is cached straight across a deploy.
        #
        # "no-cache" does not mean do not store it. The browser keeps the file and asks
        # whether it changed, which costs one 304 per file and is always right.
        Header set Cache-Control "no-cache"

        # These two do carry a hash, and they are 15MB of the 17MB — so almost all of
        # the bytes are still cached hard, which is the part that matters on mobile data.
        <FilesMatch "^[0-9a-f]{16,}\.wasm$">
            Header set Cache-Control "public, max-age=31536000, immutable"
        </FilesMatch>
    </Directory>
</VirtualHost>
```

Then the certificate, which certbot will add the `:443` vhost for:

```sh
sudo a2enmod headers deflate          # the caching and compression rules need both
sudo a2ensite yellowtrack-web
sudo apache2ctl configtest
sudo systemctl reload apache2

sudo certbot --apache -d app.yourdomain
```

Same order as the API vhost, and for the same reason: port 80 first, because certbot
writes the 443 one and cannot do so against a site Apache will not start.

### One environment, and what that means here

There is one instance and one database. The web build, the desktop build and both phone
builds all point at the same `yellowtrack.serverUrl`, which is production.

So deploying the web application puts it in front of real studio data immediately, and
there is nowhere to try it first. That is a known gap rather than an oversight — see the
staging entry in `docs/ROADMAP.md` — and until it closes, the way to try a client against
something disposable is to run a server locally and point one build at it:

```sh
./gradlew :server:run
./gradlew :desktopApp:run -Pyellowtrack.serverUrl=http://localhost:8080
```

That is a per-build decision taken at compile time. No client can switch environments while
running, and none should be able to: a button that repoints an application at another
studio's data is a button somebody eventually presses.

---

## The desktop application

Three installers, one per operating system, built by `jpackage` — which only emits the
format of the machine it runs on. A `.msi` has to be built on Windows however capable the
rest of the toolchain is, so there is no cross-compiling here and no way to arrange one.

On the machine you are on:

```sh
./gradlew :desktopApp:packageReleaseDistributionForCurrentOS
```

The result lands in `desktopApp/build/compose/binaries/main-release/<format>/`.

For all three at once, let CI build them — from a tag, or from the Actions tab, which is
the same thing without minting a version:

```sh
git tag v0.7.0 && git push origin v0.7.0
```

Then publish them from a machine that has ssh access:

```sh
./scripts/deploy-installers.sh yellowtrack
```

That takes the installers from the most recent successful Release run, writes a download
page beside them, and puts both at `https://app.yourdomain/downloads/`.

### Why CI does not publish them

It would need an ssh key with write access to the instance in a public repository's secrets,
and port 22 open to GitHub's address ranges rather than to one address — which is most of
what restricting it was for. So CI builds and somebody with a key publishes, the same
division as the server and the web application.

### Not inside the site's directory

`deploy-web.sh` rsyncs `/var/www/yellowtrack` with `--delete`. Installers kept inside it
would be deleted by the next deployment of the web application, at a moment unrelated to
anything anybody did to them. They live in `/var/www/yellowtrack-downloads` and are reached
through an `Alias`.

### Release builds minify, and that is where this first broke

`packageRelease*` runs ProGuard; the development build does not. The first time anyone ran
it, ProGuard failed on OkHttp's optional TLS providers and its GraalVM substitutions —
classes that are referenced, never present, and never used, because this application runs on
a JVM and uses the platform's TLS.

`desktopApp/proguard-rules.pro` names them one at a time rather than passing
`-ignorewarnings`, which would have fixed the build in a line and silently swallowed the
next unresolved reference, which might be a class the application needs.

### Nothing is signed

macOS will report that the developer cannot be verified; Windows SmartScreen will call the
installer unrecognised. Both can be got past by hand — right-click Open on macOS, More info
then Run anyway on Windows — and neither should be asked of a studio.

Signing needs an Apple Developer account and a Windows code-signing certificate. Until then
the browser build is the honest way to hand this to somebody: no install, no warning, and no
certificate.

---

## Pointing the clients at it

The clients are built with the server baked in, declared once in `gradle.properties`:

```properties
yellowtrack.serverUrl=https://api.yourdomain
```

so an ordinary build reaches the deployed server and needs no flag:

```sh
./gradlew :desktopApp:packageDistributionForCurrentOS
./gradlew :androidApp:assembleRelease
```

Point it elsewhere for an afternoon by overriding the same property:

```sh
./gradlew :desktopApp:run -Pyellowtrack.serverUrl=http://localhost:8080
```

**This defaulted to loopback until 0.7.0, and it was the wrong way round.** Every build made
without remembering the flag pointed at a port on the machine that compiled it — which
fails on the desktop the moment no development server is running, and on a phone can never
work at all, because `localhost` there is the phone. It presents as *"could not reach the
server"* with a server that is perfectly healthy, so check the built value before chasing
the deployment:

```sh
./gradlew :shared:app:generateBuildInfo
grep SERVER_URL shared/app/build/generated/buildinfo/com/yellowtrack/platform/app/BuildInfo.kt
```

---

## Verifying it

```sh
./scripts/verify-deployment.sh https://api.yourdomain
```

Run it on the instance, after the server is up. It checks the three silent failures above
plus a few others, exits non-zero if any fail, and — importantly — reports what it *could
not* check as skipped rather than passing over it. A check that quietly does not run reads
as a check that passed, which is the exact shape of failure the script exists to catch.

The superuser check was verified by deliberately granting `SUPERUSER` to `yellowtrack_app`
and confirming it fails, rather than by assuming.

## Verifying it by hand, in order

```sh
# 1. The process is up.
curl -s https://api.yourdomain/health

# 2. It can reach what it needs. database:false is a misconfiguration, not a restart.
curl -s https://api.yourdomain/ready

# 3. The policies are live. Must be f | f.
psql -c "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'yellowtrack_app'"

# 4. Mail actually leaves. Answers 202 either way, so watch the log, not the response.
curl -s -X POST https://api.yourdomain/auth/forgot-password \
  -H 'Content-Type: application/json' -d '{"email":"you@yourdomain"}'
journalctl -u yellowtrack -n 50
```

Step 4 is the one to do deliberately. It is the only part of the system designed to tell you
nothing when it fails.
