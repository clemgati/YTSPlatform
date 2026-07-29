package com.yellowtrack.platform.core.model.delivery

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class DeliverableId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): DeliverableId = DeliverableId(uuidV7().toString())
    }
}

/** What is being handed over. */
@Serializable
enum class DeliverableKind {
    Gallery,
    Album,
    Prints,
    Video,

    /** The original files, usually the thing a commercial client actually bought. */
    RawFiles,

    Other,
}

@Serializable
enum class DeliverableStatus {
    NotStarted,
    InProgress,

    /** With the client, awaiting their word. */
    Delivered,

    /** They have asked for changes. */
    InRevision,

    /** Signed off. Nothing further is owed on it. */
    Approved,
}

/**
 * Something promised to the client at the end of a job.
 *
 * The two figures that decide arguments are already in the contract and, until now, were
 * never compared against anything: [com.yellowtrack.platform.core.model.contract.Contract]
 * carries a turnaround in days and a number of revision rounds. A studio that has agreed
 * to both and tracks neither finds out it is late when the client says so, and gives away
 * a fourth revision on a two-revision contract because nobody was counting.
 *
 * @param revisionsUsed how many rounds of changes have been asked for and done. Compared
 *   against the contract's allowance, not stored against it — the contract is free to
 *   change, and the count of what actually happened should not change with it.
 */
@Serializable
data class Deliverable(
    val id: DeliverableId,
    override val studioId: StudioId,
    val projectId: ProjectId,
    val name: String,
    val kind: DeliverableKind = DeliverableKind.Gallery,
    val status: DeliverableStatus = DeliverableStatus.NotStarted,
    /**
     * When it is owed.
     *
     * Null where it can be computed instead — from the shoot date and the turnaround the
     * contract promised. Stored only when the studio overrides that.
     */
    val dueAt: Instant? = null,
    val deliveredAt: Instant? = null,
    val approvedAt: Instant? = null,
    val revisionsUsed: Int = 0,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    val isSettled: Boolean get() = status == DeliverableStatus.Approved

    /** Whether the client is still owed something. */
    val isOutstanding: Boolean get() = !isSettled

    /**
     * Late against a promised date.
     *
     * Anything already approved is never late, however long it took: the question a studio
     * needs answering is what is owed now, not what to feel bad about.
     */
    fun isOverdue(
        now: Instant,
        due: Instant?,
    ): Boolean = isOutstanding && due != null && now > due
}
