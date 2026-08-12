package com.yellowtrack.platform.server.event

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * The two pages a member of the public sees, and the only HTML this server serves.
 *
 * ## Why plain files rather than the wasm build
 *
 * The rest of this product is Compose Multiplatform, and this is not. The audience is a guest
 * holding a phone on a venue's wifi who wants to see whether their headshot arrived: a form
 * and a grid of images. A Compose/wasm bundle is megabytes of runtime before the first
 * paint, over the worst network this product will ever meet, for a page with two inputs on
 * it. ADR 0013 already said the front end is not shared; this is what that costs and what it
 * buys.
 *
 * Served by this server rather than separately so the API is same-origin — no CORS, no second
 * deployment, and one place where `PHOTOS_URL` has to be true.
 *
 * ## Nothing is interpolated
 *
 * The token is in the address bar and the page reads it from there. No templating means no
 * way for a crafted token to become markup, and the pages set every value they receive with
 * `textContent` rather than `innerHTML`.
 */
fun Route.publicSite() {
    /**
     * The root, which exists so that typing the domain is not an error.
     *
     * It is printed on banners and it is the sender of every delivery email, so it is typed
     * by people holding no link at all. Before this it answered 403 from the vhost
     * allowlist, which reads as broken rather than as "you need your own link".
     *
     * Unlike the other two, this page may be cached and may be indexed: it names no event,
     * no studio and no person, and there is no token in its address.
     */
    get("/") { call.landing() }

    get("/join/{token}") { call.page("join.html") }

    get("/gallery/{token}") { call.page("gallery.html") }

    get("/site.css") { call.asset("site.css", ContentType.Text.CSS) }

    /**
     * The mark at the top of the sign-up page.
     *
     * A guest is about to type their name, address and phone number into a page they reached
     * by pointing a camera at a piece of paper. Something recognisable at the top of it is
     * worth more than any wording underneath.
     *
     * 160 pixels wide and eleven kilobytes, shown at about half that. This page is read on a
     * venue's wifi and the full-size mark is ninety-two kilobytes for the same picture.
     */
    get("/mark.png") { call.image("mark.png", ContentType.Image.PNG) }

    get("/join.js") { call.asset("join.js", ContentType.Text.JavaScript) }

    get("/gallery.js") { call.asset("gallery.js", ContentType.Text.JavaScript) }
}

/**
 * A page, with the headers a page carrying somebody's private link should have.
 *
 * Not cached: the URL *is* the credential, and a copy in a shared phone's cache or an
 * intermediary outlives the visit.
 */
private suspend fun ApplicationCall.page(name: String) {
    val html = read(name)?.replace(ASSET_TOKEN, assetVersion) ?: return respond(HttpStatusCode.NotFound, "Not found")

    response.cacheControl(CacheControl.NoStore(null))
    response.header(HttpHeaders.Pragma, "no-cache")
    security()

    respondText(html, ContentType.Text.Html)
}

/**
 * The front page: cacheable, indexable, and carrying no token.
 *
 * The `no-store` and `noindex` the other pages need exist because their addresses *are* the
 * credential. This one has no address to protect, and a domain that sends mail while serving
 * nothing indexable is a domain receivers trust less.
 */
private suspend fun ApplicationCall.landing() {
    val html = read("index.html") ?: return respond(HttpStatusCode.NotFound, "Not found")

    response.cacheControl(CacheControl.MaxAge(maxAgeSeconds = 3_600))
    security(indexable = true)

    respondText(html, ContentType.Text.Html)
}

/** The stylesheet and scripts hold nothing private, so they may be cached. */
private suspend fun ApplicationCall.asset(
    name: String,
    type: ContentType,
) {
    val body = read(name) ?: return respond(HttpStatusCode.NotFound, "Not found")

    response.cacheControl(CacheControl.MaxAge(maxAgeSeconds = 3_600))
    security()

    respondText(body, type)
}

/**
 * The same as [asset], for something that is not text.
 *
 * Separate rather than making [asset] generic: [read] decodes to a string, which is right for
 * every other thing this serves and would quietly corrupt anything that is not.
 */
private suspend fun ApplicationCall.image(
    name: String,
    type: ContentType,
) {
    val bytes = readBytes(name) ?: return respond(HttpStatusCode.NotFound, "Not found")

    // Longer than the pages, which change with a deployment. This does not.
    response.cacheControl(CacheControl.MaxAge(maxAgeSeconds = 86_400))
    security()

    respondBytes(bytes, type)
}

private fun ApplicationCall.security(indexable: Boolean = false) {
    // Everything the pages need comes from this origin, except the photographs themselves,
    // which are presigned URLs on the object store. Nothing is inline, so no unsafe-inline.
    response.header(
        "Content-Security-Policy",
        "default-src 'none'; " +
            "script-src 'self'; " +
            "style-src 'self'; " +
            "img-src 'self' https: data:; " +
            "connect-src 'self'; " +
            "base-uri 'none'; " +
            "form-action 'none'; " +
            "frame-ancestors 'none'",
    )
    response.header("X-Frame-Options", "DENY")
    response.header("X-Content-Type-Options", "nosniff")
    // A token in a Referer is a token handed to whatever the guest visits next. The pages say
    // this in a meta tag too; a header is the half a proxy cannot strip by rewriting HTML.
    response.header("Referrer-Policy", "no-referrer")
    // Search engines have no business on a page whose address is a credential, and a token
    // in an index is a token in public. The front page has no token and wants to be found.
    if (!indexable) response.header("X-Robots-Tag", "noindex, nofollow")
}

private suspend fun ApplicationCall.respond(
    status: HttpStatusCode,
    body: String,
) = respondText(body, ContentType.Text.Plain, status)

/**
 * Read from the classpath rather than the file system, so it works the same from a shadow jar
 * as from a Gradle run.
 */

/**
 * The placeholder the pages carry, and what it is replaced with.
 *
 * Assets are cached for an hour and the pages are not, so changing the stylesheet used to mean
 * a fresh page styled by a stale sheet — for up to an hour, for anybody who had visited
 * before. The heading was centred on the deployment and left-aligned in the studio's browser,
 * and nothing about that looks like a caching problem while you are staring at it.
 *
 * Derived from the bytes rather than from the clock, so the address changes when the asset
 * changes and not when the server restarts. A deployment that alters nothing invalidates
 * nothing.
 */
private const val ASSET_TOKEN = "{{assets}}"

private val assetVersion: String by lazy {
    val digest = java.security.MessageDigest.getInstance("SHA-256")

    listOf("site.css", "join.js", "gallery.js", "mark.png")
        .mapNotNull { readBytes(it) }
        .forEach { digest.update(it) }

    digest.digest().take(6).joinToString("") { byte -> byte.toUByte().toString(16).padStart(2, '0') }
}

private fun readBytes(name: String): ByteArray? =
    object {}
        .javaClass.classLoader
        .getResourceAsStream("public/$name")
        ?.use { it.readBytes() }

private fun read(name: String): String? =
    object {}
        .javaClass.classLoader
        .getResourceAsStream("public/$name")
        ?.bufferedReader()
        ?.use { it.readText() }
