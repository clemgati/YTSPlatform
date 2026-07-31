package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.data.sync.SyncTransport
import com.yellowtrack.platform.core.model.sync.SyncPullResponse
import com.yellowtrack.platform.core.model.sync.SyncPushOutcome
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import com.yellowtrack.platform.core.model.sync.SyncPushResult

/**
 * A server that does as it is told.
 *
 * Stands in for the real one so the device half of reconciliation can be tested without a
 * network — the interesting questions there are what the device does with an answer, not
 * how the answer travelled.
 *
 * [pushed] records what was sent, which is how tests check the things a device gets wrong
 * silently: uploading a row three times because three edits were queued, or failing to
 * upload it at all.
 *
 * Lives here rather than in `core:testing`, which depends on this module and so cannot be
 * depended on by it. It moves there the first time a feature module needs it.
 */
class FakeSyncTransport(
    private val onPush: (SyncPushRequest) -> List<SyncPushResult> = ::acceptEverything,
) : SyncTransport {
    val pushed = mutableListOf<SyncPushRequest>()
    val pullsSince = mutableListOf<Long>()

    /** Pages handed back in order, one per call. An empty list means "nothing new". */
    var pages: MutableList<SyncPullResponse> = mutableListOf()

    /** Set to throw from the next push, standing in for a connection that drops. */
    var failNextPush: Throwable? = null

    override suspend fun pull(
        since: Long,
        limit: Int,
    ): SyncPullResponse {
        pullsSince += since
        return if (pages.isEmpty()) SyncPullResponse(cursor = since, hasMore = false) else pages.removeAt(0)
    }

    override suspend fun push(changes: SyncPushRequest): List<SyncPushResult> {
        failNextPush?.let {
            failNextPush = null
            throw it
        }

        pushed += changes
        return onPush(changes)
    }

    companion object {
        fun acceptEverything(changes: SyncPushRequest): List<SyncPushResult> =
            buildList {
                changes.clients.forEach {
                    add(SyncPushResult("client", it.id.value, SyncPushOutcome.Applied, it.audit.version))
                }
                changes.projects.forEach {
                    add(SyncPushResult("project", it.id.value, SyncPushOutcome.Applied, it.audit.version))
                }
                changes.sessions.forEach {
                    add(SyncPushResult("session", it.id.value, SyncPushOutcome.Applied, it.audit.version))
                }
            }
    }
}
