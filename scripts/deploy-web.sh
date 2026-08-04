#!/usr/bin/env bash

# Builds the web application and puts it on the instance.
#
#     ./scripts/deploy-web.sh yellowtrack
#
# The argument is an ssh host, as deploy-server.sh takes. Apache serves the result as
# static files; there is no process to restart and nothing to roll back to, because a
# browser that has the old files will pick up the new ones on its next load.
#
# Deliberately separate from deploy-server.sh. The two move at different speeds — a
# client change does not need the API restarted, and an API change does not need 21MB
# re-uploaded — and coupling them would mean every server fix took the site down for the
# length of a wasm upload.

set -euo pipefail

HOST="${1:-}"
REMOTE_DIR="${REMOTE_DIR:-/var/www/yellowtrack}"

if [ -z "$HOST" ]; then
    echo "usage: $0 <ssh-host>    e.g. $0 yellowtrack" >&2
    exit 2
fi

cd "$(dirname "$0")/.."

echo "==> Building"
# The production distribution, not the development one: the development build ships
# unminified sources and is roughly three times the size for no benefit to anybody who is
# not debugging it.
./gradlew :webApp:wasmJsBrowserDistribution --console=plain -q

DIST="webApp/build/dist/wasmJs/productionExecutable"

if [ ! -f "$DIST/index.html" ]; then
    echo "no index.html in $DIST — the build produced nothing to deploy" >&2
    exit 1
fi

# Stated rather than assumed. Two wasm binaries make up most of it, and somebody watching
# this scroll past should know whether they are about to push 20MB or 200.
echo "==> Uploading $(du -sh "$DIST" | cut -f1) to $HOST:$REMOTE_DIR"

# --delete so a file dropped from the build is dropped from the server. A stale chunk left
# behind is worse here than on the server: the browser may still have it referenced.
rsync -az --delete "$DIST/" "$HOST:$REMOTE_DIR/"

echo "==> Checking it answers"
# Asks for the wasm rather than the page, because the page will serve happily from a
# misconfigured Apache and the wasm is what actually fails: without the right MIME type
# the browser refuses to compile it and the screen stays blank with an error only the
# console shows.
WASM=$(ssh "$HOST" "ls $REMOTE_DIR/*.wasm 2>/dev/null | head -1 | xargs -r basename" || true)

if [ -z "$WASM" ]; then
    echo "    no .wasm on the server — the upload did not land" >&2
    exit 1
fi

TYPE=$(ssh "$HOST" "curl -sS -o /dev/null -w '%{content_type}' -m 10 http://127.0.0.1/$WASM" || true)

case "$TYPE" in
    application/wasm*)
        echo "    $WASM is served as $TYPE"
        ;;
    *)
        echo "    $WASM is served as '${TYPE:-nothing}', not application/wasm." >&2
        echo "    The site will load and then fail to start. See docs/DEPLOYMENT.md." >&2
        exit 1
        ;;
esac

echo "Deployed."
