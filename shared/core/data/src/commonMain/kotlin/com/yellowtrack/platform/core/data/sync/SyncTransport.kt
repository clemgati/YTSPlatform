package com.yellowtrack.platform.core.data.sync

import com.yellowtrack.platform.core.model.sync.SyncPullResponse
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import com.yellowtrack.platform.core.model.sync.SyncPushResult

/**
 * How the device reaches the server.
 *
 * An interface rather than an HTTP client, so reconciliation can be tested without a
 * network — the interesting questions on this side are what the device does with an answer,
 * and none of them need a socket to ask. `core:network` holds the real one.
 *
 * The envelopes are `core:model`'s, so this and the server's routes are compiled against
 * one definition of the contract rather than two that agree by inspection.
 */
interface SyncTransport {
    suspend fun pull(
        since: Long,
        limit: Int,
    ): SyncPullResponse

    suspend fun push(changes: SyncPushRequest): List<SyncPushResult>
}
