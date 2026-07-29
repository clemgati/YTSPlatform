package com.yellowtrack.platform.core.model.crew

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
value class CrewMemberId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): CrewMemberId = CrewMemberId(uuidV7().toString())
    }
}

/** What someone is on the day, which decides when they are needed. */
@Serializable
enum class CrewRole {
    SecondShooter,
    Assistant,
    Videographer,

    /** Hair and make-up, who are almost always called first and leave earliest. */
    MakeUp,

    Stylist,

    /** The client's planner or coordinator, who runs the schedule everyone else follows. */
    Planner,

    /** Venue or location contact — who to ring when nobody answers the door. */
    Venue,

    Other,
}

/**
 * Someone working a shoot day.
 *
 * Held per session rather than as a studio-wide directory because that is how the work
 * actually arrives: a second shooter booked for one wedding, a make-up artist the client
 * brought, a videographer nobody has met. A directory of regulars is worth having and is
 * not this — it would link to `Contact`, and can arrive later without moving anything here.
 *
 * @param callTime when this person is due, which is the whole reason a call sheet exists.
 * Make-up is called hours before the photographer; the videographer arrives after; a
 * single time for everyone is a call sheet nobody can use.
 */
@Serializable
data class CrewMember(
    val id: CrewMemberId,
    override val studioId: StudioId,
    val sessionId: SessionId,
    val name: String,
    val role: CrewRole,
    val phone: String? = null,
    val callTime: Instant? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped
