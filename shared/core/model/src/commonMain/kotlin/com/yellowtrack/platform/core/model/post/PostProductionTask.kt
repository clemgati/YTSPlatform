package com.yellowtrack.platform.core.model.post

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
value class PostProductionTaskId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): PostProductionTaskId = PostProductionTaskId(uuidV7().toString())
    }
}

/** The work that happens after the camera is put away. */
@Serializable
enum class PostTaskKind {
    /** Choosing which frames survive. Almost always underestimated. */
    Cull,

    Edit,
    Colour,
    Retouch,
    AlbumDesign,

    /** Exporting, uploading, and getting the gallery in front of the client. */
    Delivery,

    /** Emails, invoicing, chasing. Unbilled and rarely counted. */
    Admin,

    Other,
}

@Serializable
enum class PostTaskStatus {
    ToDo,
    InProgress,
    Done,
}

/**
 * A piece of post-production work on a booking, with what it was expected to take and
 * what it actually took.
 *
 * Held against the project rather than a session, because post-production belongs to the
 * booking as a whole: one wedding produces one cull and one edit however many days were
 * shot.
 *
 * The gap between [estimatedHours] and [actualHours] is the point of the record. The
 * pricing floor in `core:model`'s cost-of-doing-business currently assumes a shoot day
 * consumes roughly two further days of work — a reasonable guess, and only a guess. Enough
 * of these turn that assumption into a measurement taken from the studio's own history.
 */
@Serializable
data class PostProductionTask(
    val id: PostProductionTaskId,
    override val studioId: StudioId,
    val projectId: ProjectId,
    val name: String,
    val kind: PostTaskKind = PostTaskKind.Edit,
    val status: PostTaskStatus = PostTaskStatus.ToDo,
    val estimatedHours: Double? = null,
    val actualHours: Double? = null,
    val completedAt: Instant? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    val isDone: Boolean get() = status == PostTaskStatus.Done

    /**
     * How far over or under the estimate this ran, once it is finished.
     *
     * Null while the work is still going: a task half done has not overrun, it is simply
     * not finished, and treating the two alike would make every open job look late.
     */
    val hoursOverEstimate: Double?
        get() =
            if (isDone && estimatedHours != null && actualHours != null) {
                actualHours - estimatedHours
            } else {
                null
            }

    /** Whether this task can contribute to a measured post-production factor. */
    val isMeasured: Boolean get() = isDone && (actualHours ?: 0.0) > 0.0
}
