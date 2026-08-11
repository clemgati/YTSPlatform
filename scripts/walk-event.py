#!/usr/bin/env python3

"""
Runs one whole event against a deployment, the way a studio and a guest would.

    ./scripts/walk-event.py --guest you@yourdomain

Every step is an HTTP request a real client makes. Nothing here reaches the database, and
nothing is stubbed: the photograph is really uploaded to the object store, and the delivery
really goes through SES to the address you name. The last verification is your inbox, which
is the only part of this feature no test can perform.

This exists because the chain had a hole in it for three merges — `advanceSlot` had no route,
so every photograph routed to the event's gallery and delivery was unreachable, and nothing
failed anywhere. `EventEndToEndTest` now covers the same story in process. This covers what
that cannot: a real bucket, a real IAM role, a real mail server, a real domain.

It creates a throwaway studio and deletes it afterwards, whether or not the walk succeeded;
the purge removes it for good after the retention window. `--keep` leaves it behind, which is
worth doing when a walk fails and you want to look at what it left.

The studio's password is generated per run and printed nowhere. Nothing needs it after this
process exits, and `--keep` therefore leaves an account only its own purge can reach.
"""

import argparse
import base64
import json
import secrets
import sys
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone

# A valid 1x1 JPEG. Real bytes rather than a placeholder, because the point of step 8 is that
# the object store accepts what it is given.
PIXEL = base64.b64decode(
    "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a"
    "HBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAARCAABAAEDASIAAhEBAxEB/8QAHwAA"
    "AQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIh"
    "MUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpT"
    "VFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5"
    "usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/9oACAEBAAA/AL+iiigD//2Q=="
)

def new_password():
    """A fresh one per run.

    It used to be a constant in this file, which meant every walkthrough studio that
    outlived its run — and until this commit that was all of them — could be signed into by
    anybody who had read the repository. On a public API, holding one is holding an account.
    """
    return secrets.token_urlsafe(24)


class Failed(Exception):
    pass


def call(base, method, path, token=None, body=None, raw=None, content_type=None, expect=None):
    """One request. Returns (status, parsed-or-text)."""
    url = base.rstrip("/") + path
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)

    request = urllib.request.Request(url, data=data, method=method)
    if token:
        request.add_header("Authorization", "Bearer " + token)
    # The public endpoints are called by a browser, and a browser sends `Origin` on every
    # POST — including a same-origin one. Omitting it is why this script passed while the
    # sign-up page was refused with a bare 403 in production.
    if path.startswith("/api/"):
        request.add_header("Origin", base.rstrip("/"))
    if data is not None:
        request.add_header("Content-Type", content_type or "application/json")

    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            status, text = response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        status, text = error.code, error.read().decode()
    except urllib.error.URLError as error:
        raise Failed(f"{method} {path}: could not reach {base} — {error.reason}")

    parsed = None
    if text.strip():
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError:
            parsed = text

    if expect is not None and status not in expect:
        raise Failed(f"{method} {path}: expected {expect}, got {status} — {parsed}")

    return status, parsed


def step(number, description):
    print(f"\n{number:>2}. {description}")


def note(text):
    print(f"    {text}")


def main():
    parser = argparse.ArgumentParser(description="Run one event against a deployment.")
    parser.add_argument("--base", default="https://api.yellowtrackstudios.com",
                        help="the API. Use http://127.0.0.1:8080 on the instance itself.")
    parser.add_argument("--guest", required=True,
                        help="an address you can read. The delivery really goes here.")
    parser.add_argument("--studio-email", default=None,
                        help="the studio's reply address. Defaults to --guest.")
    # Opt out rather than opt in. It was opt in, and the result was a studio left on
    # production for every run anybody forgot the flag on — each one holding a live sign-up
    # code that a stranger could still scan.
    parser.add_argument("--keep", action="store_true",
                        help="leave the throwaway studio behind. Delete it yourself afterwards.")
    arguments = parser.parse_args()

    base = arguments.base
    guest = arguments.guest
    password = new_password()
    reply_to = arguments.studio_email or guest
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    stamp = datetime.now(timezone.utc).strftime("%H%M%S")

    print(f"Walking one event against {base}")
    print(f"The delivery will be sent to {guest}. This creates real data and sends real mail.")

    # 1 -----------------------------------------------------------------------------------
    step(1, "A studio signs up")
    email = f"walkthrough-{stamp}-{uuid.uuid4().hex[:6]}@yellowtrackstudios.com"
    _, session = call(base, "POST", "/auth/sign-up", body={
        "email": email, "password": password,
        "name": "Walkthrough", "studioName": "Walkthrough Studio",
    }, expect=[201])
    token, studio_id = session["token"], session["studioId"]
    note(f"{email}")
    note(f"studio {studio_id}")

    # The rest runs inside a try, so a studio outlives this script only when somebody
    # asked for that. A walk that fails in step 9 used to leave a studio behind holding a
    # live sign-up code on a public domain, and the failure that produced it was the
    # reason nobody was looking.
    try:
        walk(base, token, studio_id, guest, reply_to, now, stamp)
    finally:
        if arguments.keep:
            note(f"keeping {email} — delete it yourself when you are done with it")
        else:
            step(15, "Cleaning up")
            call(base, "POST", "/auth/delete-account", token=token,
                 body={"password": password}, expect=[200])
            note("the studio is marked deleted; the purge removes it after the retention window")

    print("\n" + "-" * 72)
    print("Every step the software can check has passed.")
    print()
    print(f"The one it cannot is now in {guest}: open it on a phone, follow the link, and")
    print("confirm the photograph appears. That is the whole feature, and nothing short of")
    print("reading that email proves it.")
    print()
    print("If the link 404s, PHOTOS_URL points somewhere this server does not serve.")
    print("If the image is broken, the presigned URL is wrong or the role cannot GetObject.")
    return 0


def walk(base, token, studio_id, guest, reply_to, now, stamp):
    """Steps 2 to 14. Everything after the studio exists and before it is torn down."""
    # 2 -----------------------------------------------------------------------------------
    step(2, "It fills in its details")
    note("A studio with no profile has no reply address, and delivery refuses — so this is")
    note("what the desktop application does when somebody saves Settings.")
    call(base, "POST", "/sync/changes", token=token, body={
        "studioProfiles": [{
            "id": str(uuid.uuid4()),
            "studioId": studio_id,
            "name": "Walkthrough Studio",
            "email": reply_to,
            "currency": "USD",
            "audit": {"createdAt": now, "updatedAt": now, "version": 1},
        }],
    }, expect=[200])
    note(f"reply-to {reply_to}")

    # 3 -----------------------------------------------------------------------------------
    step(3, "An event")
    _, created = call(base, "POST", "/events", token=token,
                      body={"name": f"Walkthrough {stamp}"}, expect=[201])
    event = created["id"]
    note(event)

    # 4 -----------------------------------------------------------------------------------
    step(4, "The code that goes on the banner")
    _, invite = call(base, "POST", f"/events/{event}/invite", token=token, expect=[200])
    note(invite["url"])
    note("^ this is PHOTOS_URL. If the host is wrong, every scan fails and nothing here says so.")

    # 5 -----------------------------------------------------------------------------------
    step(5, "Somebody scans it — no credential of any kind from here")
    _, scanned = call(base, "GET", f"/api/join/{invite['token']}", expect=[200])
    note(f"the page will say: {scanned['eventName']}")

    # 6 -----------------------------------------------------------------------------------
    step(6, "…and signs up")
    call(base, "POST", f"/api/join/{invite['token']}",
         body={"email": guest, "name": "Walkthrough Guest"}, expect=[204])
    note(f"{guest} is registered")

    # 7 -----------------------------------------------------------------------------------
    step(7, "The studio sees them")
    _, registrations = call(base, "GET", f"/events/{event}/registrations", token=token, expect=[200])
    registration = registrations[0]["id"]
    note(f"{registrations[0]['email']} ({registration})")

    # 8 -----------------------------------------------------------------------------------
    step(8, "A station, and that person seated at it")
    _, station = call(base, "POST", f"/events/{event}/stations", token=token,
                      body={"name": "Bay 1", "sourceKey": f"Camera {stamp}"}, expect=[201])
    _, slot = call(base, "POST", f"/events/{event}/stations/{station['id']}/advance", token=token,
                   body={"registrationId": registration}, expect=[201])
    note(f"station {station['id']}")
    note(f"sitting {slot['id']}")

    # 9 -----------------------------------------------------------------------------------
    step(9, "A photograph arrives from the watched folder")
    note("The first real use of the instance role's PutObject. A 503 here is S3 refusing.")
    captured = int(datetime.now(timezone.utc).timestamp() * 1000)
    # clientNow alongside capturedAt, read as late as possible. Both come from this
    # machine's clock; the server subtracts one from the other to cancel its error. The
    # first live run of this script lost its photograph to the gallery over 39ms of skew.
    client_now = int(datetime.now(timezone.utc).timestamp() * 1000)
    _, stored = call(
        base, "POST",
        f"/events/{event}/photographs?source=Camera%20{stamp}"
        f"&capturedAt={captured}&clientNow={client_now}",
        token=token, raw=PIXEL, content_type="image/jpeg", expect=[201],
    )
    if stored.get("registrationId") != registration:
        raise Failed(
            "the photograph did not route to the sitting — it went to the event's gallery, "
            "which is what happens when no slot is open"
        )
    note(f"photo {stored['photoId']} → this guest's sitting")

    # 10 ----------------------------------------------------------------------------------
    step(10, "The studio's list of sittings")
    _, sittings = call(base, "GET", f"/events/{event}/sittings", token=token, expect=[200])
    mine = next(s for s in sittings if s["id"] == slot["id"])
    note(f"{mine['email']} at {mine['stationName']}: {mine['photographs']} photograph(s), "
         f"{'open' if mine['closedAt'] is None else 'closed'}")

    # 11 ----------------------------------------------------------------------------------
    step(11, "Delivery is refused while the sitting is open")
    status, refusal = call(base, "POST", f"/events/{event}/sittings/{slot['id']}/deliver",
                           token=token, expect=[409])
    note(f"{status} {refusal['error']}")

    # 12 ----------------------------------------------------------------------------------
    step(12, "The photographer finishes")
    call(base, "POST", f"/events/{event}/stations/{station['id']}/close", token=token, expect=[204])
    note("station closed")

    # 13 ----------------------------------------------------------------------------------
    step(13, "And the studio hands it over")
    _, delivered = call(base, "POST", f"/events/{event}/sittings/{slot['id']}/deliver",
                        token=token, expect=[200])
    note(f"sent to {delivered['email']}: {delivered['photographs']} photograph(s)")

    # 14 ----------------------------------------------------------------------------------
    step(14, "Delivering again sends nothing")
    _, again = call(base, "POST", f"/events/{event}/sittings/{slot['id']}/deliver",
                    token=token, expect=[200])
    if again["sentNow"]:
        raise Failed("the second delivery sent another email")
    note("sentNow=false, as it should be")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Failed as failure:
        print(f"\nSTOPPED: {failure}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
