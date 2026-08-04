package com.yellowtrack.platform.feature.dashboard

import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.sync.SyncConflict
import com.yellowtrack.platform.core.model.sync.SyncConflictId
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeGearRepository
import com.yellowtrack.platform.core.testing.FakeLeadRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.FakeStorageVolumeRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.FakeSyncConflictRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.dashboard.presentation.DashboardViewModel
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Dashboard's half of ADR 0008 decision 3.
 *
 * Settings holds the recovery; this holds the telling. A conflict visible only to a
 * photographer who happens to open Settings is not one the studio has been shown, and the
 * ADR made being shown the condition on last-write-wins being acceptable at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConflictBannerTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `discarded work reaches the dashboard without anyone going looking`() =
        runTest {
            val summary = summaryWith(conflicts = listOf(conflict("one"), conflict("two")))

            assertEquals(2, summary.unresolvedConflicts)
        }

    @Test
    fun `a studio with nothing discarded is told nothing`() =
        runTest {
            val summary = summaryWith(conflicts = emptyList())

            assertEquals(
                0,
                summary.unresolvedConflicts,
                "a banner that shows when there is nothing wrong teaches the studio to ignore it",
            )
        }

    @Test
    fun `dealing with one takes it off the count`() =
        runTest {
            val conflicts = FakeSyncConflictRepository(listOf(conflict("one"), conflict("two")))
            val viewModel = viewModel(conflicts)

            assertEquals(2, viewModel.summary().unresolvedConflicts)

            conflicts.resolve(SyncConflictId("one"))

            assertEquals(
                1,
                viewModel.summary().unresolvedConflicts,
                "the count has to fall as they are dealt with, or it stops meaning anything",
            )
        }

    private suspend fun summaryWith(conflicts: List<SyncConflict>): DashboardSummary =
        viewModel(FakeSyncConflictRepository(conflicts)).summary()

    private suspend fun DashboardViewModel.summary(): DashboardSummary {
        val state = uiState.first { it.summary is UiState.Success }
        return (state.summary as UiState.Success<DashboardSummary>).data
    }

    private fun viewModel(conflicts: FakeSyncConflictRepository) =
        DashboardViewModel(
            clientRepository = FakeClientRepository(),
            projectRepository = FakeProjectRepository(),
            sessionRepository = FakeSessionRepository(),
            leadRepository = FakeLeadRepository(),
            studioProfileRepository = FakeStudioProfileRepository(),
            conflictRepository = conflicts,
            studioContext = LocalStudioContext(),
            gearRepository = FakeGearRepository(),
            volumeRepository = FakeStorageVolumeRepository(),
            clock = TestAppClock(),
        )

    private fun conflict(id: String) =
        SyncConflict(
            id = SyncConflictId(id),
            studioId = LocalStudioContext().studioId,
            entityTable = "session",
            entityId = "session-1",
            losingPayload = """{"title":"Ceremony — 2pm"}""",
            winningPayload = """{"title":"Ceremony — 3pm"}""",
            detectedAt = TestAppClock.DEFAULT_NOW,
            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
        )
}
