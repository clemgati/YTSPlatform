/*
 * The sign-up page.
 *
 * Nothing here writes markup. Every value that came from a server or a person is set with
 * textContent, so an event named `<img onerror=...>` is an event with a peculiar name rather
 * than an incident.
 */
(function () {
    "use strict";

    var token = window.location.pathname.split("/").pop();

    var eventLine = document.getElementById("event");
    var problem = document.getElementById("problem");
    var form = document.getElementById("form");
    var done = document.getElementById("done");
    var submit = document.getElementById("submit");

    function fail(message) {
        problem.textContent = message;
        problem.hidden = false;
        form.hidden = true;
        eventLine.hidden = true;
    }

    fetch("/api/join/" + encodeURIComponent(token), { headers: { Accept: "application/json" } })
        .then(function (response) {
            if (response.status === 404) {
                // The same words a withdrawn code gets, because the server deliberately
                // answers both identically and the page must not undo that.
                fail("This sign-up is not open. Ask the photographer for a current code.");
                return null;
            }
            if (!response.ok) throw new Error("unavailable");
            return response.json();
        })
        .then(function (event) {
            if (!event) return;
            eventLine.textContent = event.eventName;
            form.hidden = false;
        })
        .catch(function () {
            fail("We could not reach the server. Check your connection and try again.");
        });

    function refuse(message) {
        problem.textContent = message;
        problem.hidden = false;
        submit.disabled = false;
        submit.textContent = "Send me my photographs";
    }

    form.addEventListener("submit", function (submission) {
        submission.preventDefault();

        var email = document.getElementById("email").value.trim();
        var givenName = document.getElementById("given-name").value.trim();
        var familyName = document.getElementById("family-name").value.trim();
        var phone = document.getElementById("phone").value.trim();

        // The browser enforces `required` on its own; this is the same rule for a browser
        // that did not, which is every one of them with scripting quirks and a few of them
        // with autofill.
        if (!email || !givenName || !familyName) return;

        // Disabled for the length of the request. A guest on venue wifi taps a button that
        // appears to do nothing and taps it again; without this that is two sign-ups racing.
        submit.disabled = true;
        submit.textContent = "Sending…";

        fetch("/api/join/" + encodeURIComponent(token), {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                email: email,
                givenName: givenName,
                familyName: familyName,
                phone: phone || null
            })
        })
            .then(function (response) {
                if (response.status === 204) {
                    form.hidden = true;
                    problem.hidden = true;
                    done.hidden = false;
                    return;
                }

                // A refusal may carry no body at all — a proxy or a CORS rejection answers
                // with nothing. Reading it as JSON then throws, and the old code let that
                // fall into the network handler below, so a 403 was reported as "we could
                // not reach the server". It took a packet capture to find out otherwise.
                return response.text().then(function (text) {
                    var message = "That could not be sent.";
                    try {
                        var body = JSON.parse(text);
                        if (body && body.error) message = body.error;
                    } catch (ignored) {
                        // Not JSON. Say what happened rather than inventing a cause.
                        message = "The server refused that (" + response.status + "). Please tell the photographer.";
                    }
                    refuse(message);
                });
            })
            .catch(function () {
                // Only a request that never got an answer reaches here now.
                refuse("We could not reach the server. Check your connection and try again.");
            });
    });
})();
