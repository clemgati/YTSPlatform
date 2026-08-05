package com.yellowtrack.platform.server

import com.yellowtrack.platform.core.model.auth.AccountResponse
import com.yellowtrack.platform.core.model.auth.SessionResponse
import com.yellowtrack.platform.core.model.auth.SignInRequest
import com.yellowtrack.platform.core.model.auth.SignUpRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
 * The sign-up, sign-in and sign-out path, end to end over HTTP.
 *
 * Runs against the real Postgres, so what is exercised is the same code and the same
 * schema a device would meet.
 */
class AuthenticationTest {
    // -- Signing up ---------------------------------------------------------------------

    // -- Taking your work with you -------------------------------------------------------

    @Test
    fun `a studio can download everything it has`() =
        withServer { client ->
            val session = client.signUpSuccessfully(uniqueEmail())

            val response = client.get("/auth/export") { bearerAuth(session.token) }

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            // An attachment, or a browser renders megabytes of JSON into a tab.
            assertTrue(
                response.headers["Content-Disposition"]?.contains("attachment") == true,
                "should download rather than display: ${response.headers["Content-Disposition"]}",
            )

            val body = response.bodyAsText()
            assertTrue("\"application\":\"Yellow Track\"" in body, body.take(200))
            assertTrue(session.studioId in body, "the export should name the studio it is of")
        }

    /** A studio's whole history is not something to hand to an unauthenticated caller. */
    @Test
    fun `the export refuses a caller with no token`() =
        withServer { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.get("/auth/export").status)
        }

    /**
     * The server is the authority on shape, not the form. A client that skips the check —
     * an old build, a browser with the script blocked, curl — must still be refused.
     */
    @Test
    fun `refuses an address that could not be delivered to`() =
        withServer { client ->
            listOf("ada-no-at-sign.test", "ada@localhost", "ada@harbourline.", "@harbourline.test", "ada@a.b.1")
                .forEach { malformed ->
                    val response = client.signUp(malformed, PASSWORD, "Ada Okafor", "Harbourline Photography")
                    assertEquals(HttpStatusCode.BadRequest, response.status, "should refuse $malformed")
                }
        }

    /**
     * The fault that prompted all of this. `@gmail.ocm` is a well-formed address whose
     * domain does not exist, and the server cannot know that — so it must take it. The
     * form asks about it instead, which is the only place the answer lives.
     */
    @Test
    fun `accepts a well-formed address even when the domain looks like a slip`() =
        withServer { client ->
            val response =
                client.signUp("ada-${System.nanoTime()}@gmail.ocm", PASSWORD, "Ada Okafor", "Harbourline Photography")

            assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        }

    @Test
    fun `signing up creates an account, a studio, and the membership between them`() =
        withServer { client ->
            val email = uniqueEmail()
            val response = client.signUp(email, PASSWORD, "Ada Okafor", "Harbourline Photography")

            assertEquals(HttpStatusCode.Created, response.status)
            val session = apiJson.decodeFromString<SessionResponse>(response.bodyAsText())

            assertEquals(email, session.email)
            assertEquals("Harbourline Photography", session.studioName)
            assertTrue(session.token.isNotBlank(), "a sign-up must come back signed in")
            assertTrue(session.expiresAt > System.currentTimeMillis(), "the session must not arrive expired")

            TestDatabase.connection().use { db ->
                assertEquals(
                    1,
                    countOf(
                        db,
                        """
                        SELECT count(*) FROM studio_member
                        JOIN account ON account.id = studio_member.account_id
                        WHERE account.email = ? AND studio_member.role = 'Owner'
                        """.trimIndent(),
                        email,
                    ),
                    "the person who signs up owns the studio they created",
                )
            }
        }

    @Test
    fun `the password is not stored, and neither is the token`() =
        withServer { client ->
            val email = uniqueEmail()
            val session = client.signUpSuccessfully(email)

            TestDatabase.connection().use { db ->
                val stored =
                    db.prepareStatement("SELECT password_hash FROM account WHERE email = ?").use { statement ->
                        statement.setString(1, email)
                        statement.executeQuery().use { rows ->
                            rows.next()
                            rows.getString(1)
                        }
                    }

                assertNotEquals(PASSWORD, stored, "a stored password is a disclosed password")
                assertFalse(stored.contains(PASSWORD), "and it must not merely be wrapped in something")
                assertTrue(stored.startsWith("\$argon2id\$"), "expected an Argon2id hash, got: ${stored.take(20)}")

                assertEquals(
                    0,
                    countOf(db, "SELECT count(*) FROM auth_session WHERE token_digest = ?", session.token),
                    "the table must hold the digest of the token, never the token itself",
                )
            }
        }

    @Test
    fun `the same email cannot be registered twice`() =
        withServer { client ->
            val email = uniqueEmail()
            client.signUpSuccessfully(email)

            val second = client.signUp(email, PASSWORD, "Someone Else", "Another Studio")

            assertEquals(HttpStatusCode.Conflict, second.status)
        }

    @Test
    fun `an email is the same address whatever its capitals`() =
        withServer { client ->
            val email = uniqueEmail()
            client.signUpSuccessfully(email)

            assertEquals(
                HttpStatusCode.Conflict,
                client.signUp(email.uppercase(), PASSWORD, "Someone Else", "Another Studio").status,
                "two accounts differing only by capitals would be two people with one address",
            )

            assertEquals(
                HttpStatusCode.OK,
                client.signIn(email.uppercase(), PASSWORD).status,
                "and signing in must not depend on typing it the same way twice",
            )
        }

    @Test
    fun `a password too short to be worth having is refused`() =
        withServer { client ->
            val response = client.signUp(uniqueEmail(), "short", "Ada Okafor", "Harbourline")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    // -- Signing in ---------------------------------------------------------------------

    @Test
    fun `the right password signs in`() =
        withServer { client ->
            val email = uniqueEmail()
            client.signUpSuccessfully(email)

            val response = client.signIn(email, PASSWORD)

            assertEquals(HttpStatusCode.OK, response.status)
            val session = apiJson.decodeFromString<SessionResponse>(response.bodyAsText())
            assertTrue(session.token.isNotBlank())
        }

    @Test
    fun `a wrong password and an unknown address are refused identically`() =
        withServer { client ->
            val email = uniqueEmail()
            client.signUpSuccessfully(email)

            val wrongPassword = client.signIn(email, "not the right password")
            val unknownAddress = client.signIn(uniqueEmail(), PASSWORD)

            assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
            assertEquals(HttpStatusCode.Unauthorized, unknownAddress.status)
            assertEquals(
                wrongPassword.bodyAsText(),
                unknownAddress.bodyAsText(),
                "telling the two apart turns this endpoint into a way to ask who has an account here",
            )
        }

    @Test
    fun `signing in twice gives two different tokens, and both work`() =
        withServer { client ->
            val email = uniqueEmail()
            val laptop = client.signUpSuccessfully(email)
            val phone = apiJson.decodeFromString<SessionResponse>(client.signIn(email, PASSWORD).bodyAsText())

            assertNotEquals(laptop.token, phone.token, "two devices are two sessions")
            assertEquals(HttpStatusCode.OK, client.me(laptop.token).status)
            assertEquals(HttpStatusCode.OK, client.me(phone.token).status)
        }

    // -- Being signed in ----------------------------------------------------------------

    @Test
    fun `a token identifies the account and the studio it acts as`() =
        withServer { client ->
            val email = uniqueEmail()
            val session = client.signUpSuccessfully(email)

            val response = client.me(session.token)

            assertEquals(HttpStatusCode.OK, response.status)
            val me = apiJson.decodeFromString<AccountResponse>(response.bodyAsText())
            assertEquals(email, me.email)
            assertEquals(session.studioId, me.studioId)
            assertEquals("Harbourline Photography", me.studioName)
        }

    @Test
    fun `no token, a nonsense token, and a tampered token are all refused`() =
        withServer { client ->
            val session = client.signUpSuccessfully(uniqueEmail())

            assertEquals(HttpStatusCode.Unauthorized, client.get("/auth/me").status)
            assertEquals(HttpStatusCode.Unauthorized, client.me("not-a-token").status)
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.me(session.token.dropLast(1) + "x").status,
                "a token is looked up by digest, so one close to a valid token is not close to valid",
            )
        }

    @Test
    fun `signing out stops that token and leaves the other device alone`() =
        withServer { client ->
            val email = uniqueEmail()
            val laptop = client.signUpSuccessfully(email)
            val phone = apiJson.decodeFromString<SessionResponse>(client.signIn(email, PASSWORD).bodyAsText())

            assertEquals(HttpStatusCode.NoContent, client.post("/auth/sign-out") { bearerAuth(laptop.token) }.status)

            assertEquals(
                HttpStatusCode.Unauthorized,
                client.me(laptop.token).status,
                "a revoked token must stop working immediately, which is the whole reason these are " +
                    "not JWTs",
            )
            assertEquals(
                HttpStatusCode.OK,
                client.me(phone.token).status,
                "signing out of one device must not sign out of the others",
            )
        }

    @Test
    fun `an expired session is refused even though it was never revoked`() =
        withServer { client ->
            val session = client.signUpSuccessfully(uniqueEmail())

            TestDatabase.connection().use { db ->
                db
                    .prepareStatement("UPDATE auth_session SET expires_at = 1 WHERE token_digest = ?")
                    .use { statement ->
                        statement.setString(
                            1,
                            com.yellowtrack.platform.server.auth.Tokens
                                .digest(session.token),
                        )
                        assertEquals(1, statement.executeUpdate(), "expected to age exactly one session")
                    }
            }

            assertEquals(HttpStatusCode.Unauthorized, client.me(session.token).status)
        }

    @Test
    fun `a session records that it was used`() =
        withServer { client ->
            val session = client.signUpSuccessfully(uniqueEmail())
            client.me(session.token)

            TestDatabase.connection().use { db ->
                val lastUsed =
                    db
                        .prepareStatement("SELECT last_used_at FROM auth_session WHERE token_digest = ?")
                        .use { statement ->
                            statement.setString(
                                1,
                                com.yellowtrack.platform.server.auth.Tokens
                                    .digest(session.token),
                            )
                            statement.executeQuery().use { rows ->
                                rows.next()
                                rows.getObject(1) as Long?
                            }
                        }

                assertNotNull(lastUsed, "a session nobody can date is a session nobody can prune")
            }
        }

    // -- Plumbing -----------------------------------------------------------------------

    private fun assertNotNull(
        value: Any?,
        message: String,
    ) = assertTrue(value != null, message)

    private fun withServer(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module(TestDatabase.database) }
            // Bodies are encoded by hand rather than by a client-side negotiation plugin,
            // so the test sends the bytes a device would send instead of relying on two
            // serialisers agreeing with each other.
            block(client)
        }

    private suspend fun HttpClient.signUp(
        email: String,
        password: String,
        name: String,
        studioName: String,
    ) = post("/auth/sign-up") {
        contentType(ContentType.Application.Json)
        setBody(apiJson.encodeToString(SignUpRequest(email, password, name, studioName)))
    }

    private suspend fun HttpClient.signUpSuccessfully(email: String): SessionResponse {
        val response = signUp(email, PASSWORD, "Ada Okafor", "Harbourline Photography")
        assertEquals(HttpStatusCode.Created, response.status, "sign-up should have succeeded: ${response.bodyAsText()}")
        return apiJson.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.signIn(
        email: String,
        password: String,
    ) = post("/auth/sign-in") {
        contentType(ContentType.Application.Json)
        setBody(apiJson.encodeToString(SignInRequest(email, password)))
    }

    private suspend fun HttpClient.me(token: String) = get("/auth/me") { bearerAuth(token) }

    private fun countOf(
        db: java.sql.Connection,
        sql: String,
        parameter: String,
    ): Int =
        db.prepareStatement(sql).use { statement ->
            statement.setString(1, parameter)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private companion object {
        /** Twelve characters, which is the floor the endpoint enforces. */
        const val PASSWORD = "a long enough password"

        private var counter = 0

        /** Unique per call, because the shared database is not emptied between tests. */
        fun uniqueEmail(): String = "ada-${counter++}-${System.nanoTime()}@harbourline.test"
    }
}
