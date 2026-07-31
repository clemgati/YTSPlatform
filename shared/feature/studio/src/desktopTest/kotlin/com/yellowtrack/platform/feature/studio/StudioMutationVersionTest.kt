package com.yellowtrack.platform.feature.studio

import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.gear.GearCategory
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.testing.FakeGearRepository
import com.yellowtrack.platform.core.testing.FakeLightingRecipeRepository
import com.yellowtrack.platform.core.testing.FakeStorageVolumeRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.feature.studio.presentation.StudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every mutation has to move the row's version.
 *
 * `version` is optimistic-concurrency state, not decoration: an edit that leaves it
 * unchanged is indistinguishable from no edit at all, so reconciliation would discard it
 * without anyone noticing — on a device nobody is currently looking at. See
 * `docs/adr/0008-synchronisation-semantics.md`.
 *
 * These two were written without it. The general guard — moving the increment into SQL so
 * no caller can forget — belongs with the synchronisation work, where the invariant can be
 * designed once rather than remembered twenty-three times.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudioMutationVersionTest {
    private val studioId = LocalStudioContext.LOCAL_STUDIO_ID
    private val now = TestAppClock.DEFAULT_NOW

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        gear: FakeGearRepository,
        volumes: FakeStorageVolumeRepository,
    ) = StudioViewModel(
        gearRepository = gear,
        recipeRepository = FakeLightingRecipeRepository(),
        volumeRepository = volumes,
        studioContext = LocalStudioContext(),
        clock = TestAppClock(),
        studioProfileRepository = FakeStudioProfileRepository(),
        timeZone = TimeZone.UTC,
    )

    @Test
    fun `marking gear serviced moves its version`() =
        runTest {
            val item =
                GearItem(
                    id = GearItemId.new(),
                    studioId = studioId,
                    name = "Canon R5 body",
                    category = GearCategory.Camera,
                    audit = AuditMetadata.createdAt(now),
                )
            val gear = FakeGearRepository(listOf(item))

            viewModel(gear, FakeStorageVolumeRepository()).markServiced(item.id)

            val stored = assertNotNull(gear.getGearItem(item.id))
            assertNotNull(stored.lastServicedAt, "the service date is the point of the action")
            assertTrue(
                stored.audit.version > item.audit.version,
                "an edit that leaves the version alone is indistinguishable from no edit",
            )
            assertEquals(stored.audit.updatedAt, stored.lastServicedAt)
        }

    @Test
    fun `marking a drive checked moves its version`() =
        runTest {
            val volume =
                StorageVolume(
                    id = StorageVolumeId.new(),
                    studioId = studioId,
                    label = "Red Samsung T7",
                    kind = StorageKind.ExternalDrive,
                    audit = AuditMetadata.createdAt(now),
                )
            val volumes = FakeStorageVolumeRepository(listOf(volume))

            viewModel(FakeGearRepository(), volumes).markVolumeChecked(volume.id)

            val stored = assertNotNull(volumes.getVolume(volume.id))
            assertNotNull(stored.lastCheckedAt)
            assertTrue(
                stored.audit.version > volume.audit.version,
                "a check nobody can tell happened is a check that will be reconciled away",
            )
        }
}
