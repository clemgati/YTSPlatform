#!/usr/bin/env bash

# Checks a deployment for the failures that do not announce themselves.
#
# Run it ON the instance, after the server is up. Everything here is something that leaves a
# server which starts, answers, and looks correct — see docs/DEPLOYMENT.md.
#
#     ./verify-deployment.sh https://api.yourdomain
#
# Exits non-zero if any check fails, so it can be a smoke test after a deploy rather than
# something somebody remembers to read.

set -uo pipefail

BASE_URL="${1:-http://127.0.0.1:8080}"
DB_NAME="${DB_NAME:-yellowtrack}"

failures=0
skipped=0

pass() { printf '  \033[32m✓\033[0m %s\n' "$1"; }
fail() { printf '  \033[31m✗\033[0m %s\n' "$1"; failures=$((failures + 1)); }
# Reported loudly rather than passed over. A check that silently did not run reads as a
# check that passed, which is the shape of failure this script exists to find.
skip() { printf '  \033[33m—\033[0m %s\n' "$1"; skipped=$((skipped + 1)); }
note() { printf '    %s\n' "$1"; }

# The superuser connection differs by platform: a `postgres` OS user on the instance, the
# invoking account on a Homebrew laptop.
psql_super() {
    if id postgres >/dev/null 2>&1; then
        sudo -u postgres psql "$@"
    else
        psql "$@"
    fi
}

echo
echo "Verifying $BASE_URL"
echo

# --- 1. The process is up -----------------------------------------------------------------

echo "Reachable"
if curl -fsS -m 10 "$BASE_URL/health" >/dev/null 2>&1; then
    pass "/health answers"
else
    fail "/health did not answer"
    note "Nothing below will mean anything. Check: systemctl status yellowtrack, and Apache."
    echo
    exit 1
fi

# --- 2. It can reach what it needs ---------------------------------------------------------

echo
echo "Ready"
readiness="$(curl -fsS -m 10 "$BASE_URL/ready" 2>/dev/null || echo '{}')"

if echo "$readiness" | grep -q '"database":true'; then
    pass "database reachable"
else
    fail "database not reachable"
    note "DATABASE_URL / DATABASE_USER / DATABASE_PASSWORD in the environment file."
fi

if echo "$readiness" | grep -q '"mail":true'; then
    pass "mail configured"
else
    fail "MAIL_HOST is not set"
    note "Password reset will answer 202 and never deliver. That is by design — the endpoint"
    note "cannot say whether an address has an account — so this check is the only warning."
fi

# --- 3. The tenant boundary is actually enforced ---------------------------------------------

echo
echo "Tenant isolation"
if ! command -v psql >/dev/null 2>&1; then
    note "psql not on PATH; skipping the database checks. Run this on the instance."
else
    role_flags="$(psql_super -d postgres -tAc \
        "SELECT rolsuper::text || ' ' || rolbypassrls::text FROM pg_roles WHERE rolname = 'yellowtrack_app'" \
        2>/dev/null)"
    query_status=$?

    if [ "$query_status" -ne 0 ]; then
        # Not a finding. Saying "the role does not exist" here would report an inability to
        # ask as though it were an answer, which is worse than saying nothing.
        skip "could not query Postgres as a superuser"
        note "Run this on the instance, where 'sudo -u postgres psql' works."
    elif [ -z "$role_flags" ]; then
        fail "the yellowtrack_app role does not exist"
        note "Migrations create it. Has the server started against this database at least once?"
    elif [ "$role_flags" = "false false" ]; then
        pass "yellowtrack_app is neither superuser nor BYPASSRLS"
    else
        fail "yellowtrack_app has superuser or BYPASSRLS ($role_flags)"
        note "Every row level security policy is inert. Studios can read each other's data."
    fi

    # The role the *server* connects as is the one that matters, and it is in the
    # environment rather than the database. A superuser here is the whole failure.
    configured_user="$(sudo grep -hs '^DATABASE_USER=' /etc/yellowtrack/env 2>/dev/null | cut -d= -f2- || echo "")"
    if [ -z "$configured_user" ]; then
        note "DATABASE_USER not found in /etc/yellowtrack/env; checking is up to you."
    elif [ "$configured_user" = "yellowtrack_app" ]; then
        pass "the server connects as yellowtrack_app"
    else
        fail "the server connects as '$configured_user', not yellowtrack_app"
        note "If that role is a superuser, every policy is bypassed and nothing looks wrong."
    fi

    unguarded="$(psql_super -d "$DB_NAME" -tAc "
        SELECT count(*) FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.column_name = 'studio_id'
          AND c.table_name NOT IN ('studio_member', 'auth_session')
          AND NOT EXISTS (
              SELECT 1 FROM pg_class p
              WHERE p.relname = c.table_name AND p.relrowsecurity AND p.relforcerowsecurity
          )" 2>/dev/null || echo "?")"

    if [ "$unguarded" = "0" ]; then
        pass "every studio-scoped table has row level security, forced"
    elif [ "$unguarded" = "?" ]; then
        note "Could not check the policies; is DB_NAME=$DB_NAME right?"
    else
        fail "$unguarded studio-scoped tables are not protected"
        note "ENABLE without FORCE looks protected and is not."
    fi
fi

# --- 4. The database is not reachable from outside -------------------------------------------

echo
echo "Exposure"
if ! command -v ss >/dev/null 2>&1; then
    skip "ss is not available, so nothing here was checked"
    note "Run this on the instance. A check that quietly does not run is worse than none."
else
    if ss -ltn | grep -qE '(0\.0\.0\.0|\*):5432'; then
        fail "Postgres is listening on all interfaces"
        note "listen_addresses in postgresql.conf should be 'localhost'."
    else
        pass "Postgres is not listening publicly"
    fi

    if ss -ltn | grep -qE '(0\.0\.0\.0|\*):8080'; then
        fail "the server is listening on all interfaces"
        note "It should bind 127.0.0.1 and be reached only through Apache."
    else
        pass "the server is bound to loopback"
    fi
fi

# --- 5. Backups exist and have been restored -------------------------------------------------

echo
echo "Backups"
if ! command -v systemctl >/dev/null 2>&1; then
    skip "systemd is not available, so the backup timer was not checked"
elif sudo systemctl list-timers 2>/dev/null | grep -q backup; then
    pass "a backup timer exists"
else
    fail "no backup timer found"
    note "One lost instance is one lost business. And an untested restore is a belief."
fi

echo
if [ "$failures" -eq 0 ] && [ "$skipped" -eq 0 ]; then
    printf '\033[32mAll checks passed.\033[0m\n\n'
elif [ "$failures" -eq 0 ]; then
    printf '\033[32mAll checks passed\033[0m, \033[33m%s skipped\033[0m — not the same as passing.\n\n' "$skipped"
else
    printf '\033[31m%s check(s) failed\033[0m, %s skipped.\n\n' "$failures" "$skipped"
fi

exit "$((failures > 0))"
