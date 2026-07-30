package com.yellowtrack.platform.core.model.media

import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The 3-2-1 rule, checked rather than believed.
 *
 * Each clause guards a different way of losing a wedding: one copy fails, two copies of
 * the same kind fail together, and everything in one room burns at once. The tests are
 * written as the ways a studio actually gets this wrong.
 */
class BackupHealthTest {
    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000)
    private val sessionId = SessionId("session-1")

    private fun copy(
        kind: StorageKind,
        offsite: Boolean = false,
        verified: Boolean = true,
    ) = MediaCopy(
        id = MediaCopyId.new(),
        studioId = StudioId("studio-1"),
        sessionId = sessionId,
        volumeName = kind.name,
        kind = kind,
        isOffsite = offsite,
        copiedAt = now,
        verifiedAt = now.takeIf { verified },
        audit = AuditMetadata.createdAt(now),
    )

    @Test
    fun `three copies on two kinds with one away satisfies the rule`() {
        val health =
            BackupHealth.of(
                listOf(
                    copy(StorageKind.Computer),
                    copy(StorageKind.ExternalDrive),
                    copy(StorageKind.Cloud),
                ),
            )

        assertTrue(health.isSatisfied)
        assertTrue(health.shortfalls.isEmpty())
    }

    @Test
    fun `the card still in the bag is not a backup`() {
        val health =
            BackupHealth.of(
                listOf(
                    copy(StorageKind.CameraCard),
                    copy(StorageKind.CameraCard),
                    copy(StorageKind.Computer),
                ),
            )

        assertEquals(1, health.copies, "the card is the original, not a copy of it")
        assertFalse(health.isSatisfied)
    }

    @Test
    fun `two external drives on the same desk are not two kinds`() {
        val health =
            BackupHealth.of(
                listOf(
                    copy(StorageKind.ExternalDrive),
                    copy(StorageKind.ExternalDrive),
                    copy(StorageKind.ExternalDrive),
                ),
            )

        assertTrue(health.hasEnoughCopies)
        assertFalse(health.hasEnoughKinds, "drives bought together fail together")
        assertFalse(health.hasOffsite)
        assertFalse(health.isSatisfied)
    }

    @Test
    fun `cloud storage counts as away without being marked so`() {
        val health = BackupHealth.of(listOf(copy(StorageKind.Cloud)))

        assertEquals(1, health.offsiteCopies, "a cloud copy is not in the building by definition")
        assertTrue(health.hasOffsite)
    }

    @Test
    fun `a drive kept elsewhere counts as away when it is marked so`() {
        val health = BackupHealth.of(listOf(copy(StorageKind.ExternalDrive, offsite = true)))

        assertEquals(1, health.offsiteCopies)
    }

    @Test
    fun `nothing recorded says so rather than reporting a shortfall of three`() {
        val health = BackupHealth.of(emptyList())

        assertEquals(listOf("No copies recorded at all", "Nothing is off the premises"), health.shortfalls)
    }

    @Test
    fun `what is missing is listed in the order it should be fixed`() {
        // One copy, on the studio machine: everything is wrong at once.
        val health = BackupHealth.of(listOf(copy(StorageKind.Computer)))

        assertEquals(
            listOf("2 more copies needed", "Nothing is off the premises"),
            health.shortfalls,
            "a second copy anywhere beats a third, and getting one out of the building beats " +
                "spreading copies across more drives in the same room",
        )
    }

    @Test
    fun `enough copies in the same room still reports what is wrong`() {
        val health =
            BackupHealth.of(
                listOf(
                    copy(StorageKind.Computer),
                    copy(StorageKind.ExternalDrive),
                    copy(StorageKind.Nas),
                ),
            )

        assertTrue(health.hasEnoughCopies)
        assertTrue(health.hasEnoughKinds)
        assertEquals(listOf("Nothing is off the premises"), health.shortfalls)
    }

    @Test
    fun `copies nobody has opened are counted separately`() {
        val health =
            BackupHealth.of(
                listOf(
                    copy(StorageKind.Computer, verified = true),
                    copy(StorageKind.ExternalDrive, verified = false),
                    copy(StorageKind.Cloud, verified = false),
                ),
            )

        assertTrue(health.isSatisfied, "the rule is about where the copies are, not whether they were checked")
        assertEquals(
            2,
            health.unverifiedCopies,
            "a backup nobody has opened is a backup nobody knows they have",
        )
    }
}
