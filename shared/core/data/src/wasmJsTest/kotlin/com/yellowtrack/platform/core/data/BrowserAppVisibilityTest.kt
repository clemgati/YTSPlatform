package com.yellowtrack.platform.core.data

import app.cash.turbine.test
import com.yellowtrack.platform.core.data.sync.BrowserAppVisibility
import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import org.w3c.dom.events.Event
import kotlin.test.Test

/**
 * A tab being looked at again, driven by the real event in a real browser.
 *
 * Runs under `wasmJsBrowserTest`, so `document` is a genuine document and `dispatchEvent`
 * goes through the browser's own event machinery. The listener name and the state check are
 * the two things that compile perfectly while doing nothing, which is what this covers.
 */
class BrowserAppVisibilityTest {
    /**
     * Headless Chrome runs the suite in a visible document, so a `visibilitychange` dispatched
     * here is the "became visible" edge — which is the one that should reconcile.
     */
    @Test
    fun `reports the tab becoming visible again`() =
        runTest {
            BrowserAppVisibility().foregrounded.test {
                // Nothing is emitted on subscribe: the initial state is not a return, and a
                // platform reporting it would double the run sign-in already does.
                expectNoEvents()

                document.dispatchEvent(Event("visibilitychange"))

                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Listeners come off when collection ends.
     *
     * A web build's flow lives for as long as the tab, so a leak here would show up as an
     * afternoon's worth of handlers rather than as a crash.
     */
    @Test
    fun `stops listening once collection ends`() =
        runTest {
            val visibility = BrowserAppVisibility()

            visibility.foregrounded.test {
                document.dispatchEvent(Event("visibilitychange"))
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            document.dispatchEvent(Event("visibilitychange"))

            visibility.foregrounded.test {
                expectNoEvents()
                document.dispatchEvent(Event("visibilitychange"))
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
