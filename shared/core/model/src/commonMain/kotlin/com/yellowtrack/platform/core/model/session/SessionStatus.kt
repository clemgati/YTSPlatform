package com.yellowtrack.platform.core.model.session

import kotlinx.serialization.Serializable

/** Where a scheduled block stands. */
@Serializable
enum class SessionStatus {
    /** On the calendar, not yet confirmed with the client. */
    Scheduled,

    /** Confirmed with the client. Weather and travel are now your problem. */
    Confirmed,

    /** Underway. */
    InProgress,

    /** Shot. Media may not yet be offloaded. */
    Completed,

    /** Moved to a new date. The original block is kept for history. */
    Postponed,

    Cancelled,
    ;

    val occupiesCalendar: Boolean
        get() = this in setOf(Scheduled, Confirmed, InProgress)
}
