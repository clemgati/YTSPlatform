package com.yellowtrack.platform.core.common.logging

/**
 * The browser console, which is the only place a web build can leave anything.
 *
 * `console.error` rather than `log`, so it survives a filter set to errors — the state a
 * console is usually left in when somebody is looking for a problem rather than reading
 * along.
 */
actual fun logFailure(
    where: String,
    error: Throwable,
) {
    reportToConsole("[yellowtrack] $where\n${error.stackTraceToString()}")
}

/**
 * Through `js` rather than `kotlinx.browser.window.console`, so this stays usable from a
 * worker as well as a page. `core:common` has no browser dependency and should not gain one
 * for a single call.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun reportToConsole(message: String): Unit = js("console.error(message)")
