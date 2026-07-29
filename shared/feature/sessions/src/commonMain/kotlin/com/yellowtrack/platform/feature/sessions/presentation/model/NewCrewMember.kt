package com.yellowtrack.platform.feature.sessions.presentation.model

import com.yellowtrack.platform.core.model.crew.CrewRole

/**
 * What the crew form collected.
 *
 * [callTime] is blank when someone is simply due with everyone else, which is the common
 * case for a second shooter and almost never the case for hair and make-up.
 */
internal data class NewCrewMember(
    val name: String,
    val role: CrewRole,
    val phone: String,
    val callTime: String,
)

/**
 * The role as it would be written on a call sheet.
 *
 * Defined once and used by both the form and the detail page: two copies of a display
 * name drift the first time one of them is reworded.
 */
internal val CrewRole.label: String
    get() =
        when (this) {
            CrewRole.SecondShooter -> "Second shooter"
            CrewRole.Assistant -> "Assistant"
            CrewRole.Videographer -> "Videographer"
            CrewRole.MakeUp -> "Hair & make-up"
            CrewRole.Stylist -> "Stylist"
            CrewRole.Planner -> "Planner"
            CrewRole.Venue -> "Venue contact"
            CrewRole.Other -> "Other"
        }
