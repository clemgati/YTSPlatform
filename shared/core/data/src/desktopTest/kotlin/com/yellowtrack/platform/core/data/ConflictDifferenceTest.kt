package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.data.sync.differences
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.sync.SyncConflict
import com.yellowtrack.platform.core.model.sync.SyncConflictId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Turning two stored payloads into something a photographer can act on.
 *
 * The payloads are whole entities and the two devices agreed about nearly all of it, so
 * the useful answer is the handful of fields that moved. Showing both documents in full
 * would be complete and unreadable, which for this screen is the same as not showing them.
 */
class ConflictDifferenceTest {
    @Test
    fun `only the fields that actually moved are reported`() {
        val differences =
            conflict(
                losing = """{"title":"Ceremony — 2pm","timeZoneId":"Europe/London","notes":"Bring the long lens"}""",
                winning = """{"title":"Ceremony — 3pm","timeZoneId":"Europe/London","notes":"Bring the long lens"}""",
            ).differences()

        assertEquals(1, differences.size, "the two devices agreed about everything but the title")
        assertEquals("Title", differences.single().label)
        assertEquals("Ceremony — 2pm", differences.single().discarded)
        assertEquals("Ceremony — 3pm", differences.single().kept)
    }

    @Test
    fun `the bookkeeping fields are not reported, because they always move`() {
        val differences =
            conflict(
                losing = """{"title":"Ceremony","audit":{"version":3,"updatedAt":"2026-07-30T10:00:00Z"}}""",
                winning = """{"title":"Ceremony","audit":{"version":4,"updatedAt":"2026-07-30T11:00:00Z"}}""",
            ).differences()

        assertTrue(
            differences.isEmpty(),
            "version and updatedAt differ on every write by definition. Listing them would bury " +
                "the one field the studio changed under two it did not",
        )
    }

    @Test
    fun `a field added on one side and absent on the other is still a difference`() {
        val differences =
            conflict(
                losing = """{"title":"Ceremony","notes":"Bring the long lens"}""",
                winning = """{"title":"Ceremony"}""",
            ).differences()

        assertEquals("Notes", differences.single().label)
        assertEquals("Bring the long lens", differences.single().discarded)
        assertEquals("", differences.single().kept, "a note deleted on the other device is still a note lost")
    }

    @Test
    fun `a camel-cased field reads as the words on the form`() {
        val differences =
            conflict(
                losing = """{"accountName":"Ada Okafor"}""",
                winning = """{"accountName":"Ada Okafor-Bell"}""",
            ).differences()

        assertEquals("Account name", differences.single().label)
    }

    @Test
    fun `a payload that cannot be read yields nothing rather than throwing`() {
        val differences = conflict(losing = "not json at all", winning = """{"title":"Ceremony"}""").differences()

        assertTrue(
            differences.isEmpty(),
            "the screen shows the conflict anyway and says it could not be read. A conflict that " +
                "crashed the settings screen would be worse than one that renders plainly",
        )
    }

    private fun conflict(
        losing: String,
        winning: String,
    ) = SyncConflict(
        id = SyncConflictId("conflict-1"),
        studioId = TEST_STUDIO_ID,
        entityTable = "session",
        entityId = "session-1",
        losingPayload = losing,
        winningPayload = winning,
        detectedAt = TEST_NOW,
        audit = AuditMetadata.createdAt(TEST_NOW),
    )
}
