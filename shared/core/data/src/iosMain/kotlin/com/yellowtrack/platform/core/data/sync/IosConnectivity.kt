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
