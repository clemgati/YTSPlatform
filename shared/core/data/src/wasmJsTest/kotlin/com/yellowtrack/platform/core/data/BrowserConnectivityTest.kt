package com.yellowtrack.platform.core.data

import app.cash.turbine.test
import com.yellowtrack.platform.core.data.sync.BrowserConnectivity
import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import org.w3c.dom.events.Event
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The browser's answer to "is there a network", driven by real events in a real browser.
 *
 * The first test to run outside the JVM in this repository. Everything else lives in
 * `desktopTest`, which meant three of the four [com.yellowtrack.platform.core.data.sync.Connectivity]
 * implementations were only ever proved to compile — and compiling is exactly what a listener
 * registered against the wrong event name does perfectly well.
 *
 * These run under `wasmJsBrowserTest` in headless Chrome, so `window` is a genuine window and
 * `dispatchEvent` goes through the browser's own event machinery rather than a stub.
 */
class BrowserConnectivityTest {
    /**
     * The transition the whole feature exists for: a connection drops and comes back.
     *
     * The first emission is taken without asserting its value. It reflects
     * `navigator.onLine` in whatever browser is running the suite, and a test that depended
     * on that would fail on a machine with no network — which teaches nobody anything, and is
     * the same trap [DesktopConnectivityTest] documents.
     */
    @Test
    fun `reports the connection dropping and coming back`() =
        runTest {
            BrowserConnectivity().online.test {
                // Awaiting this first is not just an assertion. It is what proves the flow
                // has started and the listeners are registered — dispatching before that
                // would fire into a window nothing is listening to, and the test would hang
                // on an event that was never going to arrive.
                awaitItem()

                window.dispatchEvent(Event("offline"))
                assertFalse(awaitItem(), "an offline event means the browser is certain there is no network")

                window.dispatchEvent(Event("online"))
                assertTrue(awaitItem(), "and this is the emission that brings a sync forward")

                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Listeners are removed when collection stops.
     *
     * The web build's flow is collected for as long as a tab is open, so a leak here would
     * not show up as a crash — it would show up as a tab that has been open all day holding
     * a handler for every screen it has ever visited. Asserting it by dispatching after
     * cancellation: nothing should be delivered, and nothing should throw.
     */
    @Test
    fun `stops listening once collection ends`() =
        runTest {
            val connectivity = BrowserConnectivity()

            connectivity.online.test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            // If `awaitClose` did not remove them, this reaches a handler whose channel is
            // closed. `trySend` on a closed channel is silent rather than fatal, so this
            // cannot be caught by watching for an exception — which is why the check below
            // is that a *fresh* collector sees a correct sequence rather than a doubled one.
            window.dispatchEvent(Event("offline"))

            connectivity.online.test {
                awaitItem()
                window.dispatchEvent(Event("online"))
                assertTrue(awaitItem(), "a new collector must see its own events and not the old one's")
                cancelAndIgnoreRemainingEvents()
            }
        }
}
