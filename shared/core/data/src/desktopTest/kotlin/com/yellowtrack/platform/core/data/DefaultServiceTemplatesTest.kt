package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.data.internal.defaultServiceTemplatesForStudio
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.service.ServiceLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What a studio finds already there on first run.
 *
 * The shape of the work is seeded; the price is not. A seeded price would be invented, in
 * a currency the studio has not chosen, and would then be measured against the studio's
 * real pricing floor as though it meant something.
 */
class DefaultServiceTemplatesTest {
    private val now = Instant.fromEpochMilliseconds(1_781_100_000_000)
    private val templates = defaultServiceTemplatesForStudio(StudioId("studio-1"), now)

    @Test
    fun `no seeded template carries a price`() {
        templates.forEach { template ->
            assertNull(
                template.basePrice,
                "${template.name} was seeded with a price the application invented",
            )
        }
    }

    @Test
    fun `every business line the studio runs gets a starting template`() {
        val lines = templates.map { it.serviceLine }.toSet()

        assertTrue(ServiceLine.Wedding in lines)
        assertTrue(ServiceLine.Video in lines)
        assertTrue(ServiceLine.RealEstate in lines)
        assertTrue(ServiceLine.Headshot in lines)
    }

    @Test
    fun `the shape of the work is seeded, since it is the same in every country`() {
        val wedding = assertNotNull(templates.firstOrNull { it.serviceLine == ServiceLine.Wedding })

        assertEquals(10 * 60, wedding.defaultSessionDurationMinutes)
        assertEquals(2, wedding.defaultSessionCount, "the engagement shoot and the day itself")
        assertNotNull(wedding.defaultTurnaroundDays)
        assertNotNull(wedding.defaultDeliverableCount)
    }

    @Test
    fun `a real estate listing is turned around in a day, because that is the product`() {
        val listing = assertNotNull(templates.firstOrNull { it.serviceLine == ServiceLine.RealEstate })

        assertEquals(1, listing.defaultTurnaroundDays)
    }
}
