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

### 2. SES starts in sandbox, and a password reset will not say so

In the sandbox SES rejects mail to any address you have not verified. The reset endpoint
answers `202 If that address has an account, a code is on its way` **whether or not the
send succeeded** — deliberately, because saying otherwise would reveal which addresses have
accounts (`ADR 0010` decision 3). So in the sandbox, resets appear to work and silently
never arrive.

The failure goes to the log and nowhere else. Request production access before anybody
needs to reset a password, and until it is granted, treat reset as not working.

### 3. Nothing is backed up unless you back it up

Postgres on the same instance as the application means one lost instance is one lost
business. `docs/VISION.md` asks for zero avoidable data loss and this is the most avoidable
kind there is. A `pg_dump` to S3 on a timer, and **a restore you have actually performed
once** — an untested backup is a belief, not a backup.

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

## Apache

The server binds to `127.0.0.1` on purpose: Apache terminates TLS and is the only thing
that reaches it. That is what `ADR 0007` assumed and it is why the JAR has no TLS
configuration of its own.

```apache
<VirtualHost *:443>
    ServerName api.yourdomain

    SSLEngine on
    SSLCertificateFile      /etc/letsencrypt/live/api.yourdomain/fullchain.pem
    SSLCertificateKeyFile   /etc/letsencrypt/live/api.yourdomain/privkey.pem

    ProxyPreserveHost On
    ProxyPass        / http://127.0.0.1:8080/
    ProxyPassReverse / http://127.0.0.1:8080/

    # A device that has been offline for a day pushes its whole outbox at once.
    ProxyTimeout 120
</VirtualHost>
```

`certbot --apache` for the certificate. Renewal is a cron job it installs; check it once,
because an expired certificate takes every device offline simultaneously.

---

## Pointing the clients at it

The clients are built with the server baked in. The default is loopback, which no phone can
reach:

```sh
./gradlew :desktopApp:packageDistributionForCurrentOS -Pyellowtrack.serverUrl=https://api.yourdomain
```

A build without that flag will look correct and fail to reach anything, so it is worth
checking Settings → Synchronisation on a device before shipping one.

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
