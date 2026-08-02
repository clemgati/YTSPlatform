package com.yellowtrack.platform.core.model.sync

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import kotlinx.serialization.Serializable

/**
 * The synchronisation contract, defined once and compiled into both sides.
 *
 * It was defined twice until this file existed — once in the server's routes and once in
 * `core:data` — which is precisely the arrangement ADR 0007 chose a Kotlin server to avoid.
 * Two definitions of one contract agree right up until somebody edits one of them, and the
 * disagreement shows up as a field that silently stops crossing rather than as a build
 * failure.
 *
 * These carry `core:model` types rather than opaque payloads. A generic envelope would
 * extend to the remaining eighteen entities without a code change and would throw away the
 * only thing that dependency buys: adding a field to `Session` is a compile error in both
 * halves at once. Growing this by one list per entity is the cost of keeping that.
 *
 * Transport-shaped rather than domain-shaped, and kept in its own package for that reason —
 * ADR 0008 was wary of the domain model acquiring concerns about how data moves. These
 * describe an envelope, not a photography business.
 */
@Serializable
data class SyncPullResponse(
    /** Where the device should resume. Unchanged when nothing came back. */
    val cursor: Long,
    /** Whether more remains beyond this page, so the device knows to come again. */
    val hasMore: Boolean,
    val clients: List<Client> = emptyList(),
    val projects: List<Project> = emptyList(),
    val sessions: List<Session> = emptyList(),
    /**
     * Work reconciliation discarded, travelling down only.
     *
     * There is no matching list on [SyncPushRequest]: the server is the only party that
     * ever sees both versions, so a device asserting a conflict would be claiming something
     * it cannot know.
     */
    val conflicts: List<SyncConflict> = emptyList(),
)

@Serializable
data class SyncPushRequest(
    val clients: List<Client> = emptyList(),
    val projects: List<Project> = emptyList(),
    val sessions: List<Session> = emptyList(),
) {
    val isEmpty: Boolean get() = clients.isEmpty() && projects.isEmpty() && sessions.isEmpty()
}

/** What became of one pushed row. */
@Serializable
enum class SyncPushOutcome {
    /** Stored. Nothing was discarded. */
    Applied,

    /**
     * Stored, and something was discarded to store it — or refused because a tombstone beat
     * it. Either way a conflict now holds both versions.
     */
    Conflicted,

    /** Not stored, and nothing was lost, because the push should not have been made. */
    Rejected,
}

@Serializable
data class SyncPushResult(
    val entityTable: String,
    val entityId: String,
    val outcome: SyncPushOutcome,
    /** The version now on the server, so the device can stop being behind. */
    val version: Int,
    val detail: String? = null,
)

@Serializable
data class SyncPushResponse(
    val results: List<SyncPushResult> = emptyList(),
) {
    /** So a device can say "three of your changes were also made elsewhere" without counting. */
    val conflicted: Int get() = results.count { it.outcome == SyncPushOutcome.Conflicted }
}
