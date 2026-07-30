package com.yellowtrack.platform.core.common.time

/**
 * Makes sure named time zones can be resolved on this platform.
 *
 * A no-op everywhere except the browser, where the zone database has to be pulled in
 * explicitly — see the wasm implementation. Called once at start-up rather than guarded
 * at every call site, because the failure it prevents is a thrown exception deep inside
 * rendering a shoot day.
 */
expect fun ensureTimeZonesLoaded()
