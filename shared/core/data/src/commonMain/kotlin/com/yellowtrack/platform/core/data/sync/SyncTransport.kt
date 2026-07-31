package com.yellowtrack.platform.core.data.sync

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.sync.SyncConflict

/** What the server sends down. Mirrors the server's own `PullResponse`. */
data class PullPage(
    val cursor: Long,
    val hasMore: Boolean,
    val clients: List<Client> = emptyList(),
    val projects: List<Project> = emptyList(),
    val sessions: List<Session> = emptyList(),
    /** Work reconciliation discarded. Travels down only — the device cannot raise one. */
    val conflicts: List<SyncConflict> = emptyList(),
)

/** What the device sends up. */
data class ChangesToPush(
    val clients: List<Client> = emptyList(),
    val projects: List<Project> = emptyList(),
    val sessions: List<Session> = emptyList(),
) {
    val isEmpty: Boolean get() = clients.isEmpty() && projects.isEmpty() && sessions.isEmpty()
}

/** What became of one pushed row, as the server reported it. */
data class PushAck(
    val entityTable: String,
    val entityId: String,
    val outcome: PushOutcome,
    val version: Int,
    val detail: String? = null,
)

enum class PushOutcome {
    Applied,
    Conflicted,

    /**
     * The server would not take it. The entry stays in the outbox with its failure
     * recorded, because a rejection is a bug to be looked at rather than work to discard.
     */
    Rejected,

    /**
     * The server never answered about this row. Not a server outcome — what [SyncEngine]
     * uses when a push fails partway, so the entry is kept and tried again.
     */
    Unanswered,
}

/**
 * How the device reaches the server.
 *
 * An interface rather than a Ktor client, so the reconciliation logic can be tested
 * without a network or a server. The Ktor implementation arrives with the client wiring;
 * what matters first is that the device does the right thing with what comes back, which
 * is the part where mistakes are silent.
 */
interface SyncTransport {
    suspend fun pull(
        since: Long,
        limit: Int,
    ): PullPage

    suspend fun push(changes: ChangesToPush): List<PushAck>
}
