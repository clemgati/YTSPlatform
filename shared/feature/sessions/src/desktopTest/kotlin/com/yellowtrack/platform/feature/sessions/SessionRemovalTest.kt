package com.yellowtrack.platform.feature.sessions

import com.yellowtrack.platform.core.ui.removal.Removal
import com.yellowtrack.platform.feature.sessions.presentation.details.mapper.sessionRemoval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * What holds a shoot day in place.
 *
 * Five kinds of record point at a session and none can exist without it, so nothing
 * cascades — but the reason bites harder here than for a booking. A backup row is the
 * record of *where the client's photographs are*, and a talent release is the written
 * permission to use somebody's face. Neither is recoverable from anywhere else in the
 * application, and neither should be capable of vanishing because a day was tidied away.
 *
 * That is why both are named before the planning artefacts: the first reason a studio reads
 * is the one it weighs the decision against.
 */
class SessionRemovalTest {
    @Test
    fun `a day with nothing recorded on it can be removed`() {
        assertEquals(Removal.Available, removal())
    }

    @Test
    fun `a backup holds the day, because it is the only record of where the photographs are`() {
        val held = removal(backups = 1)

        assertIs<Removal.HeldBy>(held)
        assertEquals("1 backup", held.summary)
    }

    @Test
    fun `a signed release holds the day`() {
        val held = removal(releases = 2)

        assertIs<Removal.HeldBy>(held)
        assertEquals(
            "2 releases",
            held.summary,
            "consent to use somebody's face is not something to lose by tidying a calendar",
        )
    }

    @Test
    fun `evidence is named before planning`() {
        val held = removal(shots = 4, packedItems = 2, backups = 1)

        assertIs<Removal.HeldBy>(held)
        assertEquals(
            "1 backup, 4 shots and 2 packed items",
            held.summary,
            "a studio reading this decides on the first item; the backup is the one that " +
                "matters and a shot list is not",
        )
    }

    @Test
    fun `a leftover packing entry is still enough to stop it`() {
        val held = removal(packedItems = 1)

        assertIs<Removal.HeldBy>(held)
        assertEquals(
            "1 packed item",
            held.summary,
            "planning is work somebody entered, and quietly discarding entered work is the " +
                "failure this whole sweep is about",
        )
    }

    @Test
    fun `every kind of attachment is counted`() {
        val held = removal(backups = 1, releases = 1, crew = 1, shots = 1, packedItems = 1)

        assertIs<Removal.HeldBy>(held)
        assertEquals(
            5,
            held.holds.size,
            "a kind of record that points at a session but is not checked here is a silent " +
                "orphan waiting to happen",
        )
    }

    private fun removal(
        backups: Int = 0,
        releases: Int = 0,
        crew: Int = 0,
        shots: Int = 0,
        packedItems: Int = 0,
    ) = sessionRemoval(
        backups = backups,
        releases = releases,
        crew = crew,
        shots = shots,
        packedItems = packedItems,
    )
}
