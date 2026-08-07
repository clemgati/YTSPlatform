package com.yellowtrack.platform.core.data.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Whether the device believes it can reach the network.
 *
 * Exists for one moment: the one where a connection comes back. Everything else about
 * reconciliation is already handled by the timer, and a studio that has been offline all
 * morning does not want to wait out the rest of an hour-long backoff to find out.
 *
 * **Deliberately advisory.** A device can hold a Wi-Fi association to a router with no route
 * to the internet, and every platform's answer here is a guess of some quality. So nothing
 * *blocks* on this: a write still attempts and still fails honestly, and the periodic loop
 * still runs on its own. This only ever brings a run forward, which is the same rule
 * [Synchroniser.startSyncOnWrite] follows and for the same reason — a trigger that can be
 * wrong must not be the only thing keeping a device current.
 */
interface Connectivity {
    /**
     * Emits whenever the answer changes. `true` means a connection is believed available.
     *
     * Not required to emit an initial value: [Synchroniser] acts on the transition into
     * `true`, so a platform that only reports changes is enough.
     */
    val online: Flow<Boolean>

    companion object {
        /**
         * For platforms that cannot answer, and for tests about something else.
         *
         * Emits nothing rather than guessing `true`. Claiming a connection that is not there
         * would produce a run that fails and lengthens the backoff — the opposite of the
         * point.
         */
        val Unknown: Connectivity =
            object : Connectivity {
                override val online: Flow<Boolean> = emptyFlow()
            }
    }
}
