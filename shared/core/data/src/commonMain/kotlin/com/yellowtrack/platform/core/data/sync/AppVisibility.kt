package com.yellowtrack.platform.core.data.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * When the application comes back in front of somebody.
 *
 * The gap this closes is the one a studio actually notices. A phone spends most of its day
 * with this application in the background, where the periodic loop has usually backed off to
 * something long and the last run may be an hour old. Opening it shows whatever that run
 * left, and the studio reads a stale screen while a current one arrives silently a moment
 * later — or does not, because the interval has not elapsed.
 *
 * Neither existing trigger covers it. The timer is a clock and knows nothing about attention.
 * [Connectivity] fires when a *connection* returns, which is not the same event: a phone in a
 * pocket on good Wi-Fi all afternoon reports no change at all, and is exactly the case where
 * the screen is most out of date when it is next looked at.
 *
 * **Advisory, like [Connectivity], and for the same reason.** This only ever brings a run
 * forward. Nothing blocks on it, the periodic loop is untouched, and a platform that cannot
 * answer is left exactly as it was rather than told the application is always in front.
 */
interface AppVisibility {
    /**
     * Emits once each time the application becomes visible again.
     *
     * Deliberately [Unit] rather than a `Boolean`. The transition into the background is not
     * interesting — nothing should be scheduled for a device nobody is holding — and a flow
     * carrying both edges invites a collector to act on the wrong one.
     *
     * Not required to emit on first launch. Sign-in already runs a reconciliation, and a
     * platform that reports the initial state as a change would double it.
     */
    val foregrounded: Flow<Unit>

    companion object {
        /**
         * For platforms with no such moment, and for tests about something else.
         *
         * Desktop binds this deliberately rather than for want of an implementation. A window
         * regaining focus is not the same event: it happens every time somebody switches back
         * from a browser, many times an hour, and treating that as "the application was
         * reopened" would be a second timer with a worse trigger. A desktop application also
         * stays running, so its periodic loop has usually not backed off far, and a machine
         * waking from sleep is already noticed by `DesktopConnectivity`'s poll.
         */
        val Unknown: AppVisibility =
            object : AppVisibility {
                override val foregrounded: Flow<Unit> = emptyFlow()
            }
    }
}
