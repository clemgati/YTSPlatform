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
import com.yellowtrack.platform.core.model.gear.LightRole
import com.yellowtrack.platform.core.model.gear.LightSetup
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.model.media.VolumeStatus
import com.yellowtrack.platform.core.testing.FakeGearRepository
import com.yellowtrack.platform.core.testing.FakeLightingRecipeRepository
import com.yellowtrack.platform.core.testing.FakeStorageVolumeRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.studio.presentation.StudioViewModel
import com.yellowtrack.platform.feature.studio.presentation.model.NewGearItem
import com.yellowtrack.platform.feature.studio.presentation.model.NewLightSetup
import com.yellowtrack.platform.feature.studio.presentation.model.NewLightingRecipe
import com.yellowtrack.platform.feature.studio.presentation.model.NewVolume
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

    // -- Drives and lighting set-ups ---------------------------------------------------------

    /**
     * Relabelling a drive must not say nobody has ever read it.
     *
     * `lastCheckedAt` is set by a different button entirely and is the only thing separating
     * a backup a studio has from one it believes it has.
     */
    @Test
    fun `correcting a drive keeps when it was last read`() =
        runTest {
            val checked = TestAppClock.DEFAULT_NOW
            val volumes =
                FakeStorageVolumeRepository(
                    listOf(
                        StorageVolume(
                            id = VOLUME,
                            studioId = STUDIO,
                            label = "Shoot SSD 1",
                            kind = StorageKind.ExternalDrive,
                            status = VolumeStatus.InUse,
                            lastCheckedAt = checked,
                            audit = AuditMetadata.createdAt(checked),
                        ),
                    ),
                )
            val viewModel = viewModel(FakeGearRepository(), volumes)

            viewModel.saveVolume(volumeForm(label = "Shoot SSD 1 (blue)"), existingId = VOLUME)

            val stored = volumes.observeVolumes().first().single()
            assertEquals("Shoot SSD 1 (blue)", stored.label)
            assertEquals(checked, stored.lastCheckedAt, "a rename is not a claim that it was never read")
        }

    /**
     * And must not revive it either.
     *
     * The form has no status field — failing a drive is a separate action on the register —
     * so a status built from the form would report a dead drive as in use, and every shoot
     * with a copy on it would go back to counting it.
     */
    @Test
    fun `correcting a drive does not revive a failed one`() =
        runTest {
            val volumes =
                FakeStorageVolumeRepository(
                    listOf(
                        StorageVolume(
                            id = VOLUME,
                            studioId = STUDIO,
                            label = "Shoot SSD 1",
                            kind = StorageKind.ExternalDrive,
                            status = VolumeStatus.Failed,
                            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
                        ),
                    ),
                )
            val viewModel = viewModel(FakeGearRepository(), volumes)

            // The form has no status field, so what it carries is whatever the dialog
            // defaulted to. That is exactly the case: the stored status must win.
            viewModel.saveVolume(
                volumeForm(label = "Shoot SSD 1", status = VolumeStatus.InUse),
                existingId = VOLUME,
            )

            assertEquals(
                VolumeStatus.Failed,
                volumes
                    .observeVolumes()
                    .first()
                    .single()
                    .status,
            )
        }

    @Test
    fun `a lighting set-up can be corrected`() =
        runTest {
            val recipes =
                FakeLightingRecipeRepository(
                    listOf(
                        LightingRecipe(
                            id = RECIPE,
                            studioId = STUDIO,
                            name = "Clamshell",
                            lights = listOf(LightSetup(role = LightRole.Key, instrument = "Profoto B10")),
                            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
                        ),
                    ),
                )
            val viewModel = viewModel(FakeGearRepository(), recipes = recipes)

            viewModel.saveRecipe(
                NewLightingRecipe(
                    name = "Clamshell, tightened",
                    lights =
                        listOf(
                            NewLightSetup(
                                role = LightRole.Key,
                                instrument = "Profoto B10",
                                modifier = "3ft octabox",
                                power = null,
                                position = null,
                                distance = null,
                            ),
                        ),
                    notes = null,
                ),
                existingId = RECIPE,
            )

            val stored = recipes.observeRecipes().first().single()
            assertEquals(RECIPE, stored.id, "a set-up worked out once is corrected, not duplicated")
            assertEquals("Clamshell, tightened", stored.name)
            assertEquals("3ft octabox", stored.lights.single().modifier)
        }

    private fun volumeForm(
        label: String,
        status: VolumeStatus = VolumeStatus.InUse,
    ) = NewVolume(
        label = label,
        kind = StorageKind.ExternalDrive,
        status = status,
        isOffsite = false,
        notes = null,
    )

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

    private fun viewModel(
        gear: FakeGearRepository,
        volumes: FakeStorageVolumeRepository = FakeStorageVolumeRepository(),
        recipes: FakeLightingRecipeRepository = FakeLightingRecipeRepository(),
    ) = StudioViewModel(
        gearRepository = gear,
        recipeRepository = recipes,
        volumeRepository = volumes,
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
        val VOLUME = StorageVolumeId("volume-1")
        val RECIPE = LightingRecipeId("recipe-1")
        val ID = GearItemId("gear-1")
    }
}
