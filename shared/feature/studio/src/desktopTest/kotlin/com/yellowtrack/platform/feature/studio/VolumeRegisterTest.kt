package com.yellowtrack.platform.feature.studio

import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.model.media.VolumeStatus
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.testing.FakeGearRepository
import com.yellowtrack.platform.core.testing.FakeLightingRecipeRepository
import com.yellowtrack.platform.core.testing.FakeMediaCopyRepository
import com.yellowtrack.platform.core.testing.FakeStorageVolumeRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.studio.presentation.StudioViewModel
import com.yellowtrack.platform.feature.studio.presentation.model.NewVolume
import com.yellowtrack.platform.feature.studio.presentation.model.VolumeRegister
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The register, and the question it exists to answer.
 *
 * A studio could always ask whether one wedding was safe. What it could not ask, at the
 * moment it mattered, was: this drive has died — what was on it?
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VolumeRegisterTest {
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

    private fun volume(
        label: String,
        kind: StorageKind = StorageKind.ExternalDrive,
        status: VolumeStatus = VolumeStatus.InUse,
        checked: Boolean = false,
    ) = StorageVolume(
        id = StorageVolumeId.new(),
        studioId = studioId,
        label = label,
        kind = kind,
        status = status,
        lastCheckedAt = now.takeIf { checked },
        audit = AuditMetadata.createdAt(now),
    )

    private fun copyOn(
        volume: StorageVolume,
        session: SessionId = SessionId.new(),
    ) = MediaCopy(
        id = MediaCopyId.new(),
        studioId = studioId,
        sessionId = session,
        volumeId = volume.id,
        volumeName = volume.label,
        kind = volume.kind,
        copiedAt = now,
        audit = AuditMetadata.createdAt(now),
    )

    private class Harness(
        val viewModel: StudioViewModel,
        val volumes: FakeStorageVolumeRepository,
    )

    private fun harness(
        volumes: List<StorageVolume> = emptyList(),
        copies: List<MediaCopy> = emptyList(),
    ): Harness {
        val copyRepository = FakeMediaCopyRepository(copies)
        val volumeRepository = FakeStorageVolumeRepository(volumes, copyRepository)

        return Harness(
            viewModel =
                StudioViewModel(
                    gearRepository = FakeGearRepository(),
                    recipeRepository = FakeLightingRecipeRepository(),
                    volumeRepository = volumeRepository,
                    studioContext = LocalStudioContext(),
                    clock = TestAppClock(),
                    studioProfileRepository = FakeStudioProfileRepository(),
                    timeZone = TimeZone.UTC,
                ),
            volumes = volumeRepository,
        )
    }

    private suspend fun StudioViewModel.register(): VolumeRegister {
        val state = uiState.first { it.content is UiState.Success }

        return (state.content as UiState.Success).data.register
    }

    // --- What is on a drive -----------------------------------------------------------------

    @Test
    fun `a drive says how many shoots are on it`() =
        runTest {
            val drive = volume("Red Samsung T7")
            val harness = harness(listOf(drive), listOf(copyOn(drive), copyOn(drive)))

            assertEquals(
                2,
                harness.viewModel
                    .register()
                    .volumes
                    .single()
                    .copyCount,
            )
        }

    @Test
    fun `marking a drive failed says how much is on it`() =
        runTest {
            val drive = volume("Red Samsung T7")
            val harness = harness(listOf(drive), listOf(copyOn(drive), copyOn(drive), copyOn(drive)))

            harness.viewModel.setVolumeStatus(drive.id, VolumeStatus.Failed)

            val register = harness.viewModel.register()
            assertEquals(1, register.failedCount)
            assertEquals(
                3,
                register.copiesAtRisk,
                "the number of shoots at stake is the reason to act, not a statistic",
            )
        }

    @Test
    fun `a failed drive sorts to the top, because it is what needs doing`() =
        runTest {
            val working = volume("Studio NAS", kind = StorageKind.Nas)
            val dead = volume("Red Samsung T7", status = VolumeStatus.Failed)
            val harness = harness(listOf(working, dead))

            assertEquals(
                listOf("Red Samsung T7", "Studio NAS"),
                harness.viewModel
                    .register()
                    .volumes
                    .map { it.label },
            )
        }

    @Test
    fun `a drive put back in use stops counting against the studio`() =
        runTest {
            val drive = volume("Red Samsung T7", status = VolumeStatus.Failed)
            val harness = harness(listOf(drive), listOf(copyOn(drive)))

            harness.viewModel.setVolumeStatus(drive.id, VolumeStatus.InUse)

            val register = harness.viewModel.register()
            assertEquals(0, register.failedCount)
            assertEquals(0, register.copiesAtRisk)
        }

    // --- Drives nobody has opened -------------------------------------------------------------

    @Test
    fun `a drive nobody has ever checked is counted`() =
        runTest {
            val harness = harness(listOf(volume("Red Samsung T7"), volume("Studio NAS", checked = true)))

            assertEquals(
                1,
                harness.viewModel.register().neverCheckedCount,
                "a drive nobody has opened is the one that fails silently",
            )
        }

    @Test
    fun `checking a drive records that someone opened it`() =
        runTest {
            val drive = volume("Red Samsung T7")
            val harness = harness(listOf(drive))

            harness.viewModel.markVolumeChecked(drive.id)

            assertEquals(0, harness.viewModel.register().neverCheckedCount)
        }

    @Test
    fun `a failed drive is not nagged about being unchecked`() =
        runTest {
            val harness = harness(listOf(volume("Red Samsung T7", status = VolumeStatus.Failed)))

            assertEquals(
                0,
                harness.viewModel.register().neverCheckedCount,
                "the drive is dead; asking whether anyone opened it lately is not the advice",
            )
        }

    // --- Adding one -----------------------------------------------------------------------------

    @Test
    fun `a drive added is in use and dependable`() =
        runTest {
            val harness = harness()

            harness.viewModel.saveVolume(
                NewVolume(
                    label = "Red Samsung T7",
                    kind = StorageKind.ExternalDrive,
                    status = VolumeStatus.InUse,
                    isOffsite = false,
                    notes = null,
                ),
            )

            val added =
                harness.viewModel
                    .register()
                    .volumes
                    .single()
            assertEquals("Red Samsung T7", added.label)
            assertTrue(added.isDependable)
            assertEquals("In the studio", added.whereLabel)
        }

    @Test
    fun `a cloud drive is away from the studio without being marked so`() =
        runTest {
            val harness = harness(listOf(volume("Backblaze", kind = StorageKind.Cloud)))

            assertEquals(
                "Away from the studio",
                harness.viewModel
                    .register()
                    .volumes
                    .single()
                    .whereLabel,
            )
        }

    @Test
    fun `an empty register says so rather than reporting nothing wrong`() =
        runTest {
            val register = harness().viewModel.register()

            assertTrue(register.volumes.isEmpty())
            assertEquals(0, register.failedCount)
            assertFalse(register.copiesAtRisk > 0)
        }
}
