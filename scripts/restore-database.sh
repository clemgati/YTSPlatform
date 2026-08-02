#!/usr/bin/env bash

# Restores a backup — or, by default, proves one could be restored.
#
#     sudo -u postgres ./restore-database.sh --latest              # rehearse, harmlessly
#     sudo -u postgres ./restore-database.sh --latest --into yellowtrack   # for real
#
# The default is the rehearsal because that is the operation worth running when nothing is
# wrong. An untested backup is a belief about a file, and the belief is only ever checked
# on the worst day of the year.

set -euo pipefail

DB_NAME="${DB_NAME:-yellowtrack}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/yellowtrack}"
SCRATCH_DB="${SCRATCH_DB:-yellowtrack_restore_check}"

dump=""
target=""

while [ $# -gt 0 ]; do
    case "$1" in
        --latest)
            dump="$(find "$BACKUP_DIR" -name "${DB_NAME}-*.dump" -type f | sort | tail -1)"
            shift
            ;;
        --into)
            target="${2:-}"
            shift 2
            ;;
        -*)
            echo "unknown option: $1" >&2
            exit 2
            ;;
        *)
            dump="$1"
            shift
            ;;
    esac
done

if [ -z "$dump" ] || [ ! -f "$dump" ]; then
    echo "usage: $0 [--latest | <dump file>] [--into <database>]" >&2
    [ -n "$dump" ] && echo "no such file: $dump" >&2
    exit 2
fi

# Read the time out of the name rather than the file's mtime, which changes when a dump is
# copied off the instance and back — the moment you most want to know what you are holding.
taken="$(basename "$dump" .dump | sed "s/^${DB_NAME}-//")"

echo "Backup: $dump"
echo "        $(du -h "$dump" | cut -f1), taken $taken"
echo

# --- Rehearsal -------------------------------------------------------------------------

if [ -z "$target" ]; then
    echo "==> Restoring into $SCRATCH_DB to prove it works. Nothing live is touched."

    dropdb --if-exists "$SCRATCH_DB"
    createdb "$SCRATCH_DB"

    # --no-owner because the scratch database is not owned by the role the dump names, and
    # ownership is not what is being tested here.
    #
    # Errors are counted rather than fatal: a dump restores with complaints about roles and
    # extensions that already exist, and treating those as failure would make the rehearsal
    # cry wolf until nobody runs it.
    errors="$(pg_restore --no-owner --dbname "$SCRATCH_DB" "$dump" 2>&1 >/dev/null | grep -c 'error' || true)"

    tables="$(psql -d "$SCRATCH_DB" -tAc \
        "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")"
    studios="$(psql -d "$SCRATCH_DB" -tAc "SELECT count(*) FROM studio" 2>/dev/null || echo "?")"
    clients="$(psql -d "$SCRATCH_DB" -tAc "SELECT count(*) FROM client" 2>/dev/null || echo "?")"

    echo
    echo "    tables:  $tables"
    echo "    studios: $studios"
    echo "    clients: $clients"
    echo "    pg_restore errors: $errors"

    dropdb "$SCRATCH_DB"

    echo
    if [ "$tables" -lt 25 ]; then
        echo "FAILED: $tables tables restored, which is fewer than the schema has."
        echo "This backup would not bring the business back. Find out why now."
        exit 1
    fi
    echo "Restorable. $tables tables came back and the scratch database has been dropped."
    exit 0
fi

# --- The real thing --------------------------------------------------------------------

echo "==> Restoring into '$target', REPLACING everything in it."
echo
echo "    Stop the server first, or it will write to a database being rebuilt underneath it:"
echo "        sudo systemctl stop yellowtrack"
echo
printf "    Type the database name to continue: "
read -r confirmation

if [ "$confirmation" != "$target" ]; then
    echo "    Did not match. Nothing has been changed."
    exit 1
fi

# --clean drops each object before recreating it, so this replaces rather than merges.
# Merging a backup into a live database is how you end up with a half-old, half-new state
# that no one can reason about.
pg_restore --clean --if-exists --no-owner --dbname "$target" "$dump"

echo
echo "Restored. Start the server and check readiness:"
echo "    sudo systemctl start yellowtrack && curl -s localhost:8080/ready"
