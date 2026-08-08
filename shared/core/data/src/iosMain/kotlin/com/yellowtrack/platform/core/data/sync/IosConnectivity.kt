package com.yellowtrack.platform.core.data.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_get_global_queue

/**
 * `NWPathMonitor`, which reports the first path as soon as it starts.
 *
 * `nw_path_status_satisfied` is Apple's "a route exists for this path", which is the same
 * distinction Android's VALIDATED capability draws and stops short of proving the far end
 * answers. That is the right level for this: [Connectivity] is advisory, and a run that turns
 * out to be pointless costs one request.
 *
 * On its own global queue rather than the main one. The handler does nothing but hand a boolean
 * to a flow, and a photographer's phone reports path changes while the interface is drawing.
 *
 * ## What was measured, and what could not be
 *
 * Toggling the host's Wi-Fi with the Simulator running, sampling the host's default route
 * every two seconds:
 *
 * ```
 * 13:39:20  route=en0     baseline; the monitor reports satisfied
 * 13:39:52  route=none    genuinely offline, and stays offline for 29 seconds
 * 13:40:21  route=en0     back
 * ```
 *
 * The monitor reported `satisfied` once at start and never fired again — straight through the
 * offline window. That is **not** this class losing an update. The same window watched with a
 * bare `nw_path_monitor`, with no flow, channel or dispatcher anywhere near it, behaved
 * identically: one call at start and nothing after. The Simulator does not deliver path
 * updates for host network changes.
 *
 * So the transition is unverified here and *cannot* be verified here. What is verified is that
 * the monitor starts, reports its first path, and can be cancelled and started again — see
 * `IosConnectivityTest`, both of whose tests fail if `nw_path_monitor_start` is removed.
 * Proving the transition needs a real device.
 *
 * Two earlier attempts at this measurement read as a broken monitor and were a broken
 * measurement: the machine failed over to a tethered iPhone on `en7` about two seconds after
 * the radio went off, so it never went offline at all and `satisfied` was the correct answer
 * throughout. Listing which interfaces hold an address beforehand does not catch that — `en7`
 * is dormant and unaddressed until the failover gives it a route. Sample `route -n get default`
 * across the whole window instead, and treat a measurement without that ground truth as void.
 * The same trap cost three readings on [DesktopConnectivity].
 */
@OptIn(ExperimentalForeignApi::class)
class IosConnectivity : Connectivity {
    override val online: Flow<Boolean> =
        callbackFlow {
            val monitor = nw_path_monitor_create()

            nw_path_monitor_set_update_handler(monitor) { path ->
                trySend(nw_path_get_status(path) == nw_path_status_satisfied)
            }
            nw_path_monitor_set_queue(monitor, dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u))
            nw_path_monitor_start(monitor)

            awaitClose { nw_path_monitor_cancel(monitor) }
        }
}
