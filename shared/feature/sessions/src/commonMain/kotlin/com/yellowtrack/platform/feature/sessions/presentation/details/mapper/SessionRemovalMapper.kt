package com.yellowtrack.platform.feature.sessions.presentation.details.mapper

import com.yellowtrack.platform.core.ui.removal.Removal
import com.yellowtrack.platform.core.ui.removal.heldBy

/**
 * Works out what is holding a shoot day in place.
 *
 * Five kinds of record point at a session and none of them can exist without it. As
 * everywhere else, nothing cascades — but the reason bites harder here than it does for a
 * booking, because two of the five are the things a studio would be sued over losing.
 *
 * A backup row is the record of *where the client's photographs are*. A talent release is
 * the written permission to use somebody's face. Neither is recoverable from anywhere else
 * in the application, and neither should be capable of disappearing because a shoot day was
 * tidied away — so both are named first, and a day carrying either says so.
 *
 * Crew, shots and packed gear are planning rather than evidence, but they still hold the
 * day: they are work somebody entered, and quietly discarding entered work is the failure
 * this whole sweep is about.
 */
internal fun sessionRemoval(
    backups: Int,
    releases: Int,
    crew: Int,
    shots: Int,
    packedItems: Int,
): Removal =
    heldBy(
        Removal.Hold("backup", "backups", backups),
        Removal.Hold("release", "releases", releases),
        Removal.Hold("crew member", "crew members", crew),
        Removal.Hold("shot", "shots", shots),
        Removal.Hold("packed item", "packed items", packedItems),
    )
