package com.yellowtrack.platform.core.common.time

/**
 * Loads the IANA timezone database into the browser build.
 *
 * `kotlinx-datetime` resolves zone ids on wasm through `@js-joda/core`, which ships with
 * no zone data at all: `TimeZone.of("Europe/London")` throws `Invalid zone ID` until
 * `@js-joda/timezone` has been imported and registered itself. Every `Session` carries a
 * zone id, so without this the web build fails on any screen that renders a shoot time.
 *
 * The module is imported for its side effect, and [ensureTimeZonesLoaded] exists so
 * something references it — an unreferenced external object is liable to be dropped.
 */
@JsModule("@js-joda/timezone")
private external object JsJodaTimeZone

private val loaded: JsJodaTimeZone = JsJodaTimeZone

actual fun ensureTimeZonesLoaded() {
    // Touching the module is the whole point; the value itself is of no interest.
    loaded.toString()
}
