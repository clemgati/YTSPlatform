/*
 * The gallery page.
 *
 * The only thing it does is turn a list of URLs into images. It does not know the studio, the
 * event's other guests, or anything else — because the endpoint behind it does not either.
 */
(function () {
    "use strict";

    var token = window.location.pathname.split("/").pop();

    var eventLine = document.getElementById("event");
    var problem = document.getElementById("problem");
    var grid = document.getElementById("grid");
    var note = document.getElementById("note");

    function fail(message) {
        problem.textContent = message;
        problem.hidden = false;
        eventLine.hidden = true;
    }

    fetch("/api/gallery/" + encodeURIComponent(token), { headers: { Accept: "application/json" } })
        .then(function (response) {
            if (response.status === 404) {
                // Unknown, withdrawn, and nothing-released-yet are one answer on the server,
                // deliberately. Saying more here would undo that.
                fail("These photographs are not available. If the photographer has only just "
                    + "finished, try again shortly.");
                return null;
            }
            if (!response.ok) throw new Error("unavailable");
            return response.json();
        })
        .then(function (gallery) {
            if (!gallery) return;

            eventLine.textContent = gallery.eventName;

            gallery.photographs.forEach(function (url, index) {
                var link = document.createElement("a");
                link.href = url;
                link.target = "_blank";
                link.rel = "noopener noreferrer";

                var image = document.createElement("img");
                image.src = url;
                // Nothing here knows what is in the photograph, so the label says what it is
                // rather than pretending to describe it.
                image.alt = "Photograph " + (index + 1);
                image.loading = "lazy";
                image.decoding = "async";

                link.appendChild(image);
                grid.appendChild(link);
            });

            grid.hidden = false;
            note.hidden = false;
        })
        .catch(function () {
            fail("We could not reach the server. Check your connection and try again.");
        });
})();
