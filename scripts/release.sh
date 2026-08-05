#!/usr/bin/env bash

# Builds and deploys everything a studio can install or open, in one command.
#
#     ./scripts/release.sh yellowtrack https://app.yellowtrackstudios.com
#
# The web application is built here and pushed straight to the instance. The three desktop
# installers are built by CI, because jpackage only emits the format of the machine it runs
# on: a .msi has to be built on Windows however capable this laptop is.
#
# So CI is started first and the web deployment happens while it works. That is not an
# optimisation for its own sake — the CI half takes ten to fifteen minutes and the web half
# takes two, and running them in sequence would mean staring at a progress line for most of
# it.
#
#     ./scripts/release.sh yellowtrack https://app.yourdomain --dry-run
#
# prints what it would do and touches nothing.
#
# The server is not deployed here. It moves at a different speed to its clients, and a
# release that restarted the API every time somebody fixed a label would take the studio
# offline for no reason. Use deploy-server.sh.

set -euo pipefail

HOST="${1:-}"
SITE_ORIGIN="${2:-}"
DRY_RUN=false

for arg in "$@"; do
    [ "$arg" = "--dry-run" ] && DRY_RUN=true
done

if [ -z "$HOST" ] || [ -z "$SITE_ORIGIN" ]; then
    echo "usage: $0 <ssh-host> <site-origin> [--dry-run]" >&2
    echo "   e.g. $0 yellowtrack https://app.yourdomain" >&2
    exit 2
fi

cd "$(dirname "$0")/.."

STARTED=$SECONDS
STEP=0
TOTAL=5

step() {
    STEP=$((STEP + 1))
    printf '\n[%d/%d] %s  (%dm%02ds elapsed)\n' "$STEP" "$TOTAL" "$1" $(((SECONDS - STARTED) / 60)) $(((SECONDS - STARTED) % 60))
}

run() {
    if $DRY_RUN; then
        echo "       would run: $*"
    else
        "$@"
    fi
}

# --- 1. Refuse to release something that is not what is committed -------------------------
#
# CI builds a commit and this machine builds a working tree, and nothing downstream would
# notice they had diverged. The installers and the web application would then be two
# different versions of the application, deployed a minute apart, with the same name.

step "Checking the working tree"

if [ -n "$(git status --porcelain)" ]; then
    echo "       uncommitted changes. CI builds a commit and this builds a working tree —" >&2
    echo "       releasing now would ship two different versions under one name." >&2
    git status --short >&2
    exit 1
fi

BRANCH=$(git rev-parse --abbrev-ref HEAD)
SHA=$(git rev-parse HEAD)

if ! git rev-parse --quiet --verify "origin/$BRANCH" >/dev/null 2>&1 ||
    [ "$(git rev-parse "origin/$BRANCH")" != "$SHA" ]; then
    echo "       $BRANCH is not pushed, or origin is behind. CI can only build what it can see." >&2
    echo "       git push origin $BRANCH" >&2
    exit 1
fi

echo "       $BRANCH at ${SHA:0:8}, clean and pushed"

# --- 2. Start CI, because it is the long pole --------------------------------------------

step "Starting the installer build on CI"

if $DRY_RUN; then
    echo "       would run: gh workflow run Release --ref $BRANCH"
    RUN_ID="(dry-run)"
else
    # Recorded before triggering so the run we then look for cannot be an older one that
    # happened to be in flight.
    BEFORE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    gh workflow run Release --ref "$BRANCH"

    RUN_ID=""
    for _ in $(seq 1 20); do
        sleep 3
        RUN_ID=$(
            gh run list --workflow Release --branch "$BRANCH" --limit 5 \
                --json databaseId,createdAt,headSha \
                --jq "[.[] | select(.headSha == \"$SHA\" and .createdAt > \"$BEFORE\")] | .[0].databaseId // empty"
        )
        [ -n "$RUN_ID" ] && break
    done

    if [ -z "$RUN_ID" ]; then
        echo "       the run did not appear. Check the Actions tab." >&2
        exit 1
    fi

    echo "       run $RUN_ID building macOS, Windows and Linux"
fi

# --- 3. The web application, while CI works ----------------------------------------------

step "Building and deploying the web application"

run ./scripts/deploy-web.sh "$HOST" "$SITE_ORIGIN"

# --- 4. Wait for the installers ----------------------------------------------------------

step "Waiting for the installers"

if $DRY_RUN; then
    echo "       would run: gh run watch \$RUN_ID"
else
    echo "       three runners, usually ten to fifteen minutes."

    # --exit-status so a failed leg stops the release rather than being deployed around.
    if ! gh run watch "$RUN_ID" --exit-status; then
        echo >&2
        echo "       the installer build failed. Nothing further is deployed; the web" >&2
        echo "       application above is already live and is unaffected." >&2
        echo "       gh run view $RUN_ID --log-failed" >&2
        exit 1
    fi
fi

# --- 5. Publish the installers -----------------------------------------------------------

step "Publishing the installers"

run ./scripts/deploy-installers.sh "$HOST" "$RUN_ID" "$SITE_ORIGIN"

printf '\nDone in %dm%02ds.\n' $(((SECONDS - STARTED) / 60)) $(((SECONDS - STARTED) % 60))
echo "  web        $SITE_ORIGIN"
echo "  installers $SITE_ORIGIN/downloads/"
echo
echo "Neither desktop installer is signed, so both operating systems warn on first run."
