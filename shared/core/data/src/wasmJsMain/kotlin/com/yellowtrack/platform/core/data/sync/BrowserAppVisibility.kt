package com.yellowtrack.platform.core.data.sync

import kotlinx.browser.document
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A tab being looked at again.
 *
 * `visibilitychange` rather than window `focus`, because a tab can be visible while another
 * window has focus — two windows side by side is ordinary on a desktop browser, and clicking
 * between them would otherwise report a return to an application that never went away.
 *
 * This matters more here than on the other platforms and for a reason particular to the web
 * build: its database is in memory. A tab left open for hours holds whatever the last run
 * pulled, and a tab restored from the back/forward cache resumes with no reload and no event
 * of its own beyond this one.
 *
 * Only the transition into `visible` is reported. Going hidden is not interesting: nothing
 * should be scheduled for a tab nobody is looking at.
 */
class BrowserAppVisibility : AppVisibility {
    override val foregrounded: Flow<Unit> =
        callbackFlow {
            val onVisibilityChange: (org.w3c.dom.events.Event) -> Unit = {
                if (documentIsVisible()) trySend(Unit)
            }

            document.addEventListener("visibilitychange", onVisibilityChange)
            awaitClose { document.removeEventListener("visibilitychange", onVisibilityChange) }
        }
}

/**
 * `document.visibilityState` is not on Kotlin/wasm's `Document`, so it is read through JS.
 *
 * The event fires for both edges and carries no payload, so the state has to be asked for —
 * without this, going *hidden* would report a return to the application.
 */
private fun documentIsVisible(): Boolean = js("document.visibilityState === 'visible'")
