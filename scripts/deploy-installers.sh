#!/usr/bin/env bash

# Puts the desktop installers on the instance, beside the web application.
#
#     ./scripts/deploy-installers.sh yellowtrack
#     ./scripts/deploy-installers.sh yellowtrack 12345678901 https://app.yourdomain
#
# The first argument is an ssh host, as the other deploy scripts take. The second is a
# Release workflow run to take the installers from; without it, the most recent successful
# one is used.
#
# Run from your machine rather than from CI, and deliberately. Uploading from a GitHub
# runner would mean an ssh key with write access to the instance living in a public
# repository's secrets, and port 22 being open to GitHub's address ranges rather than to
# one address — which is most of the point of restricting it.
#
# So: CI builds the three installers, this fetches them, and the machine that already has
# ssh access is the one that pushes.

set -euo pipefail

HOST="${1:-}"
RUN_ID="${2:-}"
# Optional, and only used to ask the right vhost in the check at the end.
SITE_ORIGIN="${3:-}"

# Not under /var/www/yellowtrack. deploy-web.sh rsyncs that directory with --delete, so
# anything of ours living inside it disappears the next time the site is deployed.
REMOTE_DIR="${REMOTE_DIR:-/var/www/yellowtrack-downloads}"

if [ -z "$HOST" ]; then
    echo "usage: $0 <ssh-host> [run-id]    e.g. $0 yellowtrack" >&2
    exit 2
fi

cd "$(dirname "$0")/.."

if [ -z "$RUN_ID" ]; then
    echo "==> Finding the most recent successful Release run"
    RUN_ID=$(gh run list --workflow Release --status success --limit 1 --json databaseId --jq '.[0].databaseId')

    if [ -z "$RUN_ID" ] || [ "$RUN_ID" = "null" ]; then
        echo "    none found. Run the Release workflow first, from the Actions tab or a tag." >&2
        exit 1
    fi
fi

STAGING=$(mktemp -d)
trap 'rm -rf "$STAGING"' EXIT

# Said before it starts, because gh run download prints nothing at all while it works and
# this is around 200MB: three installers, each most of a JVM runtime. Without the size on
# screen first, a slow connection is indistinguishable from a hung script.
SIZE=$(
    gh api "repos/{owner}/{repo}/actions/runs/$RUN_ID/artifacts" \
        --jq '[.artifacts[].size_in_bytes] | add / 1048576 | floor' 2>/dev/null || true
)

echo "==> Downloading ${SIZE:-?}MB of installers from run $RUN_ID"
echo "    gh prints nothing until it finishes; give it a few minutes."

# GitHub zips artefacts on the way out; `gh run download` unpacks them, so what lands here
# is the .dmg, .msi and .deb themselves rather than three zips. The zipping saves almost
# nothing — a .dmg and a .msi are compressed already.
gh run download "$RUN_ID" --dir "$STAGING"

# Flattened: the artefacts arrive in a directory each, and a download page is easier to
# write — and a URL easier to give somebody — when they are side by side.
mkdir -p "$STAGING/flat"
find "$STAGING" -type f \( -name '*.dmg' -o -name '*.msi' -o -name '*.deb' \) -exec mv {} "$STAGING/flat/" \;

# The package name has a space in it, so jpackage produces "Yellow Track-1.0.0.dmg". That
# is right on a desktop and wrong in a URL, where it becomes %20 in some places and a
# broken link in others. Renamed once, here, rather than encoded everywhere it is written.
for f in "$STAGING"/flat/*; do
    [ -e "$f" ] || continue
    safe=$(basename "$f" | tr '[:upper:] ' '[:lower:]-')
    [ "$safe" = "$(basename "$f")" ] || mv "$f" "$STAGING/flat/$safe"
done

COUNT=$(find "$STAGING/flat" -type f | wc -l | tr -d ' ')

if [ "$COUNT" -eq 0 ]; then
    echo "    run $RUN_ID has no installers attached" >&2
    exit 1
fi

echo "    got $COUNT:"
(cd "$STAGING/flat" && ls -lh | tail -n +2 | awk '{print "      " $NF " (" $5 ")"}')

if [ "$COUNT" -ne 3 ]; then
    # Reported rather than refused. Two installers are worth publishing while the third
    # platform is being fixed; silently publishing two as though they were three is not.
    echo "    note: expected 3 (macOS, Windows, Linux)" >&2
fi

# A page rather than a directory listing, so the vhost can keep Options -Indexes and so
# there is somewhere to say the installers are unsigned. Somebody who meets "the developer
# cannot be verified" without being told to expect it concludes the download is broken.
echo "==> Writing the download page"
{
    cat <<'HEAD'
<!doctype html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Yellow Track — downloads</title>
<style>
  body { background:#141414; color:#e6e6e6; font:16px/1.6 system-ui, sans-serif;
         margin:0 auto; padding:2.5rem 1.25rem; max-width:34rem; }
  h1 { font-size:1.5rem; margin:0 0 .25rem; }
  a { color:#fabb18; }
  ul { list-style:none; padding:0; }
  li { border-top:1px solid #2a2a2a; padding:.85rem 0; }
  .size { color:#9a9a9a; font-size:.875rem; }
  .warn { border-left:3px solid #fabb18; padding-left:1rem; color:#c9c9c9; font-size:.9375rem; }
</style>
<h1>Yellow Track</h1>
<p class="size">Desktop installers</p>
<ul>
HEAD

    for f in "$STAGING"/flat/*; do
        name=$(basename "$f")
        size=$(du -h "$f" | cut -f1)
        case "$name" in
            *.dmg) label="macOS" ;;
            *.msi) label="Windows" ;;
            *.deb) label="Linux (Debian, Ubuntu)" ;;
            *)     label="$name" ;;
        esac
        printf '  <li><a href="%s">%s</a> <span class="size">%s</span></li>\n' "$name" "$label" "$size"
    done

    cat <<'FOOT'
</ul>
<p class="warn">
  These are not signed. macOS will say the developer cannot be verified — right-click the
  application and choose Open the first time. Windows will call it unrecognised — choose
  More info, then Run anyway.
</p>
<p class="size">
  Nothing to install: <a href="/">use it in this browser</a>.
</p>
FOOT
} > "$STAGING/page.html"

# Written outside the directory and moved in. Redirecting straight into it creates the file
# before the loop above reads the glob, and the page then lists itself as a download.
mv "$STAGING/page.html" "$STAGING/flat/index.html"

echo "==> Uploading to $HOST:$REMOTE_DIR"
# --delete so last release's installers go rather than accumulating under names nobody
# will ever link to again.
#
# No -z, unlike the other two deploy scripts. Installers are compressed archives already,
# so compressing them again spends CPU at both ends to send the same number of bytes; on a
# small instance that is slower rather than faster. --progress because this is another
# couple of hundred megabytes and silence reads as a hang.
rsync -a --progress --delete "$STAGING/flat/" "$HOST:$REMOTE_DIR/"

echo "==> Checking they are served"
FIRST=$(cd "$STAGING/flat" && ls *.dmg *.msi *.deb 2>/dev/null | head -1 || true)

if [ -n "$FIRST" ]; then
    # The site's own vhost, for the reason deploy-web.sh gives: a request to the loopback
    # with no Host header lands on the first vhost, which knows nothing about /downloads.
    if [ -n "$SITE_ORIGIN" ]; then
        SITE_HOST=${SITE_ORIGIN#*://}
        SITE_HOST=${SITE_HOST%%/*}
        CODE=$(
            ssh "$HOST" "curl -sS -o /dev/null -w '%{http_code}' -m 20 -L \
                --resolve '$SITE_HOST:443:127.0.0.1' 'https://$SITE_HOST/downloads/$FIRST'" || true
        )
    else
        CODE=$(ssh "$HOST" "curl -sS -o /dev/null -w '%{http_code}' -m 20 'http://127.0.0.1/downloads/$FIRST'" || true)
    fi

    case "$CODE" in
        200)
            echo "    /downloads/$FIRST answers 200"
            ;;
        *)
            echo "    /downloads/$FIRST answered '${CODE:-nothing}'." >&2
            echo "    The Alias is probably missing from the vhost — see docs/DEPLOYMENT.md." >&2
            exit 1
            ;;
    esac
fi

echo "Deployed."
