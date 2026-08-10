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

    form.addEventListener("submit", function (submission) {
        submission.preventDefault();

        var email = document.getElementById("email").value.trim();
        var name = document.getElementById("name").value.trim();

        if (!email) return;

        // Disabled for the length of the request. A guest on venue wifi taps a button that
        // appears to do nothing and taps it again; without this that is two sign-ups racing.
        submit.disabled = true;
        submit.textContent = "Sending…";

        fetch("/api/join/" + encodeURIComponent(token), {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email: email, name: name || null })
        })
            .then(function (response) {
                if (response.status === 204) {
                    form.hidden = true;
                    problem.hidden = true;
                    done.hidden = false;
                    return;
                }

                return response.json().then(function (body) {
                    problem.textContent = (body && body.error) || "That could not be sent.";
                    problem.hidden = false;
                    submit.disabled = false;
                    submit.textContent = "Send me my photographs";
                });
            })
            .catch(function () {
                problem.textContent = "We could not reach the server. Check your connection and try again.";
                problem.hidden = false;
                submit.disabled = false;
                submit.textContent = "Send me my photographs";
            });
    });
})();
