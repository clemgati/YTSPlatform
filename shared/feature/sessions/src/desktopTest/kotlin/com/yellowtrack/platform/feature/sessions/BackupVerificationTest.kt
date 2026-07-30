package com.yellowtrack.platform.feature.sessions

import com.yellowtrack.platform.core.common.storage.VolumeContents
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeCrewRepository
import com.yellowtrack.platform.core.testing.FakeGearRepository
import com.yellowtrack.platform.core.testing.FakeMediaCopyRepository
import com.yellowtrack.platform.core.testing.FakePackingRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.FakeShotRepository
import com.yellowtrack.platform.core.testing.FakeStorageVolumeRepository
import com.yellowtrack.platform.core.testing.FakeTalentReleaseRepository
import com.yellowtrack.platform.core.testing.FakeVolumeInspector
import com.yellowtrack.platform.core.testing.RecordingDocumentSink
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsUiState
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checking a backup by reading it, rather than by being told.
 *
 * "Verified" meant a studio had pressed a button. A drive can fail silently and a folder
 * can be moved, so a tick recorded without reading anything is a backup nobody has checked
 * wearing the label of one that has been.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupVerificationTest {
    private val zone = TimeZone.of("Europe/London")
    private val sessionId = SessionId.new()
    private val projectId = ProjectId.new()
    private val studioId = LocalStudioContext.LOCAL_STUDIO_ID
    private val path = "/Volumes/Red T7/2026/Johnson Wedding"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun session() =
        Session(
            id = sessionId,
            studioId = studioId,
            projectId = projectId,
            title = "Wedding day",
            kind = SessionKind.Shoot,
            status = SessionStatus.Completed,
            startsAt = LocalDateTime.parse("2026-08-15T14:00").toInstant(zone),
            endsAt = LocalDateTime.parse("2026-08-15T23:00").toInstant(zone),
            timeZoneId = zone.id,
            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
        )

    private fun copy(
        onPath: String? = path,
        verified: Boolean = false,
    ) = MediaCopy(
        id = MediaCopyId.new(),
        studioId = studioId,
        sessionId = sessionId,
        volumeName = "Red Samsung T7",
        kind = StorageKind.ExternalDrive,
        path = onPath,
        copiedAt = TestAppClock.DEFAULT_NOW,
        verifiedAt = TestAppClock.DEFAULT_NOW.takeIf { verified },
        audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
    )

    private class Harness(
        val viewModel: SessionDetailsViewModel,
        val copies: FakeMediaCopyRepository,
        val inspector: FakeVolumeInspector,
    )

    private fun harness(
        copies: List<MediaCopy>,
        inspector: FakeVolumeInspector = FakeVolumeInspector(),
    ): Harness {
        val copyRepository = FakeMediaCopyRepository(copies)

        return Harness(
            viewModel =
                SessionDetailsViewModel(
                    sessionId = sessionId,
                    sessionRepository = FakeSessionRepository(listOf(session())),
                    shotRepository = FakeShotRepository(),
                    crewRepository = FakeCrewRepository(),
                    releaseRepository = FakeTalentReleaseRepository(),
                    mediaCopyRepository = copyRepository,
                    packingRepository = FakePackingRepository(),
                    gearRepository = FakeGearRepository(),
                    volumeRepository = FakeStorageVolumeRepository(copies = copyRepository),
                    volumeInspector = inspector,
                    projectRepository = FakeProjectRepository(),
                    clientRepository = FakeClientRepository(),
                    documentSink = RecordingDocumentSink(),
                    studioContext = LocalStudioContext(),
                    clock = TestAppClock(),
                    deviceZone = zone,
                ),
            copies = copyRepository,
            inspector = inspector,
        )
    }

    private suspend fun SessionDetailsViewModel.state(): SessionDetailsUiState =
        uiState.first { it.session is UiState.Success }

    // --- Reading the drive -----------------------------------------------------------------

    @Test
    fun `a copy that reads is recorded with what was found`() =
        runTest {
            val existing = copy()
            val harness = harness(listOf(existing), FakeVolumeInspector.holding(path, files = 2_481))

            harness.viewModel.checkMediaCopy(existing.id)

            val stored = assertNotNull(harness.copies.getCopy(existing.id))
            assertEquals(2_481, stored.verifiedFileCount)
            assertNotNull(stored.verifiedAt)
            assertTrue(stored.wasReadByTheApplication)
        }

    @Test
    fun `a drive nobody plugged in does not count as a verification`() =
        runTest {
            val existing = copy()
            val harness = harness(listOf(existing))

            harness.viewModel.checkMediaCopy(existing.id)

            val stored = assertNotNull(harness.copies.getCopy(existing.id))
            assertNull(
                stored.verifiedAt,
                "checked today and found nothing is not a verification and must not read as one",
            )
            assertTrue(assertNotNull(harness.viewModel.state().checkResult).contains("plugged in"))
        }

    @Test
    fun `an empty folder is reported rather than counted as a backup`() =
        runTest {
            val existing = copy()
            val harness =
                harness(
                    listOf(existing),
                    FakeVolumeInspector(mapOf(path to VolumeContents(exists = true, fileCount = 0, totalBytes = 0))),
                )

            harness.viewModel.checkMediaCopy(existing.id)

            assertNull(assertNotNull(harness.copies.getCopy(existing.id)).verifiedAt)
            assertTrue(assertNotNull(harness.viewModel.state().checkResult).contains("empty"))
        }

    @Test
    fun `a failed read leaves an earlier good result standing`() =
        runTest {
            val existing = copy(verified = true)
            val harness = harness(listOf(existing))

            harness.viewModel.checkMediaCopy(existing.id)

            assertNotNull(
                assertNotNull(harness.copies.getCopy(existing.id)).verifiedAt,
                "today's failure does not erase the fact that it read fine last month",
            )
        }

    @Test
    fun `a copy with no path is never read`() =
        runTest {
            val existing = copy(onPath = null)
            val harness = harness(listOf(existing))

            harness.viewModel.checkMediaCopy(existing.id)

            assertTrue(harness.inspector.pathsRead.isEmpty())
            assertNull(assertNotNull(harness.copies.getCopy(existing.id)).verifiedAt)
        }

    // --- Ticked by hand ----------------------------------------------------------------------

    @Test
    fun `a tick by hand carries a date and no count`() =
        runTest {
            val existing = copy(onPath = null)
            val harness = harness(listOf(existing))

            harness.viewModel.markMediaCopyCheckedByHand(existing.id)

            val stored = assertNotNull(harness.copies.getCopy(existing.id))
            assertNotNull(stored.verifiedAt)
            assertNull(stored.verifiedFileCount)
            assertFalse(
                stored.wasReadByTheApplication,
                "a tick must not borrow the authority of a count the application took",
            )
        }

    // --- Platforms that cannot read ------------------------------------------------------------

    @Test
    fun `a device with no filesystem says so rather than reporting everything missing`() =
        runTest {
            val harness = harness(listOf(copy()), FakeVolumeInspector(isSupported = false))

            assertFalse(
                harness.viewModel.state().canReadDrives,
                "telling a studio its backups vanished because the browser cannot see drives " +
                    "would be the worst possible answer",
            )
        }
}
