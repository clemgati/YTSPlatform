package com.yellowtrack.platform.core.model.gear

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class LightingRecipeId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): LightingRecipeId = LightingRecipeId(uuidV7().toString())
    }
}

/** What a light is doing in the set-up, which is what makes a recipe repeatable. */
@Serializable
enum class LightRole {
    Key,
    Fill,

    /** Separating the subject from what is behind them. */
    Rim,

    Background,

    /** Bounced off a wall or ceiling rather than aimed. */
    Bounce,
}

/**
 * One light in a set-up.
 *
 * @param power as the studio reads it off the back of the light — "1/4", "6.3", "60%".
 *   Free text because every manufacturer numbers this differently and a normalised figure
 *   would have to be converted back before anyone could dial it in.
 * @param position where it stands, in the terms used on the day: "camera left, 45°, just
 *   above eye line". A structured coordinate would be precise and useless.
 */
@Serializable
data class LightSetup(
    val role: LightRole,
    val instrument: String,
    val modifier: String? = null,
    val power: String? = null,
    val position: String? = null,
    val distance: String? = null,
)

/**
 * A lighting set-up worth repeating.
 *
 * Photographers rebuild the same three-light headshot set-up from memory a hundred times
 * and get a slightly different result each time. Written down, it is a starting point that
 * takes ten minutes instead of forty.
 */
@Serializable
data class LightingRecipe(
    val id: LightingRecipeId,
    override val studioId: StudioId,
    val name: String,
    val lights: List<LightSetup> = emptyList(),
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    val lightCount: Int get() = lights.size
}
