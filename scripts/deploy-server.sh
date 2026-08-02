#!/usr/bin/env bash

# Builds the server and puts it on the instance.
#
#     ./scripts/deploy-server.sh yellowtrack
#
# The argument is an ssh host — a ~/.ssh/config alias is easiest, so no address, user or
# key path lives in this repository. Nothing here is secret and nothing here is specific to
# one deployment.
#
# Deliberately dumb: build, upload, restart, check. No rollback and no blue/green, because
# a photography studio's server going away for four seconds is not an outage worth
# engineering around, and machinery nobody exercises is machinery that does not work when
# it is finally needed.

set -euo pipefail

HOST="${1:-}"
REMOTE_DIR="${REMOTE_DIR:-/opt/yellowtrack}"
SERVICE="${SERVICE:-yellowtrack}"

if [ -z "$HOST" ]; then
    echo "usage: $0 <ssh-host>    e.g. $0 yellowtrack" >&2
    exit 2
fi

cd "$(dirname "$0")/.."

echo "==> Building"
# The tests need a Postgres and this script does not, so they are not run here. Run
# ./gradlew :server:test yourself, or let CI do it — deploying an untested build is a
# choice, and it should be one somebody makes rather than one a script makes quietly.
./gradlew :server:installDist --console=plain -q

echo "==> Uploading to $HOST:$REMOTE_DIR"
# --delete so a library removed from the build is removed from the instance. Without it,
# an old jar lingers on the classpath and the version running is not the version built.
rsync -az --delete \
    server/build/install/server/ \
    "$HOST:$REMOTE_DIR/"

echo "==> Restarting $SERVICE"
ssh "$HOST" "sudo systemctl restart $SERVICE"

echo "==> Waiting for it to answer"
for attempt in $(seq 1 30); do
    if ssh "$HOST" "curl -fsS -m 5 http://127.0.0.1:8080/health" >/dev/null 2>&1; then
        echo "    up after ${attempt}s"
        break
    fi
    if [ "$attempt" -eq 30 ]; then
        echo "    it never answered. Recent logs:" >&2
        ssh "$HOST" "sudo journalctl -u $SERVICE -n 40 --no-pager" >&2
        exit 1
    fi
    sleep 1
done

echo "==> Readiness"
# Reported rather than enforced. A deployment where mail is unconfigured is still a
# deployment; it is a password reset that answers 202 and never arrives, which is worth
# printing every single time rather than only when somebody thinks to look.
# Without --fail, so a 503 prints the body saying why rather than "error: 503". Being
# unready is the thing this line exists to report; hiding it defeats the point.
ssh "$HOST" "curl -sS -m 5 -w ' (HTTP %{http_code})' http://127.0.0.1:8080/ready" || true
echo

echo "Deployed. Run ./scripts/verify-deployment.sh on the instance for the full checks."
