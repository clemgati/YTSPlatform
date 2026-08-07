package com.yellowtrack.platform.core.data.sync

import kotlinx.browser.window
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The browser's own answer, which is the weakest of the four and the most useful.
 *
 * Weakest because `navigator.onLine` is false only when the browser is certain there is no
 * network — a captive portal, or a router with no route out, both read as online. Most useful
 * because the web build's database is in memory: a reload starts from nothing and re-pulls the
 * studio, so a tab that has been open through a connection drop is exactly the case where
 * waiting out a backoff is most visible.
 *
 * The initial value is emitted, unlike the platforms that only report changes. A tab restored
 * from the back/forward cache resumes with no event of its own, and one extra reconciliation
 * on open is cheaper than a stale screen.
 */
class BrowserConnectivity : Connectivity {
    override val online: Flow<Boolean> =
        callbackFlow {
            trySend(window.navigator.onLine)

            val onOnline: (org.w3c.dom.events.Event) -> Unit = { trySend(true) }
            val onOffline: (org.w3c.dom.events.Event) -> Unit = { trySend(false) }

            window.addEventListener("online", onOnline)
            window.addEventListener("offline", onOffline)

            awaitClose {
                window.removeEventListener("online", onOnline)
                window.removeEventListener("offline", onOffline)
            }
        }
}
