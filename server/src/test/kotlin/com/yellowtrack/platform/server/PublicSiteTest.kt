package com.yellowtrack.platform.server

import com.yellowtrack.platform.core.model.auth.SessionResponse
import com.yellowtrack.platform.core.model.auth.SignUpRequest
import com.yellowtrack.platform.core.model.event.CreateEventRequest
import com.yellowtrack.platform.core.model.event.CreatedResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
     * The pages ask for assets by an address that changes when the asset does.
     *
     * Assets are cached for an hour and pages are not, so a stylesheet change used to reach a
     * browser that had visited before only after that hour — the heading was centred on the
     * deployment and left-aligned in the studio's browser, and that looks nothing like a
     * caching problem while you are staring at it.
     *
     * The placeholder must not survive either. A page serving a literal `{{assets}}` would
     * still render, still be styled, and quietly never invalidate anything again.
     */
    @Test
    fun `the pages version the assets they ask for`() =
        withServer { client ->
            val page = client.get("/join/anything").bodyAsText()

            assertFalse("{{assets}}" in page, "the placeholder was served as-is")

            val versions =
                Regex("""(?:href|src)="/(?:site\.css|join\.js|mark\.png)\?v=([0-9a-f]+)"""")
                    .findAll(page)
                    .map { it.groupValues[1] }
                    .toList()

            assertEquals(3, versions.size, "not every asset is versioned: $page")
            assertEquals(1, versions.toSet().size, "the assets disagree about the version")

            // And it is derived from the assets, not merely present. A constant would satisfy
            // everything above while never invalidating anything — which is the bug this
            // exists to prevent rather than a lesser version of it.
            assertEquals(hashOfAssets(), versions.first(), "the version does not follow the assets")
        }

    /** The same derivation, computed here, so a token that ignores the bytes fails. */
    private fun hashOfAssets(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")

        listOf("site.css", "join.js", "gallery.js", "mark.png")
            .mapNotNull { javaClass.classLoader.getResourceAsStream("public/$it")?.use { s -> s.readBytes() } }
            .forEach { digest.update(it) }

        return digest.digest().take(6).joinToString("") { byte -> byte.toUByte().toString(16).padStart(2, '0') }
    }

    /**
     * The mark the sign-up page shows, and that the page asks for the one being served.
     *
     * A guest is about to type their name, address and telephone number into a page they
     * reached by pointing a camera at a piece of paper. A broken image at the top of it is
     * worse than no image, and nothing else here would notice: the page would still render,
     * the form would still work, and the one thing meant to say who is asking would be a grey
     * box.
     */
    @Test
    fun `the sign-up page shows a mark that is actually served`() =
        withServer { client ->
            val page = client.get("/join/anything").bodyAsText()

            val source =
                Regex("""<img[^>]*src="([^"]+)"""")
                    .find(page)
                    ?.groupValues
                    ?.get(1)

            assertNotNull(source, "the page has no image at all")
            assertTrue(source.startsWith("/mark.png"), "unexpected image source: $source")

            val served = client.get(source)

            assertEquals(HttpStatusCode.OK, served.status)
            assertEquals(ContentType.Image.PNG, served.contentType()?.withoutParameters())
            // The signature, not the size. Serving this through the text helper — which is
            // the obvious mistake, since every other asset goes that way — decodes it as
            // UTF-8 and re-encodes it, and the result is still a few kilobytes of something.
            // It is simply no longer a PNG, and only the first eight bytes say so.
            val bytes = served.bodyAsBytes()

            assertContentEquals(
                byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()),
                bytes.take(4).toByteArray(),
                "what came back is not a PNG",
            )
            assertTrue(bytes.size > 1_000, "the mark is suspiciously small")
        }

    /**
     * The script's element names and the page's are the same names.
     *
     * `join.js` reaches for these by identifier. Renaming one in the markup breaks the form
     * at runtime — the field is simply never read, and a guest's name or number is silently
     * dropped — and nothing in a Kotlin build would say a word about it. The two files are
     * one thing and this is the only place that says so.
     */
    @Test
    fun `the sign-up form has the fields the script reads`() =
        withServer { client ->
            val page = client.get("/join/anything").bodyAsText()
            val script = client.get("/join.js").bodyAsText()

            listOf("email", "given-name", "family-name", "phone").forEach { field ->
                assertTrue("id=\"$field\"" in page, "the page has no $field field")
                assertTrue("getElementById(\"$field\")" in script, "the script does not read $field")
            }
        }

    /** Both halves of the name are required in the markup, not only on the server. */
    @Test
    fun `the sign-up form requires both names`() =
        withServer { client ->
            val page = client.get("/join/anything").bodyAsText()

            val required =
                Regex("""<input[^>]*id="(given-name|family-name)"[^>]*>""")
                    .findAll(page)
                    .filter { "required" in it.value }
                    .count()

            assertEquals(2, required, "a name field is not marked required in the markup")
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

    // -- The front page ------------------------------------------------------------------------

    /**
     * Typing the domain must not be an error.
     *
     * It is printed on banners and it is the sender of every delivery email, so it is typed
     * by people holding no link at all. It answered 403 from the vhost allowlist until this
     * page existed, which reads as broken rather than as "you need your own link".
     */
    @Test
    fun `the root serves a page rather than an error`() =
        withServer { client ->
            val response = client.get("/")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers["Content-Type"].orEmpty().startsWith("text/html"), "not html")
            assertTrue("Yellow Track Photos" in response.bodyAsText())
        }

    /** It tells somebody with no link what to do, which is the whole reason it exists. */
    @Test
    fun `the front page says how photographs are reached`() =
        withServer { client ->
            val body = client.get("/").bodyAsText().lowercase()

            assertTrue("link" in body, "it does not mention a link")
            assertTrue("spam" in body, "it does not say where a delivery often lands")
        }

    /**
     * And it knows nothing.
     *
     * Served to anybody who asks, with no token in its address, so it must name no event, no
     * studio and no person — the same discipline as the sign-up page, applied to a page that
     * has no context at all.
     */
    @Test
    fun `the front page names no event and no person`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            client.invite(session, event)

            val body = client.get("/").bodyAsText()

            assertFalse("Harbour Awards" in body, "an event was named on the public front page")
            assertFalse(event in body, "an event identifier reached the front page")
        }

    /**
     * Unlike the other two, this one wants to be found.
     *
     * The `noindex` on the token pages exists because their addresses are credentials. This
     * page has no address to protect, and a domain that sends mail while serving nothing
     * indexable is a domain receivers trust less.
     */
    @Test
    fun `the front page may be indexed and cached`() =
        withServer { client ->
            val response = client.get("/")

            assertEquals(null, response.headers["X-Robots-Tag"], "the front page asked not to be indexed")
            assertFalse(
                "no-store" in response.headers["Cache-Control"].orEmpty(),
                "the front page holds nothing private and need not be uncacheable",
            )
        }

    /** The token pages keep their protections, which this must not have loosened. */
    @Test
    fun `the token pages are still not indexed or cached`() =
        withServer { client ->
            listOf("/join/any-token", "/gallery/any-token").forEach { path ->
                val response = client.get(path)

                assertTrue("noindex" in response.headers["X-Robots-Tag"].orEmpty(), "$path may be indexed")
                assertTrue("no-store" in response.headers["Cache-Control"].orEmpty(), "$path is cacheable")
            }
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

    private suspend fun HttpClient.signUp(): SessionResponse {
        val email = "site-${counter++}-${System.nanoTime()}@harbourline.test"
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

    private suspend fun HttpClient.invite(
        session: SessionResponse,
        eventId: String,
    ) = post("/events/$eventId/invite") { bearerAuth(session.token) }

    private companion object {
        private var counter = 0
    }

    private fun withServer(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module(TestDatabase.database) }
            block(client)
        }
}
