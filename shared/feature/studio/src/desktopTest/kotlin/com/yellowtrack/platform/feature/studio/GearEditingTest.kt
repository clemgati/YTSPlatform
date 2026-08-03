package com.yellowtrack.platform.feature.studio

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.gear.GearCategory
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.GearStatus
import com.yellowtrack.platform.core.testing.FakeGearRepository
import com.yellowtrack.platform.core.testing.FakeLightingRecipeRepository
import com.yellowtrack.platform.core.testing.FakeStorageVolumeRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.studio.presentation.StudioViewModel
import com.yellowtrack.platform.feature.studio.presentation.model.NewGearItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Gear can be corrected, rather than only added and thrown away.
 *
 * Gear could be entered and deleted since 0.2.0 and never edited. That made a mistyped
 * serial number — the field an insurer needs, and the one nobody checks twice — fixable
 * only by deleting the item and entering it again, which loses its service history along
 * with the mistake.
 *
 * The identity is the point of the tests below. An edit that writes a new row leaves the
 * old one behind and reads, on the screen, exactly like a duplicate the studio typed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GearEditingTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `correcting a serial number keeps the same item`() =
        runTest {
            val gear = FakeGearRepository(listOf(body()))
            val viewModel = viewModel(gear)

            viewModel.saveGearItem(form(serialNumber = "04127634"), existingId = ID)

            val items = gear.observeGear().first()
            assertEquals(1, items.size, "an edit corrects the item; it does not add a second one")
            assertEquals("04127634", items.single().serialNumber)
            assertEquals(ID, items.single().id, "the identifier is what every other device knows it by")
        }

    /**
     * Opening the form and saving it unchanged must change nothing.
     *
     * The form is what the edit is built from, so any field of `GearItem` the form does not
     * carry is silently wiped the first time a studio corrects a typo — and the field most
     * likely to be forgotten is the service date, which is not on the form's mind because
     * it is set by a different button entirely.
     *
     * Round-tripping through the real screen model is the only version of this test that
     * keeps working: a hand-built form would be updated along with the field and would go
     * on passing while the application lost data.
     */
    @Test
    fun `opening the form and saving it back changes nothing`() =
        runTest {
            val stored = body().copy(lastServicedAt = TestAppClock.DEFAULT_NOW, notes = "Second body")
            val gear = FakeGearRepository(listOf(stored))
            val viewModel = viewModel(gear)

            viewModel.saveGearItem(viewModel.gearItem().editable, existingId = ID)

            val after = gear.observeGear().first().single()
            assertEquals(stored.lastServicedAt, after.lastServicedAt, "the service history survives an edit")
            assertEquals(stored.purchasePrice, after.purchasePrice)
            assertEquals(stored.purchasedOn, after.purchasedOn)
            assertEquals(stored.serialNumber, after.serialNumber)
            assertEquals(stored.notes, after.notes)
            assertEquals(stored.category, after.category)
            assertEquals(stored.status, after.status)
            assertEquals(stored.name, after.name)
        }

    @Test
    fun `the version rises so the correction reaches the other devices`() =
        runTest {
            val gear = FakeGearRepository(listOf(body()))
            val before =
                gear
                    .observeGear()
                    .first()
                    .single()
                    .audit.version
            val viewModel = viewModel(gear)

            viewModel.saveGearItem(form(serialNumber = "04127634"), existingId = ID)

            assertEquals(
                before + 1,
                gear
                    .observeGear()
                    .first()
                    .single()
                    .audit.version,
                "a corrected row whose version did not move is a correction that stays on " +
                    "the device it was made on",
            )
        }

    @Test
    fun `saving without an identifier adds a new item`() =
        runTest {
            val gear = FakeGearRepository(listOf(body()))
            val viewModel = viewModel(gear)

            viewModel.saveGearItem(form(name = "Canon R6 body"))

            assertEquals(
                2,
                gear.observeGear().first().size,
                "the same path still adds, because adding and correcting differ only in " +
                    "whether there is already a row",
            )
        }

    @Test
    fun `an item with no name is not saved`() =
        runTest {
            val gear = FakeGearRepository(listOf(body()))
            val viewModel = viewModel(gear)

            viewModel.saveGearItem(form(name = "   "), existingId = ID)

            assertEquals(
                "Canon R5 body",
                gear
                    .observeGear()
                    .first()
                    .single()
                    .name,
                "a blank name would leave a row on the register nobody could identify",
            )
        }

    // -- Fixtures ----------------------------------------------------------------------------

    private suspend fun StudioViewModel.gearItem() =
        uiState
            .first { it.content is UiState.Success }
            .content
            .let { (it as UiState.Success).data }
            .inventory
            .groups
            .flatMap { group -> group.items }
            .single()

    private fun viewModel(gear: FakeGearRepository) =
        StudioViewModel(
            gearRepository = gear,
            recipeRepository = FakeLightingRecipeRepository(),
            volumeRepository = FakeStorageVolumeRepository(),
            studioProfileRepository = FakeStudioProfileRepository(),
            studioContext = LocalStudioContext(),
            clock = TestAppClock(),
            timeZone = TimeZone.UTC,
        )

    private fun form(
        name: String = "Canon R5 body",
        serialNumber: String? = null,
    ) = NewGearItem(
        name = name,
        category = GearCategory.Camera,
        status = GearStatus.InService,
        serialNumber = serialNumber,
        purchasePrice = "3899.00",
        purchasedOn = "2024-03-03",
        lastServicedOn = null,
        notes = null,
    )

    private fun body() =
        GearItem(
            id = ID,
            studioId = STUDIO,
            name = "Canon R5 body",
            category = GearCategory.Camera,
            status = GearStatus.InService,
            // The studio's own currency. A price in another one is summed into the
            // insured value and throws, which the state flow catches into an error.
            purchasePrice = Money(minorUnits = 389_900, currency = CurrencyCode.USD),
            purchasedOn = LocalDate.parse("2024-03-03"),
            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
        )

    private companion object {
        val STUDIO = StudioId("studio-1")
        val ID = GearItemId("gear-1")
    }
}
