package com.yellowtrack.platform.feature.dashboard

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.GearStatus
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.model.media.VolumeStatus
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.feature.dashboard.presentation.mapper.buildStudioStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Studio Status, which said "No studio readiness items configured" and offered nowhere to
 * configure any.
 *
 * The view model handed the section an empty list with a note saying readiness tracking
 * would arrive with the Studio milestone. That milestone shipped — gear, drives and
 * lighting are all recorded — and this was never connected to it, so the dashboard has been
 * asking a question no screen in the application could answer.
 *
 * Nothing is configured now either. These are read from what the studio already keeps,
 * which is the only version that stays true: a checklist a studio ticks itself is a
 * checklist it stops ticking.
 */
class StudioReadinessTest {
    @Test
    fun `a studio with nothing recorded is asked nothing`() {
        assertTrue(
            buildStudioStatus(gear = emptyList(), volumes = emptyList()).items.isEmpty(),
            "an empty studio has no readiness to report, and inventing ticks for kit nobody " +
                "has entered would be the invented checkboxes this replaced",
        )
    }

    @Test
    fun `gear out of service is counted`() {
        val status =
            buildStudioStatus(
                gear = listOf(gear("g1"), gear("g2", GearStatus.InRepair)),
                volumes = emptyList(),
            )

        assertEquals("1 item out of service", status.items.first().title)
        assertTrue(!status.items.first().ready)
    }

    @Test
    fun `gear worth money with no serial number is what an insurer asks for`() {
        val status =
            buildStudioStatus(
                gear = listOf(gear("g1", serial = null)),
                volumes = emptyList(),
            )

        val insurance = status.items.first { it.title.contains("identified") || it.title.contains("serial") }
        assertEquals(
            "1 item worth money cannot be identified",
            insurance.title,
            "the field an insurer asks for and nobody checks until they need it",
        )
    }

    @Test
    fun `a drive nobody has read is the one that matters`() {
        val status =
            buildStudioStatus(
                gear = emptyList(),
                volumes = listOf(volume("v1", lastChecked = null)),
            )

        assertEquals(
            "1 drive has never been read",
            status.items.single().title,
            "a backup nobody has opened is the difference between a copy a studio has and " +
                "one it believes it has",
        )
    }

    @Test
    fun `a studio in good order is told so`() {
        val status =
            buildStudioStatus(
                gear = listOf(gear("g1")),
                volumes = listOf(volume("v1", lastChecked = TestAppClock.DEFAULT_NOW)),
            )

        assertTrue(status.items.all { it.ready }, "every line reads as ready: ${status.items.map { it.title }}")
        assertEquals(3, status.items.size, "gear availability, gear identification, and drives")
    }

    @Test
    fun `gear the studio no longer owns is not counted against it`() {
        val status =
            buildStudioStatus(
                gear = listOf(gear("g1"), gear("g2", GearStatus.Retired, serial = null)),
                volumes = emptyList(),
            )

        assertTrue(
            status.items.all { it.ready },
            "a retired body is not out of service and not uninsurable; it is off the schedule: ${status.items.map {
                it.title
            }}",
        )
    }

    private fun gear(
        id: String,
        status: GearStatus = GearStatus.InService,
        serial: String? = "04127634",
    ) = GearItem(
        id = GearItemId(id),
        studioId = STUDIO,
        name = "Canon R5 body",
        status = status,
        serialNumber = serial,
        purchasePrice = Money(minorUnits = 389_900, currency = CurrencyCode.USD),
        audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
    )

    private fun volume(
        id: String,
        lastChecked: kotlin.time.Instant?,
    ) = StorageVolume(
        id = StorageVolumeId(id),
        studioId = STUDIO,
        label = "Shoot SSD 1",
        kind = StorageKind.ExternalDrive,
        status = VolumeStatus.InUse,
        lastCheckedAt = lastChecked,
        audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
    )

    private companion object {
        val STUDIO = StudioId("studio-1")
    }
}
