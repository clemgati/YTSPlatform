package com.yellowtrack.platform.core.model.sync

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class SyncConflictId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): SyncConflictId = SyncConflictId(uuidV7().toString())
    }
}

/**
 * Work that reconciliation discarded, kept so somebody can get it back.
 *
 * Two devices edited the same booking while one of them was offline. One version had to
 * lose, and this is it. `docs/adr/0008-synchronisation-semantics.md` decision 3 is explicit
 * that last-write-wins is only defensible while the discarded version survives somewhere
 * the person who wrote it can find it — silent last-write-wins is a data-loss feature
 * wearing the costume of a synchronisation strategy.
 *
 * Raised by the server, which is the only party that sees both versions, and pulled down
 * like any other row so that a conflict raised while a phone was in a bag is waiting when
 * it comes out.
 *
 * @param losingPayload the version that was discarded, serialised whole. Not a diff: what
 *   a studio needs is to read what it lost and retype the part that mattered, and a diff of
 *   two JSON documents is not that.
 * @param resolvedAt when somebody dealt with it. The row stays afterwards, because what was
 *   discarded is a fact about the studio's history and a resolved conflict that vanishes is
 *   a question nobody can re-ask.
 */
@Serializable
data class SyncConflict(
    val id: SyncConflictId,
    override val studioId: StudioId,
    /** Which table, matching the server's own name for it — `client`, `project`, `session`. */
    val entityTable: String,
    val entityId: String,
    val losingPayload: String,
    val winningPayload: String,
    val detectedAt: Instant,
    val resolvedAt: Instant? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    val isResolved: Boolean get() = resolvedAt != null
}
