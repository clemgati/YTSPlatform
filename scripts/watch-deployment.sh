#!/usr/bin/env bash

# Runs the deployment checks on a timer and emails when the answer changes.
#
#     ./watch-deployment.sh https://api.yourdomain
#
# Run it ON the instance, from yellowtrack-watch.timer. See docs/DEPLOYMENT.md for the
# units and for ALERT_EMAIL, which is the only new setting it needs.
#
# `verify-deployment.sh` has always known how to find the failures that do not announce
# themselves. Nothing ran it. The backup and restore-check timers have always exited
# non-zero when they failed, and nothing read that either. So this adds no new checks — it
# is the part that was missing, which is somebody being told.
#
# ## Why it is careful about sending
#
# A check that mails on every failing run is a check that gets filtered. A full disk stays
# full for days; at one run every fifteen minutes that is several hundred identical
# messages, and the next genuine alert arrives underneath them. So mail goes out when the
# answer *changes* — healthy to failing, failing to healthy — and once a day otherwise, to
# say the thing is still broken.
#
# ## What it cannot do
#
# **Mail is one of the things being watched, so mail cannot be relied on to report it.** If
# SES has suspended sending, the alert saying SES has suspended sending does not arrive.
# There is no way around that from one instance, so instead: every run writes its verdict
# to $STATE_DIR, a failure to send is itself logged at error, and the journal keeps the full
# report whether or not anybody could be reached. `systemctl status yellowtrack-watch`
# answers the question offline.
#
# It also cannot see a bounce. SES accepts the message and bounces afterwards, out of band.

set -uo pipefail

BASE_URL="${1:-http://127.0.0.1:8080}"

STATE_DIR="${STATE_DIR:-/var/lib/yellowtrack}"
STATE_FILE="$STATE_DIR/watch-state"
ENV_FILE="${ENV_FILE:-/etc/yellowtrack/env}"

# How long a failure stays quiet before it says so again. A day: long enough not to be
# noise, short enough that a problem cannot be forgotten about over a weekend.
REPEAT_AFTER_SECONDS="${REPEAT_AFTER_SECONDS:-86400}"

HERE="$(cd "$(dirname "$0")" && pwd)"
NOW="$(date +%s)"
STAMP="$(date -u '+%Y-%m-%d %H:%M:%S UTC')"

# --- Reading the environment --------------------------------------------------------------
#
# Key by key rather than sourcing the file. It is systemd's EnvironmentFile format, which
# resembles shell closely enough to be sourced and differs in quoting — and sourcing it
# would run whatever a mistyped line happened to say, as root, on a file that holds the
# database password.

# Read directly when it can be, which under the timer is always: the unit runs as root and
# the file is root-owned 600. The sudo branch is for running this by hand from an account
# that is not root, where a prompt is expected rather than a surprise.
setting() {
    if [ -r "$ENV_FILE" ]; then
        grep -hs "^$1=" "$ENV_FILE" 2>/dev/null | tail -1 | cut -d= -f2- || true
    else
        sudo grep -hs "^$1=" "$ENV_FILE" 2>/dev/null | tail -1 | cut -d= -f2- || true
    fi
}

ALERT_EMAIL="$(setting ALERT_EMAIL)"
MAIL_HOST="$(setting MAIL_HOST)"
MAIL_PORT="$(setting MAIL_PORT)"
MAIL_USERNAME="$(setting MAIL_USERNAME)"
MAIL_PASSWORD="$(setting MAIL_PASSWORD)"
MAIL_FROM="$(setting MAIL_FROM)"

# --- Running the checks --------------------------------------------------------------------

REPORT="$(mktemp)"
trap 'rm -f "$REPORT"' EXIT

# Not `set -e`: a failing verification is the normal path through this script, not an error
# in it.
"$HERE/verify-deployment.sh" "$BASE_URL" >"$REPORT" 2>&1
VERIFY_STATUS=$?

if [ "$VERIFY_STATUS" -eq 0 ]; then
    STATUS="ok"
else
    STATUS="failing"
fi

# Whatever happens to the mail, this is on the record. journalctl -u yellowtrack-watch.
cat "$REPORT"

# --- Deciding whether to say anything -------------------------------------------------------

PREVIOUS_STATUS="unknown"
LAST_ALERT_AT=0

if [ -r "$STATE_FILE" ]; then
    PREVIOUS_STATUS="$(sed -n 's/^status=//p' "$STATE_FILE" | tail -1)"
    LAST_ALERT_AT="$(sed -n 's/^last_alert_at=//p' "$STATE_FILE" | tail -1)"
fi

: "${PREVIOUS_STATUS:=unknown}"
: "${LAST_ALERT_AT:=0}"

SUBJECT=""

if [ "$STATUS" = "failing" ] && [ "$PREVIOUS_STATUS" != "failing" ]; then
    SUBJECT="Yellow Track: a check has started failing"
elif [ "$STATUS" = "failing" ] && [ "$((NOW - LAST_ALERT_AT))" -ge "$REPEAT_AFTER_SECONDS" ]; then
    SUBJECT="Yellow Track: still failing"
elif [ "$STATUS" = "ok" ] && [ "$PREVIOUS_STATUS" = "failing" ]; then
    # Worth its own message. Without it, the last thing anybody heard was that something was
    # broken, and silence afterwards is indistinguishable from the watchdog having died too.
    SUBJECT="Yellow Track: recovered"
fi

# --- Sending ---------------------------------------------------------------------------------

send_alert() {
    local subject="$1"

    if [ -z "$ALERT_EMAIL" ]; then
        echo "watch: $subject (ALERT_EMAIL is not set in $ENV_FILE, so nobody was told)" >&2
        return 1
    fi

    if [ -z "$MAIL_HOST" ] || [ -z "$MAIL_USERNAME" ]; then
        echo "watch: $subject (mail is not configured in $ENV_FILE, so nobody was told)" >&2
        return 1
    fi

    local message
    message="$(mktemp)"

    # Written by hand rather than piped to a mail agent, so the instance needs no MTA of its
    # own. curl speaks SMTP, and the credentials SES already has are the ones in use.
    {
        printf 'From: Yellow Track <%s>\n' "${MAIL_FROM:-no-reply@yellowtrack.local}"
        printf 'To: %s\n' "$ALERT_EMAIL"
        printf 'Subject: %s\n' "$subject"
        printf 'Date: %s\n' "$(date -R)"
        printf '\n'
        printf '%s\n\n' "$STAMP"
        printf 'Host:    %s\n' "$(hostname)"
        printf 'Checked: %s\n\n' "$BASE_URL"
        # Colour codes strip out: the report is written for a terminal and this is going to
        # a mail client, where the escapes arrive as punctuation.
        sed 's/\x1b\[[0-9;]*m//g' "$REPORT"
        printf '\nOn the instance:\n'
        printf '    sudo systemctl status yellowtrack-watch\n'
        printf '    sudo journalctl -u yellowtrack-watch -n 100\n'
    } >"$message"

    local sent=0
    curl --silent --show-error --ssl-reqd --max-time 30 \
        --url "smtp://$MAIL_HOST:${MAIL_PORT:-587}" \
        --user "$MAIL_USERNAME:$MAIL_PASSWORD" \
        --mail-from "${MAIL_FROM:-no-reply@yellowtrack.local}" \
        --mail-rcpt "$ALERT_EMAIL" \
        --upload-file "$message" || sent=$?

    rm -f "$message"

    if [ "$sent" -ne 0 ]; then
        # The case this script cannot solve, said plainly rather than swallowed. If mail is
        # what is broken, this line in the journal is the alert.
        echo "watch: could not send the alert (curl exited $sent). The report above is the only copy." >&2
        return 1
    fi

    echo "watch: alerted $ALERT_EMAIL — $subject"
    return 0
}

ALERT_AT="$LAST_ALERT_AT"

if [ -n "$SUBJECT" ]; then
    # The timestamp moves whether or not the send worked. Otherwise a broken mail path
    # retries on every run, which is the flood this exists to avoid — and it would be
    # flooding about the thing that cannot be delivered.
    ALERT_AT="$NOW"
    send_alert "$SUBJECT" || true
fi

# --- Remembering ------------------------------------------------------------------------------

if ! mkdir -p "$STATE_DIR" 2>/dev/null; then
    echo "watch: cannot write $STATE_DIR, so every run will look like the first" >&2
else
    {
        echo "status=$STATUS"
        echo "last_alert_at=$ALERT_AT"
        echo "last_run_at=$NOW"
        echo "last_run=$STAMP"
    } >"$STATE_FILE"
fi

# Non-zero when something is wrong, so `systemctl status` shows the unit as failed and the
# state is visible without reading anything.
exit "$VERIFY_STATUS"
