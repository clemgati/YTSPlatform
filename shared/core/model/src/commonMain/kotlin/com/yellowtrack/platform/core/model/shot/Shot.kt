package com.yellowtrack.platform.core.model.shot

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class ShotId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): ShotId = ShotId(uuidV7().toString())
    }
}

/**
 * One photograph that has been promised.
 *
 * The reason this exists as a row rather than a line in a notes field is the family
 * formals at a wedding: twenty combinations of relatives, half of whom wander off between
 * groups, and a mother-of-the-bride who will remember for thirty years that the one with
 * her late father's side never happened.
 *
 * @param group who this shot belongs with — "Bride's family", "Groom's side", "Wedding
 *   party". Grouping is the whole point: a photographer works a group at a time and lets
 *   people go once their group is done, and a list ordered any other way keeps forty
 *   people standing in a field.
 * @param people who is needed in the frame, as they would be called for by name. Free
 *   text, because "Grandma Ruth + the twins" is what gets shouted across a lawn and no
 *   structured field survives contact with it.
 */
@Serializable
data class Shot(
    val id: ShotId,
    override val studioId: StudioId,
    val sessionId: SessionId,
    val description: String,
    val group: String? = null,
    val people: String? = null,
    /** Order within the group, so a list can be worked top to bottom. */
    val position: Int = 0,
    val isCaptured: Boolean = false,
    val capturedAt: Instant? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    /** The heading this shot sits under, with ungrouped shots collected at the end. */
    val groupOrUngrouped: String get() = group?.takeIf(String::isNotBlank) ?: UNGROUPED

    companion object {
        const val UNGROUPED = "Everything else"
    }
}
