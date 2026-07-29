package com.yellowtrack.platform.core.model.gear

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class PackingEntryId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): PackingEntryId = PackingEntryId(uuidV7().toString())
    }
}

/**
 * A piece of gear taken to a shoot.
 *
 * [isReturned] is tracked separately from [isPacked] because they fail at opposite ends of
 * the day. Packing is checked in a calm studio; returning is checked in the dark, at the
 * end of a fourteen-hour wedding, which is exactly when a light stand gets left behind a
 * curtain and is not missed until the next booking.
 */
@Serializable
data class PackingEntry(
    val id: PackingEntryId,
    override val studioId: StudioId,
    val sessionId: SessionId,
    val gearItemId: GearItemId,
    val isPacked: Boolean = false,
    val isReturned: Boolean = false,
    override val audit: AuditMetadata,
) : StudioScoped {
    /** Taken out and not yet accounted for — the only state worth chasing. */
    val isMissing: Boolean get() = isPacked && !isReturned
}
