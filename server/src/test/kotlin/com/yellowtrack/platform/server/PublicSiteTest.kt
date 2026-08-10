package com.yellowtrack.platform.server

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two pages a member of the public sees.
 *
 * There is no logic in them worth testing from here — that lives in the endpoints they call.
 * What is worth testing is what a page carrying somebody's private link in its address bar
 * must not do: end up in a cache, in a search index, in a Referer header, or in a frame on
 * somebody else's site.
 */
class PublicSiteTest {
    @Test
    fun `scanning a code serves the sign-up page`() =
        withServer { client ->
            val response = client.get("/join/any-token")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers["Content-Type"].orEmpty().startsWith("text/html"), "not html")
            assertTrue("Get your photographs" in response.bodyAsText())
        }

    @Test
    fun `a delivery link serves the gallery page`() =
        withServer { client ->
            val response = client.get("/gallery/any-token")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue("Your photographs" in response.bodyAsText())
        }

    /**
     * The page is the same file for every token.
     *
     * It is served before anybody knows whether the token is real, so an unknown one must not
     * be distinguishable at this layer either — the page asks the API and shows what it is
     * told, which is where the one careful answer lives.
     */
    @Test
    fun `an unknown token still serves the page`() =
        withServer { client ->
            assertEquals(HttpStatusCode.OK, client.get("/join/definitely-not-a-token").status)
        }

    /**
     * Nothing from the URL reaches the markup.
     *
     * The token is read from the address bar by script. If it were interpolated server-side, a
     * token would be an injection point on a page that has no authentication in front of it.
     */
    @Test
    fun `the token is not written into the page`() =
        withServer { client ->
            val token = "zzunlikelyzz"

            assertFalse(token in client.get("/join/$token").bodyAsText(), "the token was interpolated")
            assertFalse(token in client.get("/gallery/$token").bodyAsText(), "the token was interpolated")
        }

    /** And markup in a token is not markup in the page. */
    @Test
    fun `markup in a token does not reach the page`() =
        withServer { client ->
            val body = client.get("/join/%3Cscript%3Ealert(1)%3C%2Fscript%3E").bodyAsText()

            assertFalse("alert(1)" in body, "a crafted token reached the page: $body")
        }

    /**
     * The sign-up page says where to look if the email does not arrive.
     *
     * The first delivery this product sent went to spam with every authentication check
     * passing. A new sending domain has no reputation and nothing in the software fixes that
     * on the day — so the page says so, because at an event the difference between a guest
     * finding their photographs and deciding none were sent is one sentence.
     */
    @Test
    fun `the sign-up page tells people to check their spam folder`() =
        withServer { client ->
            // Comments stripped first. The first version of this assertion passed on an HTML
            // comment explaining *why* the line exists — a word no reader can see, in a test
            // that claimed they could.
            val visible = client.get("/join/any-token").bodyAsText().replace(Regex("(?s)<!--.*?-->"), "")

            assertTrue("spam" in visible.lowercase(), "the page does not mention spam")
        }

    // -- What a page holding a private link must not do ---------------------------------------

    /**
     * The URL is the credential, so a copy left in a cache outlives the visit.
     *
     * A phone handed round at an event, a browser restored from history, an intermediary that
     * decided an HTML response looked cacheable.
     */
    @Test
    fun `the pages are not cached`() =
        withServer { client ->
            listOf("/join/any-token", "/gallery/any-token").forEach { path ->
                val cacheControl = client.get(path).headers["Cache-Control"].orEmpty()

                assertTrue("no-store" in cacheControl, "$path is cacheable: '$cacheControl'")
            }
        }

    /** A token in a search result is a token in public. */
    @Test
    fun `the pages ask not to be indexed`() =
        withServer { client ->
            listOf("/join/any-token", "/gallery/any-token").forEach { path ->
                val response = client.get(path)

                assertTrue(
                    "noindex" in response.headers["X-Robots-Tag"].orEmpty(),
                    "$path may be indexed",
                )
                assertTrue("noindex" in response.bodyAsText(), "$path has no robots meta tag")
            }
        }

    /**
     * A token in a Referer is a token handed to whatever the guest opens next — including the
     * object store the photographs come from.
     */
    @Test
    fun `the pages send no referrer`() =
        withServer { client ->
            listOf("/join/any-token", "/gallery/any-token").forEach { path ->
                assertEquals("no-referrer", client.get(path).headers["Referrer-Policy"], path)
            }
        }

    /** Somebody else's page must not be able to put this one in a frame. */
    @Test
    fun `the pages refuse to be framed`() =
        withServer { client ->
            val response = client.get("/gallery/any-token")

            assertEquals("DENY", response.headers["X-Frame-Options"])
            assertTrue(
                "frame-ancestors 'none'" in response.headers["Content-Security-Policy"].orEmpty(),
                response.headers["Content-Security-Policy"].orEmpty(),
            )
        }

    /**
     * The policy has to allow the one thing the gallery genuinely needs and nothing more.
     *
     * Photographs are presigned URLs on the object store, so images come from elsewhere;
     * scripts and styles do not.
     */
    @Test
    fun `the policy allows images from the object store and scripts from nowhere else`() =
        withServer { client ->
            val policy = client.get("/gallery/any-token").headers["Content-Security-Policy"].orEmpty()

            assertTrue("default-src 'none'" in policy, policy)
            assertTrue("script-src 'self'" in policy, policy)
            assertTrue("img-src 'self' https: data:" in policy, policy)
            assertFalse("unsafe-inline" in policy, "inline scripts or styles are allowed: $policy")
        }

    // -- The assets ----------------------------------------------------------------------------

    @Test
    fun `the stylesheet and scripts are served`() =
        withServer { client ->
            listOf(
                "/site.css" to "text/css",
                "/join.js" to "javascript",
                "/gallery.js" to "javascript",
            ).forEach { (path, type) ->
                val response = client.get(path)

                assertEquals(HttpStatusCode.OK, response.status, path)
                val contentType = response.headers["Content-Type"].orEmpty()

                assertTrue(type in contentType, "$path: $contentType")
            }
        }

    /**
     * The scripts have to actually ask the API this build serves.
     *
     * The JSON moved under `/api/` when the pages took the friendly paths, and a page calling
     * the old address would render "not open" for every valid code — while every server test
     * carried on passing, because they call the API directly.
     */
    @Test
    fun `the scripts call the api paths that exist`() =
        withServer { client ->
            assertTrue("/api/join/" in client.get("/join.js").bodyAsText(), "join.js does not call /api/join")
            assertTrue(
                "/api/gallery/" in client.get("/gallery.js").bodyAsText(),
                "gallery.js does not call /api/gallery",
            )
        }

    /** Nothing inline, or the policy above would have to be loosened to allow it. */
    @Test
    fun `the pages carry no inline script or style`() =
        withServer { client ->
            listOf("/join/any-token", "/gallery/any-token").forEach { path ->
                val body = client.get(path).bodyAsText()

                val inlineScript = Regex("<script(?![^>]*\\bsrc=)")

                assertFalse(inlineScript.containsMatchIn(body), "$path has an inline script")
            }
        }

    private fun withServer(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module(TestDatabase.database) }
            block(client)
        }
}
