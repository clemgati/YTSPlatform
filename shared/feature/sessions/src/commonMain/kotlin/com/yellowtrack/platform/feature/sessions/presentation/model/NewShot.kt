package com.yellowtrack.platform.feature.sessions.presentation.model

/**
 * What the shot form collected.
 *
 * [group] is free text rather than a fixed list, because the groupings that matter are the
 * ones a particular family has — "Dad's side", "The Kowalczyks", "Anyone still standing" —
 * and no enumeration the studio ships would survive meeting one.
 */
internal data class NewShot(
    val description: String,
    val group: String,
    val people: String,
)
