package com.yellowtrack.platform.feature.studio

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.gear.GearCategory
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.GearStatus
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.feature.studio.presentation.mapper.buildInventory
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * What an inventory is actually for.
 *
 * Nobody keeps a gear list to remember what cameras they own. They keep it so that a
 * claim, a year after a van is broken into, can be settled — which turns on serial
 * numbers and prices, not on names.
 */
class InventoryTest {
    private val usd = CurrencyCode.USD
    private val now = TestAppClock.DEFAULT_NOW
    private val studioId = LocalStudioContext.LOCAL_STUDIO_ID

    private fun gear(
        name: String = "Canon R5 body",
        category: GearCategory = GearCategory.Camera,
        status: GearStatus = GearStatus.InService,
        serial: String? = "042176",
        price: Long? = 389_900L,
        servicedDaysAgo: Long? = null,
    ) = GearItem(
        id = GearItemId.new(),
        studioId = studioId,
        name = name,
        category = category,
        status = status,
        serialNumber = serial,
        purchasePrice = price?.let { Money(it, usd) },
        lastServicedAt = servicedDaysAgo?.let { now - it.days },
        audit = AuditMetadata.createdAt(now),
    )

    private fun inventory(gear: List<GearItem>) = buildInventory(gear, now, TimeZone.UTC, usd)

    // --- What the studio is insured for -------------------------------------------------

    @Test
    fun `the insured total is what was paid for what is still owned`() {
        val summary =
            inventory(
                listOf(
                    gear(price = 389_900L),
                    gear(name = "24-70mm", category = GearCategory.Lens, price = 210_000L),
                ),
            )

        assertEquals(Money(599_900L, usd), summary.insuredValue)
    }

    @Test
    fun `gear sold or written off leaves the insured total`() {
        val summary =
            inventory(
                listOf(
                    gear(price = 389_900L),
                    gear(name = "Old 5D", status = GearStatus.Retired, price = 150_000L),
                ),
            )

        assertEquals(
            Money(389_900L, usd),
            summary.insuredValue,
            "insuring a camera that was sold two years ago is money spent for nothing",
        )
    }

    @Test
    fun `gear at the repair shop is still insured`() {
        val summary = inventory(listOf(gear(status = GearStatus.InRepair, price = 389_900L)))

        assertEquals(
            Money(389_900L, usd),
            summary.insuredValue,
            "a body away being fixed is still the studio's, and can still burn with the shop",
        )
    }

    @Test
    fun `a lost camera stays on the schedule because it is exactly what gets claimed for`() {
        val summary = inventory(listOf(gear(status = GearStatus.Lost, price = 389_900L)))

        assertEquals(Money(389_900L, usd), summary.insuredValue)
    }

    @Test
    fun `gear with no price is counted, so the total is not quietly wrong`() {
        val summary =
            inventory(
                listOf(
                    gear(price = 389_900L),
                    gear(name = "Backup body", price = null),
                ),
            )

        assertEquals(Money(389_900L, usd), summary.insuredValue)
        assertEquals(1, summary.itemsWithoutPrice, "a studio reading the total must know it is short")
    }

    // --- What loses a claim -------------------------------------------------------------

    @Test
    fun `priced gear with no serial number is called out`() {
        val summary = inventory(listOf(gear(name = "Canon R5 body", serial = null)))

        assertEquals(
            listOf("Canon R5 body"),
            summary.uninsurableNames,
            "an insurer settles on serial numbers; a description of a black camera settles nothing",
        )
    }

    @Test
    fun `a reflector with no serial number is not a problem`() {
        val summary =
            inventory(
                listOf(gear(name = "5-in-1 reflector", category = GearCategory.Modifier, serial = null, price = null)),
            )

        assertTrue(
            summary.uninsurableNames.isEmpty(),
            "gear with no price is not being claimed for, so its missing serial costs nothing",
        )
    }

    @Test
    fun `a serial number of blank space counts as none`() {
        val summary = inventory(listOf(gear(name = "Canon R5 body", serial = "   ")))

        assertEquals(listOf("Canon R5 body"), summary.uninsurableNames)
    }

    // --- Servicing ----------------------------------------------------------------------

    @Test
    fun `gear serviced over a year ago is mentioned`() {
        val summary = inventory(listOf(gear(name = "Canon R5 body", servicedDaysAgo = 400)))

        assertEquals(listOf("Canon R5 body"), summary.longUnservicedNames)
    }

    @Test
    fun `gear serviced last month is not`() {
        val summary = inventory(listOf(gear(servicedDaysAgo = 30)))

        assertTrue(summary.longUnservicedNames.isEmpty())
    }

    @Test
    fun `gear never serviced is left alone`() {
        val summary = inventory(listOf(gear(name = "5-in-1 reflector", servicedDaysAgo = null)))

        assertTrue(
            summary.longUnservicedNames.isEmpty(),
            "a reflector is not overdue a shutter count, and a list wrong about half its rows gets ignored",
        )
    }

    @Test
    fun `a retired body is not nagged about servicing`() {
        val summary = inventory(listOf(gear(status = GearStatus.Retired, servicedDaysAgo = 900)))

        assertTrue(summary.longUnservicedNames.isEmpty())
    }

    // --- What can be packed --------------------------------------------------------------

    @Test
    fun `gear away being repaired is named as unavailable`() {
        val summary =
            inventory(
                listOf(
                    gear(name = "Canon R5 body"),
                    gear(name = "Second body", status = GearStatus.InRepair),
                ),
            )

        assertEquals(1, summary.availableCount)
        assertEquals(listOf("Second body"), summary.unavailableNames)
    }

    @Test
    fun `retired gear is not offered as missing from Saturday`() {
        val summary = inventory(listOf(gear(name = "Old 5D", status = GearStatus.Retired)))

        assertTrue(
            summary.unavailableNames.isEmpty(),
            "gear the studio sold is not something it is short of",
        )
    }

    // --- How the list reads ---------------------------------------------------------------

    @Test
    fun `gear is grouped by kind and sorted within a group`() {
        val summary =
            inventory(
                listOf(
                    gear(name = "70-200mm", category = GearCategory.Lens),
                    gear(name = "Canon R5 body", category = GearCategory.Camera),
                    gear(name = "24-70mm", category = GearCategory.Lens),
                ),
            )

        assertEquals(listOf(GearCategory.Camera, GearCategory.Lens), summary.groups.map { it.category })
        assertEquals(
            listOf("24-70mm", "70-200mm"),
            summary.groups
                .last()
                .items
                .map { it.name },
        )
    }

    @Test
    fun `an empty studio reports nothing rather than a total of zero problems`() {
        val summary = inventory(emptyList())

        assertEquals(0, summary.itemCount)
        assertTrue(summary.insuredValue.isZero)
        assertTrue(summary.groups.isEmpty())
        assertFalse(summary.uninsurableNames.isNotEmpty())
    }
}
