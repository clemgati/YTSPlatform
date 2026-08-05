#!/usr/bin/env bash

# Builds the web application and puts it on the instance.
#
#     ./scripts/deploy-web.sh yellowtrack https://app.yourdomain
#
# The first argument is an ssh host, as deploy-server.sh takes. The second is the origin
# the site will be served from, which is optional and only used to check that the API will
# accept requests from it — see the CORS check at the end. Apache serves the result as
# static files; there is no process to restart and nothing to roll back to, because a
# browser that has the old files will pick up the new ones on its next load.
#
# Deliberately separate from deploy-server.sh. The two move at different speeds — a
# client change does not need the API restarted, and an API change does not need 21MB
# re-uploaded — and coupling them would mean every server fix took the site down for the
# length of a wasm upload.

set -euo pipefail

HOST="${1:-}"
SITE_ORIGIN="${2:-}"
REMOTE_DIR="${REMOTE_DIR:-/var/www/yellowtrack}"

if [ -z "$HOST" ]; then
    echo "usage: $0 <ssh-host> [site-origin]" >&2
    echo "   e.g. $0 yellowtrack https://app.yourdomain" >&2
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

# Asked of the site's own vhost, not of whatever answers on 127.0.0.1.
#
# Apache serves by name. A request to the loopback address with no Host header lands on the
# first vhost — here, the API — which knows nothing about these files and returns its 404
# page. That reads as "served as text/html", which is indistinguishable from the MIME type
# being wrong, and is how this check failed a deployment that had worked.
#
# --resolve rather than the public name so it does not depend on the instance being able to
# reach itself by DNS, and https because that is where certbot redirects real traffic and
# so the vhost that actually serves anybody.
if [ -n "$SITE_ORIGIN" ]; then
    SITE_HOST=${SITE_ORIGIN#*://}
    SITE_HOST=${SITE_HOST%%/*}
    TYPE=$(
        ssh "$HOST" "curl -sS -o /dev/null -w '%{content_type}' -m 15 -L \
            --resolve '$SITE_HOST:443:127.0.0.1' 'https://$SITE_HOST/$WASM'" || true
    )
else
    echo "    no site origin given, so asking the default vhost — which may not be the site" >&2
    TYPE=$(ssh "$HOST" "curl -sS -o /dev/null -w '%{content_type}' -m 10 http://127.0.0.1/$WASM" || true)
fi

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

# Two things make a fresh web deployment fail, and this is the second. The MIME type above
# stops the application starting; this stops it reaching its own API — and it presents to
# the studio as "Could not reach the server", because a browser refuses a cross-origin
# request without letting the page see why. The API is perfectly healthy throughout, and
# the native clients are unaffected: they are not browsers and send no Origin.
if [ -z "$SITE_ORIGIN" ]; then
    echo "==> Skipping the CORS check: no site origin given"
    echo "    Pass it to have this checked: $0 $HOST https://app.yourdomain"
else
    echo "==> Checking the API accepts requests from $SITE_ORIGIN"

    API_URL=$(sed -n 's/^yellowtrack\.serverUrl=//p' gradle.properties | tail -1)

    if [ -z "$API_URL" ]; then
        echo "    yellowtrack.serverUrl is not in gradle.properties; cannot check" >&2
        exit 1
    fi

    # The preflight the sign-in POST actually triggers, rather than a plain GET: a simple
    # request can succeed while the preflight for a JSON body is refused.
    ALLOWED=$(
        curl -sS -o /dev/null -D - -m 10 -X OPTIONS \
            -H "Origin: $SITE_ORIGIN" \
            -H "Access-Control-Request-Method: POST" \
            -H "Access-Control-Request-Headers: content-type,authorization" \
            "$API_URL/health" 2>/dev/null |
            tr -d '\r' |
            sed -n 's/^[Aa]ccess-[Cc]ontrol-[Aa]llow-[Oo]rigin: //p'
    )

    if [ -z "$ALLOWED" ]; then
        echo "    $API_URL refused it: no Access-Control-Allow-Origin came back." >&2
        echo "    The site will load and then report 'Could not reach the server'." >&2
        echo >&2
        echo "    On the instance, add to /etc/yellowtrack/env:" >&2
        echo "        ALLOWED_ORIGINS=$SITE_ORIGIN" >&2
        echo "    then: sudo systemctl restart yellowtrack" >&2
        exit 1
    fi

    echo "    allowed as $ALLOWED"
fi

# The third way a deployment succeeds and the studio still runs the old application, and
# the only one that leaves no trace on the server at all: the files are new, Apache is
# healthy, and the browser never asks for them.
#
# Only the two big .wasm files carry a content hash. yellow-track-web.js keeps its name
# across every release, so a rule matching *.js and saying "immutable" pins the whole
# application in the browser for a year. index.html revalidates correctly and points at
# the same stale name, and the new wasm is never requested because it is referenced from
# inside the JavaScript that was not re-fetched.
#
# Checked here rather than trusted, because the vhost is edited by hand on the instance
# and nothing else would ever notice it had drifted.
if [ -n "$SITE_ORIGIN" ]; then
    echo "==> Checking the browser will notice the next release"

    ENTRY=$(ssh "$HOST" "ls $REMOTE_DIR/*.js 2>/dev/null | grep -v '\.map$' | head -1 | xargs -r basename" || true)

    if [ -z "$ENTRY" ]; then
        echo "    no .js on the server to check" >&2
    else
        CACHE=$(
            ssh "$HOST" "curl -sS -o /dev/null -D - -m 15 -L \
                --resolve '$SITE_HOST:443:127.0.0.1' 'https://$SITE_HOST/$ENTRY'" 2>/dev/null |
                tr -d '\r' |
                sed -n 's/^[Cc]ache-[Cc]ontrol: //p' |
                tail -1
        )

        # Read as a number rather than matched as text. The obvious globs are wrong in a
        # way that reads as right: "max-age=86400" does not contain "max-age=864" — the
        # digits carry on — so a pattern per suspicious duration silently passes most of
        # them. There is one rule, so ask it once.
        MAX_AGE=$(printf '%s' "$CACHE" | sed -n 's/.*max-age=\([0-9][0-9]*\).*/\1/p')

        # A few minutes is the most a file can be held without a release going unnoticed,
        # and immutable is unarguable: it tells the browser not to revalidate at all.
        if [ -z "$CACHE" ]; then
            echo "    $ENTRY carries no Cache-Control at all." >&2
            echo "    Apache is left to guess, and browsers guess generously. See" >&2
            echo "    docs/DEPLOYMENT.md for the <Directory> block." >&2
            exit 1
        elif [ "${CACHE#*immutable}" != "$CACHE" ] || [ "${MAX_AGE:-0}" -gt 300 ]; then
            echo "    $ENTRY is served as '$CACHE'." >&2
            echo >&2
            echo "    Its name does not change between releases, so this pins the whole" >&2
            echo "    application in the browser. Every studio that has opened the site" >&2
            echo "    keeps running the old one, and nothing here or on the instance" >&2
            echo "    reports anything wrong." >&2
            echo >&2
            echo "    Fix the <Directory> block in the web vhost — docs/DEPLOYMENT.md." >&2
            exit 1
        fi

        echo "    $ENTRY is served as '$CACHE'"
    fi
fi

echo "Deployed."
