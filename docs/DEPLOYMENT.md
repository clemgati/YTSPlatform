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

Migrations still run as the owner — Flyway needs to create tables, and the application
role cannot. That is why there are two roles and why only one of them is in `DATABASE_USER`.

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

## Software

The server is a **JVM application**, not Node — nothing in this stack uses Node except the
web build's toolchain, which runs on your machine and not on the instance.

```sh
sudo dnf install -y java-21-amazon-corretto-headless postgresql16-server httpd
```

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
EnvironmentFile=/etc/yellowtrack/env      # chmod 600, owned by root
Environment=JAVA_OPTS=-Xmx512m
ExecStart=/opt/yellowtrack/bin/server
Restart=on-failure
RestartSec=5

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

## Verifying it, in order

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
