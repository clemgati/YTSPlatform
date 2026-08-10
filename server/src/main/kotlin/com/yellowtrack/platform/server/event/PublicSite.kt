package com.yellowtrack.platform.server.event

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
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
    get("/join/{token}") { call.page("join.html") }

    get("/gallery/{token}") { call.page("gallery.html") }

    get("/site.css") { call.asset("site.css", ContentType.Text.CSS) }

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
    val html = read(name) ?: return respond(HttpStatusCode.NotFound, "Not found")

    response.cacheControl(CacheControl.NoStore(null))
    response.header(HttpHeaders.Pragma, "no-cache")
    security()

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

private fun ApplicationCall.security() {
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
    // Search engines have no business here, and a token in an index is a token in public.
    response.header("X-Robots-Tag", "noindex, nofollow")
}

private suspend fun ApplicationCall.respond(
    status: HttpStatusCode,
    body: String,
) = respondText(body, ContentType.Text.Plain, status)

/**
 * Read from the classpath rather than the file system, so it works the same from a shadow jar
 * as from a Gradle run.
 */
private fun read(name: String): String? =
    object {}
        .javaClass.classLoader
        .getResourceAsStream("public/$name")
        ?.bufferedReader()
        ?.use { it.readText() }
