package com.yellowtrack.platform.server

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.yellowtrack.platform.core.model.auth.DeleteAccountRequest
import com.yellowtrack.platform.core.model.auth.SessionResponse
import com.yellowtrack.platform.core.model.auth.SignUpRequest
import com.yellowtrack.platform.core.model.event.CreateEventRequest
import com.yellowtrack.platform.core.model.event.CreatedResponse
import com.yellowtrack.platform.core.model.event.EventInviteResponse
import com.yellowtrack.platform.core.model.event.EventSummary
import com.yellowtrack.platform.core.model.event.InvitedEventResponse
import com.yellowtrack.platform.core.model.event.QrMatrix
import com.yellowtrack.platform.core.model.event.RegistrationSummary
import com.yellowtrack.platform.core.model.event.SignUpToEventRequest
import com.yellowtrack.platform.server.event.EventInvites
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The first surface here anybody may reach without a session.
 *
 * Most of these tests are about what it refuses to say. A photograph delivery service that
 * answers "is this person signed up for that event" has leaked something no amount of
 * encryption elsewhere makes up for, and the obvious implementation answers it by accident.
 */
class EventInviteTest {
    // -- Issuing --------------------------------------------------------------------------

    @Test
    fun `an invite is issued with a url a code can carry`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")

            val invite = client.invite(session, event)

            assertTrue(invite.token.isNotBlank())
            assertTrue(invite.url.endsWith("/join/${invite.token}"), invite.url)
        }

    /**
     * Asking twice gives one code.
     *
     * A second would silently orphan whichever banner was printed from the first, and nobody
     * would find out until an event where half the codes did nothing.
     */
    @Test
    fun `issuing twice returns the same invite`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")

            assertEquals(client.invite(session, event).token, client.invite(session, event).token)
        }

    /** Two events must not share a code, or a sign-up lands on the wrong gallery. */
    @Test
    fun `two events get different invites`() =
        withServer { client ->
            val session = client.signUp()

            val first = client.invite(session, client.createEvent(session, "Morning"))
            val second = client.invite(session, client.createEvent(session, "Afternoon"))

            assertNotEquals(first.token, second.token)
        }

    @Test
    fun `issuing an invite refuses a caller with no token`() =
        withServer { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.post("/events/any/invite").status)
        }

    // -- What the public may see -----------------------------------------------------------

    @Test
    fun `scanning a code shows the event name`() =
        withServer { client ->
            val session = client.signUp()
            val invite = client.invite(session, client.createEvent(session, "Harbour Awards 2026"))

            val response = client.get("/api/join/${invite.token}")

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals(
                "Harbour Awards 2026",
                apiJson.decodeFromString<InvitedEventResponse>(response.bodyAsText()).eventName,
            )
        }

    /**
     * The name, and nothing else.
     *
     * A public endpoint should return what the page needs to say rather than a row. Anything
     * more here is a studio's operational detail handed to whoever photographed a banner.
     */
    @Test
    fun `scanning a code reveals nothing but the name`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            client.openStation(session, event, "Bay 1", "Camera A")
            val invite = client.invite(session, event)

            val body = client.get("/api/join/${invite.token}").bodyAsText()

            assertFalse(event in body, "the event identifier was exposed: $body")
            assertFalse("Camera A" in body, "a station's source was exposed: $body")
            assertFalse("Bay 1" in body, "a station was exposed: $body")
            assertFalse("studio" in body.lowercase(), "something about the studio was exposed: $body")
        }

    /** A token nobody ever issued. */
    @Test
    fun `an unknown code is not open`() =
        withServer { client ->
            assertEquals(HttpStatusCode.NotFound, client.get("/api/join/not-a-real-token").status)
        }

    /**
     * A withdrawn code and one that never existed must be indistinguishable.
     *
     * Telling them apart confirms that a particular code once existed, which is a fact about
     * a studio's events that a stranger holding an old banner should not be able to check.
     */
    @Test
    fun `a withdrawn code answers exactly as an unknown one does`() =
        withServer { client ->
            val session = client.signUp()
            val invite = client.invite(session, client.createEvent(session, "Harbour Awards 2026"))
            client.post("/events/any/invite/revoke") { bearerAuth(session.token) }

            val event = client.createEvent(session, "Withdrawn")
            val withdrawn = client.invite(session, event)
            client.post("/events/$event/invite/revoke") { bearerAuth(session.token) }

            val unknown = client.get("/api/join/not-a-real-token")
            val revoked = client.get("/api/join/${withdrawn.token}")

            assertEquals(unknown.status, revoked.status)
            assertEquals(unknown.bodyAsText(), revoked.bodyAsText())
            // And the one still live is unaffected.
            assertEquals(HttpStatusCode.OK, client.get("/api/join/${invite.token}").status)
        }

    /** Withdrawing then issuing again gives a new code, so an old banner stays dead. */
    @Test
    fun `an invite issued after a revocation is a different code`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val first = client.invite(session, event)

            client.post("/events/$event/invite/revoke") { bearerAuth(session.token) }
            val second = client.invite(session, event)

            assertNotEquals(first.token, second.token)
            assertEquals(HttpStatusCode.NotFound, client.get("/api/join/${first.token}").status)
            assertEquals(HttpStatusCode.OK, client.get("/api/join/${second.token}").status)
        }

    // -- Signing up --------------------------------------------------------------------------

    @Test
    fun `somebody can sign up with an address`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            val response = client.join(invite.token, "guest@example.test", "Ada Okafor")

            assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
            assertEquals(1, registrationCount(event))
        }

    /**
     * The one that stops this becoming a lookup service.
     *
     * Signing up an address that is already there must answer exactly as a new one does. If
     * it did not, anybody with a banner could ask whether a named person attended — which is
     * a question about somebody's whereabouts, asked of a system that photographs people.
     */
    @Test
    fun `signing up twice answers exactly as signing up once does`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            val first = client.join(invite.token, "guest@example.test")
            val second = client.join(invite.token, "guest@example.test")

            assertEquals(first.status, second.status)
            assertEquals(first.bodyAsText(), second.bodyAsText())
            assertEquals(1, registrationCount(event), "the second sign-up created a duplicate")
        }

    /** Nothing about the registration comes back — it is what the studio binds photographs to. */
    @Test
    fun `signing up returns no body at all`() =
        withServer { client ->
            val session = client.signUp()
            val invite = client.invite(session, client.createEvent(session, "Harbour Awards 2026"))

            val response = client.join(invite.token, "guest@example.test")

            assertEquals("", response.bodyAsText())
        }

    @Test
    fun `an address that could never receive anything is refused`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            listOf("", "   ", "not-an-address", "@example.test", "guest@", "guest@example", "a b@example.test")
                .forEach { bad ->
                    assertEquals(
                        HttpStatusCode.BadRequest,
                        client.join(invite.token, bad).status,
                        "should have refused: '$bad'",
                    )
                }

            assertEquals(0, registrationCount(event))
        }

    @Test
    fun `signing up against an unknown code is not open`() =
        withServer { client ->
            assertEquals(HttpStatusCode.NotFound, client.join("not-a-real-token", "guest@example.test").status)
        }

    // -- As a browser actually sends it ----------------------------------------------------------

    /**
     * A browser sends `Origin` on every POST, including a same-origin one.
     *
     * This is the request the sign-up page makes, and it was refused in production with a
     * bare 403: `ALLOWED_ORIGINS` was set for the web build, which installs CORS, and the
     * photographs host was not in it — so the page served by this application had its own
     * form submission rejected as cross-origin.
     *
     * Nothing caught it. curl sends no `Origin`, so `walk-event.py` and every probe in this
     * repository exercised a request no browser makes.
     */
    @Test
    fun `a sign-up from the public site's own origin is accepted`() =
        withCors { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            val response =
                client.post("/api/join/${invite.token}") {
                    header(HttpHeaders.Origin, "https://yellowtrackphotos.com")
                    contentType(ContentType.Application.Json)
                    setBody(apiJson.encodeToString(SignUpToEventRequest("guest@example.test", null)))
                }

            assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
            assertEquals(1, registrationCount(event))
        }

    /** And reading the event's name, which the page does first. */
    @Test
    fun `reading an event from the public site's own origin is accepted`() =
        withCors { client ->
            val session = client.signUp()
            val invite = client.invite(session, client.createEvent(session, "Harbour Awards 2026"))

            val response =
                client.get("/api/join/${invite.token}") {
                    header(HttpHeaders.Origin, "https://yellowtrackphotos.com")
                }

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        }

    /**
     * The `www` host of the public site, which is a different origin to a browser.
     *
     * Found by the walkthrough against production: `www.yellowtrackphotos.com` serves the
     * sign-up page and the apex does too, so a guest reaching it either way gets the same
     * page — and only one of them could sign up. The other typed an address, pressed the
     * button and was told the server could not be reached, which is the same failure this
     * CORS block was added to fix and was still live on the host half the world types.
     *
     * Allowed as a named subdomain rather than by loosening the rule: `www` of the site this
     * application serves, and nothing else.
     */
    @Test
    fun `a sign-up from the public site's www host is accepted`() =
        withCors { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            val response =
                client.post("/api/join/${invite.token}") {
                    header(HttpHeaders.Origin, "https://www.yellowtrackphotos.com")
                    contentType(ContentType.Application.Json)
                    setBody(apiJson.encodeToString(SignUpToEventRequest("guest@example.test")))
                }

            assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
        }

    /** And reading it, which is the request that actually failed in production. */
    @Test
    fun `reading an event from the public site's www host is accepted`() =
        withCors { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            val response =
                client.get("/api/join/${invite.token}") {
                    header(HttpHeaders.Origin, "https://www.yellowtrackphotos.com")
                }

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        }

    /**
     * And only `www`.
     *
     * Written because widening the allowance to every subdomain of the photographs host
     * passed every other test here. The unrelated-origin test uses a different host
     * altogether, so it says nothing about how far this one reaches.
     */
    @Test
    fun `a sign-up from another subdomain of the public site is refused`() =
        withCors { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            val response =
                client.get("/api/join/${invite.token}") {
                    header(HttpHeaders.Origin, "https://anything.yellowtrackphotos.com")
                }

            assertNotEquals(HttpStatusCode.OK, response.status, "an arbitrary subdomain was allowed")
        }

    /** Somebody else's page must still not post to it. */
    @Test
    fun `a sign-up from an unrelated origin is refused`() =
        withCors { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            val response =
                client.post("/api/join/${invite.token}") {
                    header(HttpHeaders.Origin, "https://not-us.example.test")
                    contentType(ContentType.Application.Json)
                    setBody(apiJson.encodeToString(SignUpToEventRequest("guest@example.test", null)))
                }

            assertEquals(HttpStatusCode.Forbidden, response.status, response.bodyAsText())
            assertEquals(0, registrationCount(event), "a stranger's page signed somebody up")
        }

    // -- One address is one person -------------------------------------------------------------

    /**
     * A second scan with a different name is the same person, renamed.
     *
     * This cost an afternoon of hunting. Four people were signed up from two email addresses;
     * every sign-up answered "You are signed up", and the studio's list kept showing the first
     * two names. Nothing was broken — one address is one person — but the name was silently
     * discarded, which reads as the software losing people.
     */
    @Test
    fun `signing up again with a name replaces the one held`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            client.join(invite.token, "guest@example.test", "James")
            client.join(invite.token, "guest@example.test", "Ralph")

            val people = client.registrations(session, event)

            assertEquals(1, people.size, "one address must remain one person")
            assertEquals("Ralph", people.single().name, "the corrected name was discarded")
        }

    /**
     * And a scan with no name leaves the held one alone.
     *
     * Absence is not a correction. Wiping a name because somebody was in a hurry the second
     * time is the same mistake pointing the other way.
     */
    @Test
    fun `signing up again without a name keeps the one held`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            client.join(invite.token, "guest@example.test", "James")
            client.join(invite.token, "guest@example.test", null)

            assertEquals("James", client.registrations(session, event).single().name)
        }

    /** Two addresses are two people, however similar the names. */
    @Test
    fun `two addresses remain two people`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            client.join(invite.token, "one@example.test", "James")
            client.join(invite.token, "two@example.test", "James")

            assertEquals(2, client.registrations(session, event).size)
        }

    // -- Not a way to fill somebody's database ------------------------------------------------

    /**
     * A code on a banner is public, so the volume it can take has to be bounded somewhere.
     *
     * The cap is on the event rather than on the caller: behind a venue's wifi everybody
     * arrives from a handful of addresses, so a per-caller limit refuses a real queue and
     * barely inconveniences a script.
     */
    @Test
    fun `an event stops taking sign-ups past its limit`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            // A limit of two, so this is a test rather than a thousand HTTP requests.
            val invites = EventInvites(TestDatabase.database, limit = 2)

            assertEquals(null, invites.signUp(invite.token, "one@example.test"))
            assertEquals(null, invites.signUp(invite.token, "two@example.test"))

            assertEquals(
                com.yellowtrack.platform.server.event.SignUpRefused.TooManyForNow,
                invites.signUp(invite.token, "three@example.test"),
            )
            assertEquals(2, registrationCount(event))
        }

    /** One event filling up must not close another's sign-up. */
    @Test
    fun `the limit is per event`() =
        withServer { client ->
            val session = client.signUp()
            val busy = client.createEvent(session, "Busy")
            val quiet = client.createEvent(session, "Quiet")
            val busyInvite = client.invite(session, busy)
            val quietInvite = client.invite(session, quiet)

            val invites = EventInvites(TestDatabase.database, limit = 1)
            invites.signUp(busyInvite.token, "one@example.test")

            assertEquals(
                com.yellowtrack.platform.server.event.SignUpRefused.TooManyForNow,
                invites.signUp(busyInvite.token, "two@example.test"),
            )
            assertEquals(null, invites.signUp(quietInvite.token, "three@example.test"), "another event was closed too")
        }

    // -- Tokens ---------------------------------------------------------------------------------

    /**
     * The token is the whole of the secret, so it has to be worth being one.
     *
     * Not an assertion about randomness — a test cannot prove that — but about the two things
     * that go wrong in practice: a token short enough to guess, and a generator that returns
     * the same thing twice.
     */
    @Test
    fun `tokens are long and do not repeat`() {
        val tokens = List(500) { EventInvites.randomToken() }

        assertEquals(500, tokens.toSet().size, "a token repeated")
        assertTrue(tokens.all { it.length >= 20 }, "tokens are shorter than expected: ${tokens.first()}")
        // The message is built from the offenders rather than by searching for one: an
        // `assertTrue` message is evaluated whether or not the assertion holds, and the first
        // version threw NoSuchElementException precisely when every token was fine.
        val unusable = tokens.filterNot { token -> token.all { it.isLetterOrDigit() || it == '-' || it == '_' } }

        assertTrue(unusable.isEmpty(), "these tokens would not survive a URL: ${unusable.take(3)}")
    }

    /** One studio must not be able to withdraw another's invite. */
    @Test
    fun `one studio cannot revoke another studio's invite`() =
        withServer { client ->
            val harbourline = client.signUp()
            val other = client.signUp()
            val event = client.createEvent(harbourline, "Harbour Awards 2026")
            val invite = client.invite(harbourline, event)

            client.post("/events/$event/invite/revoke") { bearerAuth(other.token) }

            assertEquals(
                HttpStatusCode.OK,
                client.get("/api/join/${invite.token}").status,
                "another studio withdrew an invite it cannot see",
            )
        }

    /**
     * A studio must not be able to issue an invite for somebody else's event.
     *
     * The token it got back would be useless — the lookup re-enters *its* studio scope and
     * row level security hides the other studio's event — but the row would take the one live
     * invite slot for that event, and the studio that owns it could then never issue its own.
     * A cross-tenant denial of service, arriving as a unique-index violation on somebody
     * else's screen.
     */
    @Test
    fun `one studio cannot issue an invite for another studio's event`() =
        withServer { client ->
            val harbourline = client.signUp()
            val other = client.signUp()
            val event = client.createEvent(harbourline, "Harbour Awards 2026")

            val stolen = client.post("/events/$event/invite") { bearerAuth(other.token) }

            val body = stolen.bodyAsText()

            assertNotEquals(HttpStatusCode.OK, stolen.status, "another studio issued an invite: $body")

            // And the owner can still issue its own.
            val mine = client.invite(harbourline, event)
            assertEquals(HttpStatusCode.OK, client.get("/api/join/${mine.token}").status)
        }

    // -- A studio on its way out --------------------------------------------------------

    /**
     * Deleting the account closes the sign-up pages with it.
     *
     * Found by running the walkthrough against production: the studio it created was deleted
     * in its own last step, and its code went on answering. Deletion is a mark and a purge
     * thirty days later, and for those thirty days a stranger could still scan the code, hand
     * over an address, and be told photographs were coming. They were not — the purge destroys
     * everything — so it collected personal data for an account being erased and lied to the
     * person who gave it.
     *
     * The lookup checked the event was not deleted and never asked about the studio.
     */
    @Test
    fun `a code stops working when the studio deletes its account`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            assertEquals(HttpStatusCode.OK, client.get("/api/join/${invite.token}").status)

            client.deleteAccount(session)

            assertEquals(HttpStatusCode.NotFound, client.get("/api/join/${invite.token}").status)
        }

    /**
     * And says exactly what an unknown code says.
     *
     * Answering differently would confirm that a particular code once existed, which is a
     * fact about a studio a stranger holding an old banner should not be able to check —
     * least of all about a studio that has asked to be forgotten.
     */
    @Test
    fun `a deleted studio's code answers exactly as an unknown one does`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)
            client.deleteAccount(session)

            val deleted = client.get("/api/join/${invite.token}")
            val unknown = client.get("/api/join/not-a-real-token")

            assertEquals(unknown.status, deleted.status)
            assertEquals(unknown.bodyAsText(), deleted.bodyAsText())
        }

    /** And nobody can sign up through it either, which is the half that collects an address. */
    @Test
    fun `signing up through a deleted studio's code is refused`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)
            client.deleteAccount(session)

            val refused = client.join(invite.token, "guest@example.test")
            val unknown = client.join("not-a-real-token", "guest@example.test")

            assertEquals(unknown.status, refused.status)
        }

    private suspend fun HttpClient.deleteAccount(session: SessionResponse) {
        val response =
            post("/auth/delete-account") {
                bearerAuth(session.token)
                contentType(ContentType.Application.Json)
                setBody(apiJson.encodeToString(DeleteAccountRequest("a long enough password")))
            }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    }

    // -- Whether sign-up is open ------------------------------------------------------------

    /**
     * The list says whether a code is live, and asking does not make one.
     *
     * A display device shows the events somebody could still sign up to, which means asking
     * that question about every event a studio has. The only other way to ask is to request
     * the invite — and that issues one. A device would then open sign-ups on every event the
     * studio had ever created merely by listing them, which is the opposite of what the
     * studio asked for and invisible until strangers started appearing on old events.
     */
    @Test
    fun `an event with no code is not open for sign-up`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")

            assertFalse(client.events(session).single { it.id == event }.signUpOpen)
        }

    @Test
    fun `listing events does not issue a code`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")

            client.events(session)
            client.events(session)

            assertFalse(
                client.events(session).single { it.id == event }.signUpOpen,
                "listing events opened sign-ups by itself",
            )
        }

    @Test
    fun `an event with a live code is open for sign-up`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")

            client.invite(session, event)

            assertTrue(client.events(session).single { it.id == event }.signUpOpen)
        }

    /**
     * Withdrawing closes it again, and the display on the table has to notice.
     *
     * A device showing a code for a withdrawn invite is inviting people to scan something
     * that will refuse them — worse than showing nothing, because they walk away believing
     * they signed up.
     */
    @Test
    fun `withdrawing a code closes sign-up again`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            client.invite(session, event)

            client.post("/events/$event/invite/revoke") { bearerAuth(session.token) }

            assertFalse(client.events(session).single { it.id == event }.signUpOpen)
        }

    /** One event's code must not report another's as open. */
    @Test
    fun `the flag is per event`() =
        withServer { client ->
            val session = client.signUp()
            val open = client.createEvent(session, "Open")
            val closed = client.createEvent(session, "Closed")
            client.invite(session, open)

            val events = client.events(session)

            assertTrue(events.single { it.id == open }.signUpOpen)
            assertFalse(events.single { it.id == closed }.signUpOpen, "the other event was reported open")
        }

    /**
     * Another studio's invite row must not make this studio's event look open.
     *
     * No route can produce this state — issuing checks ownership, which is the fix for the
     * first bug this table caused. The row is written directly here because the predicate
     * that keeps it harmless is invisible otherwise: every other table in this query is
     * filtered by the studio scope, `event_invite` carries no policy and is not, and both
     * bugs this table has already caused were a query on it that forgot to say which studio
     * it meant. Without a test the `AND i.studio_id = ?` reads like a redundant line somebody
     * will eventually tidy away.
     */
    @Test
    fun `another studio's invite does not open this studio's event`() =
        withServer { client ->
            val harbourline = client.signUp()
            val other = client.signUp()
            val event = client.createEvent(harbourline, "Harbour Awards 2026")

            TestDatabase.database.unscoped { db ->
                db
                    .prepareStatement(
                        """
                        INSERT INTO event_invite(token, studio_id, event_id, created_at)
                        VALUES (?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, "a-token-belonging-to-nobody-here")
                        statement.setString(2, other.studioId)
                        statement.setString(3, event)
                        statement.setLong(4, 0L)
                        statement.executeUpdate()
                    }
            }

            assertFalse(
                client.events(harbourline).single { it.id == event }.signUpOpen,
                "another studio's invite opened this studio's event",
            )
        }

    private suspend fun HttpClient.events(session: SessionResponse): List<EventSummary> {
        val response = get("/events") { bearerAuth(session.token) }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        return apiJson.decodeFromString(response.bodyAsText())
    }

    // -- The code as a grid ---------------------------------------------------------------

    /**
     * The code on a screen is the code on the paper.
     *
     * A device on a table and a printed card are two renderings of one invite, and the failure
     * to be afraid of is that they drift — a second encoder, a different quiet zone, an event
     * whose screen code sends people somewhere the printed one does not. So this decodes what
     * went over the wire and compares it to the link the invite endpoint gave out.
     */
    @Test
    fun `the code drawn on a screen carries the link the invite gave out`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val invite = client.invite(session, event)

            val grid = client.grid(session, event)

            assertEquals(invite.url, decode(grid))
        }

    /** Asking for the grid before ever issuing an invite still gives a working code. */
    @Test
    fun `a studio that asks for the grid first still gets a code`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")

            val grid = client.grid(session, event)

            // The same code the invite endpoint then reports, rather than a second one.
            assertEquals(client.invite(session, event).url, decode(grid))
        }

    /**
     * The grid is square and says how big it is.
     *
     * A client draws a module per character and trusts `size`; a row of the wrong length would
     * shear the code into something that renders and never scans.
     */
    @Test
    fun `the grid is square`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")

            val grid = client.grid(session, event)

            assertEquals(grid.size, grid.rows.size, "the grid has the wrong number of rows")
            assertTrue(grid.rows.all { it.length == grid.size }, "a row is not as wide as the grid says")
            assertTrue(grid.rows.all { row -> row.all { it == '0' || it == '1' } }, "a row holds something else")
        }

    /** Without a session this endpoint would turn an event identifier into a working code. */
    @Test
    fun `the grid refuses a caller with no token`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")

            assertEquals(HttpStatusCode.Unauthorized, client.get("/events/$event/invite.qr").status)
        }

    /** And the same cross-tenant hole the issuing endpoint had. */
    @Test
    fun `one studio cannot get a code for another studio's event`() =
        withServer { client ->
            val harbourline = client.signUp()
            val other = client.signUp()
            val event = client.createEvent(harbourline, "Harbour Awards 2026")

            val stolen = client.get("/events/$event/invite.qr") { bearerAuth(other.token) }

            assertNotEquals(HttpStatusCode.OK, stolen.status, "another studio got a code: ${stolen.bodyAsText()}")
        }

    private suspend fun HttpClient.grid(
        session: SessionResponse,
        event: String,
    ): QrMatrix {
        val response = get("/events/$event/invite.qr") { bearerAuth(session.token) }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        return apiJson.decodeFromString(response.bodyAsText())
    }

    /** Blown up so the binarizer has something to threshold, then read as a phone would. */
    private fun decode(grid: QrMatrix): String {
        val scale = 4
        val side = grid.size * scale
        val pixels =
            IntArray(side * side) { i ->
                val dark = grid.rows[i / side / scale][i % side / scale] == '1'
                if (dark) 0x000000 else 0xFFFFFF
            }

        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))).text
    }

    // -- Plumbing -----------------------------------------------------------------------------

    /**
     * A deployment that has declared origins, which is what installs CORS at all.
     *
     * The photographs host is deliberately *not* among them: the point is that the server
     * adds its own public site regardless, so a deployment cannot forget it.
     */
    private fun withCors(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application {
                module(TestDatabase.database, Deployment(allowedOrigins = listOf("https://app.example.test")))
            }
            block(client)
        }

    private fun withServer(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module(TestDatabase.database) }
            block(client)
        }

    private suspend fun HttpClient.signUp(): SessionResponse {
        val email = "invites-${counter++}-${System.nanoTime()}@harbourline.test"
        val response =
            post("/auth/sign-up") {
                contentType(ContentType.Application.Json)
                setBody(
                    apiJson.encodeToString(
                        SignUpRequest(email, "a long enough password", "Ada Okafor", "Harbourline Photography"),
                    ),
                )
            }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())

        return apiJson.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.createEvent(
        session: SessionResponse,
        name: String,
    ): String {
        val response =
            post("/events") {
                bearerAuth(session.token)
                contentType(ContentType.Application.Json)
                setBody(apiJson.encodeToString(CreateEventRequest(name)))
            }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())

        return apiJson.decodeFromString<CreatedResponse>(response.bodyAsText()).id
    }

    private suspend fun HttpClient.openStation(
        session: SessionResponse,
        eventId: String,
        name: String,
        sourceKey: String,
    ) {
        post("/events/$eventId/stations") {
            bearerAuth(session.token)
            contentType(ContentType.Application.Json)
            setBody(
                apiJson.encodeToString(
                    com.yellowtrack.platform.core.model.event
                        .OpenStationRequest(name, sourceKey),
                ),
            )
        }
    }

    private suspend fun HttpClient.registrations(
        session: SessionResponse,
        eventId: String,
    ): List<RegistrationSummary> {
        val response = get("/events/$eventId/registrations") { bearerAuth(session.token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        return apiJson.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.invite(
        session: SessionResponse,
        eventId: String,
    ): EventInviteResponse {
        val response = post("/events/$eventId/invite") { bearerAuth(session.token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        return apiJson.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.join(
        token: String,
        email: String,
        name: String? = null,
    ) = post("/api/join/$token") {
        contentType(ContentType.Application.Json)
        setBody(apiJson.encodeToString(SignUpToEventRequest(email, name)))
    }

    private fun registrationCount(eventId: String): Int =
        TestDatabase.connection().use { connection ->
            connection.prepareStatement("SELECT count(*) FROM event_registration WHERE event_id = ?").use { statement ->
                statement.setString(1, eventId)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

    private companion object {
        private var counter = 0
    }
}
