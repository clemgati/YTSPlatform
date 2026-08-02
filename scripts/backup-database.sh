#!/usr/bin/env bash

# Takes a backup of the database. Runs ON the instance, from a systemd timer.
#
#     sudo -u postgres /opt/yellowtrack-ops/backup-database.sh
#
# Every setting is an environment variable with a working default, because the timer that
# calls this passes none of them and a backup that needs arguments is a backup that stops
# happening.

set -euo pipefail

DB_NAME="${DB_NAME:-yellowtrack}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/yellowtrack}"
KEEP_DAYS="${KEEP_DAYS:-14}"
# Optional. Empty means local only — see the warning this prints when it is.
S3_BUCKET="${S3_BUCKET:-}"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dump="$BACKUP_DIR/${DB_NAME}-${stamp}.dump"

# Named rather than left to mkdir, which says "Permission denied" and not which path or
# which user — and this runs as postgres from a timer, where neither is obvious.
if [ ! -d "$BACKUP_DIR" ] && ! mkdir -p "$BACKUP_DIR" 2>/dev/null; then
    echo "cannot create $BACKUP_DIR as $(id -un):" >&2
    echo "    sudo install -d -o $(id -un) -g $(id -gn) -m 700 $BACKUP_DIR" >&2
    exit 1
fi

if [ ! -w "$BACKUP_DIR" ]; then
    echo "$BACKUP_DIR is not writable by $(id -un):" >&2
    echo "    sudo chown $(id -un):$(id -gn) $BACKUP_DIR" >&2
    exit 1
fi

# The dump holds every studio's clients, contracts and takings in plain text. Not fatal if
# it fails: a directory somebody else owns but has let us write to is unusual, not broken.
chmod 700 "$BACKUP_DIR" 2>/dev/null || true

echo "==> Dumping $DB_NAME"
# Custom format rather than plain SQL: compressed, and pg_restore can read a single table
# out of it, which is what you want at 2am when one table was truncated rather than the
# database lost.
pg_dump --format=custom --file="$dump" "$DB_NAME"
chmod 600 "$dump"

# A dump that cannot be read is a file, not a backup, and the difference is only ever
# discovered under pressure. Reading the table of contents costs milliseconds and catches
# the plausible failures: a truncated write, a full disk, a dump taken mid-shutdown.
echo "==> Checking it can be read"
tables="$(pg_restore --list "$dump" | grep -c 'TABLE DATA' || true)"
if [ "$tables" -lt 1 ]; then
    echo "    the dump contains no table data — refusing to count this as a backup" >&2
    rm -f "$dump"
    exit 1
fi
echo "    $(du -h "$dump" | cut -f1), $tables tables"

if [ -n "$S3_BUCKET" ]; then
    echo "==> Copying off the instance"
    # Server-side encryption because the object holds personal data and costs nothing to
    # encrypt. The instance profile supplies the credentials; no keys live on this box.
    aws s3 cp "$dump" "s3://$S3_BUCKET/$(basename "$dump")" --sse AES256
else
    echo "==> No S3_BUCKET set: this backup exists only on this instance"
    echo "    An EBS volume lost with its instance takes these with it. That is the"
    echo "    failure backups are for, and this configuration does not cover it."
fi

# Pruned after the upload, never before: a prune that runs when the dump failed would
# quietly turn one bad night into the loss of every copy.
echo "==> Removing local dumps older than $KEEP_DAYS days"
find "$BACKUP_DIR" -name "${DB_NAME}-*.dump" -type f -mtime "+$KEEP_DAYS" -print -delete

echo "Backed up to $dump"
