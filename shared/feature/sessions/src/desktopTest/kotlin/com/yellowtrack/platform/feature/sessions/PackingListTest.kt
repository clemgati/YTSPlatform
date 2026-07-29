package com.yellowtrack.platform.feature.sessions

import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.gear.GearCategory
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.GearStatus
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
import com.yellowtrack.platform.core.testing.FakeTalentReleaseRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsViewModel
import com.yellowtrack.platform.feature.sessions.presentation.details.model.PackingSummary
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
import kotlin.test.assertTrue

/**
 * The kit list, checked at both ends of the day.
 *
 * Packing is ticked in a calm studio in the morning. Returning is ticked in the dark at the
 * end of a fourteen-hour wedding, which is exactly when a light stand gets left behind a
 * curtain — so the two ticks are separate, and the second one is what the list is for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PackingListTest {
    private val zone = TimeZone.of("Europe/London")
    private val sessionId = SessionId.new()
    private val projectId = ProjectId.new()
    private val studioId = LocalStudioContext.LOCAL_STUDIO_ID

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
            status = SessionStatus.Confirmed,
            startsAt = LocalDateTime.parse("2026-08-15T14:00").toInstant(zone),
            endsAt = LocalDateTime.parse("2026-08-15T23:00").toInstant(zone),
            timeZoneId = zone.id,
            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
        )

    private fun gearItem(
        name: String,
        status: GearStatus = GearStatus.InService,
    ) = GearItem(
        id = GearItemId.new(),
        studioId = studioId,
        name = name,
        category = GearCategory.Camera,
        status = status,
        audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
    )

    private class Harness(
        val viewModel: SessionDetailsViewModel,
        val gear: FakeGearRepository,
        val packing: FakePackingRepository,
    )

    private fun harness(owned: List<GearItem>): Harness {
        val gear = FakeGearRepository(owned)
        val packing = FakePackingRepository()

        return Harness(
            viewModel =
                SessionDetailsViewModel(
                    sessionId = sessionId,
                    sessionRepository = FakeSessionRepository(listOf(session())),
                    shotRepository = FakeShotRepository(),
                    crewRepository = FakeCrewRepository(),
                    releaseRepository = FakeTalentReleaseRepository(),
                    mediaCopyRepository = FakeMediaCopyRepository(),
                    packingRepository = packing,
                    gearRepository = gear,
                    projectRepository = FakeProjectRepository(),
                    clientRepository = FakeClientRepository(),
                    studioContext = LocalStudioContext(),
                    clock = TestAppClock(),
                    deviceZone = zone,
                ),
            gear = gear,
            packing = packing,
        )
    }

    private suspend fun SessionDetailsViewModel.packing(): PackingSummary {
        val state = uiState.first { it.session is UiState.Success }

        return (state.session as UiState.Success).data.packing
    }

    // --- Building the list ----------------------------------------------------------------

    @Test
    fun `gear the studio owns is offered until it is on the list`() =
        runTest {
            val body = gearItem("Canon R5 body")
            val harness = harness(listOf(body))

            assertEquals(
                listOf("Canon R5 body"),
                harness.viewModel
                    .packing()
                    .available
                    .map { it.label },
            )

            harness.viewModel.addToPackingList(body.id)

            val summary = harness.viewModel.packing()
            assertEquals(listOf("Canon R5 body"), summary.items.map { it.name })
            assertTrue(summary.available.isEmpty(), "offering it twice would let it be listed twice")
        }

    @Test
    fun `gear at the repair shop is not offered`() =
        runTest {
            val harness =
                harness(
                    listOf(
                        gearItem("Canon R5 body"),
                        gearItem("Second body", status = GearStatus.InRepair),
                    ),
                )

            assertEquals(
                listOf("Canon R5 body"),
                harness.viewModel
                    .packing()
                    .available
                    .map { it.label },
                "a body at the shop cannot be packed, and offering it puts a line on the list " +
                    "that can never be ticked",
            )
        }

    @Test
    fun `adding something to the list is not the same as putting it in the van`() =
        runTest {
            val body = gearItem("Canon R5 body")
            val harness = harness(listOf(body))

            harness.viewModel.addToPackingList(body.id)

            val item =
                harness.viewModel
                    .packing()
                    .items
                    .single()
            assertFalse(item.isPacked)
            assertFalse(item.isReturned)
        }

    // --- The two ticks ---------------------------------------------------------------------

    @Test
    fun `something packed and not yet back is what the list is chasing`() =
        runTest {
            val body = gearItem("Canon R5 body")
            val harness = harness(listOf(body))

            harness.viewModel.addToPackingList(body.id)
            val entryId =
                harness.viewModel
                    .packing()
                    .items
                    .single()
                    .id
            harness.viewModel.setPacked(entryId, true)

            val summary = harness.viewModel.packing()
            assertEquals(1, summary.packed)
            assertEquals(1, summary.missing)
        }

    @Test
    fun `ticking something back in clears it from what is still out`() =
        runTest {
            val body = gearItem("Canon R5 body")
            val harness = harness(listOf(body))

            harness.viewModel.addToPackingList(body.id)
            val entryId =
                harness.viewModel
                    .packing()
                    .items
                    .single()
                    .id
            harness.viewModel.setPacked(entryId, true)
            harness.viewModel.setReturned(entryId, true)

            assertEquals(0, harness.viewModel.packing().missing)
        }

    @Test
    fun `ticking something back in at midnight also marks it packed`() =
        runTest {
            val body = gearItem("Canon R5 body")
            val harness = harness(listOf(body))

            harness.viewModel.addToPackingList(body.id)
            val entryId =
                harness.viewModel
                    .packing()
                    .items
                    .single()
                    .id

            // Nobody ticked the morning box. At the end of the night the only thing being
            // checked is what came off the van.
            harness.viewModel.setReturned(entryId, true)

            val item =
                harness.viewModel
                    .packing()
                    .items
                    .single()
            assertTrue(item.isPacked, "refusing the tick would teach a studio to stop using the list")
            assertTrue(item.isReturned)
            assertEquals(0, harness.viewModel.packing().missing)
        }

    @Test
    fun `unpacking something also unreturns it`() =
        runTest {
            val body = gearItem("Canon R5 body")
            val harness = harness(listOf(body))

            harness.viewModel.addToPackingList(body.id)
            val entryId =
                harness.viewModel
                    .packing()
                    .items
                    .single()
                    .id
            harness.viewModel.setPacked(entryId, true)
            harness.viewModel.setReturned(entryId, true)

            harness.viewModel.setPacked(entryId, false)

            val item =
                harness.viewModel
                    .packing()
                    .items
                    .single()
            assertFalse(item.isReturned, "gear that was never taken cannot have come back")
            assertEquals(0, harness.viewModel.packing().missing)
        }

    @Test
    fun `only what went out counts as missing`() =
        runTest {
            val packed = gearItem("Canon R5 body")
            val leftBehind = gearItem("Backup body")
            val harness = harness(listOf(packed, leftBehind))

            harness.viewModel.addToPackingList(packed.id)
            harness.viewModel.addToPackingList(leftBehind.id)

            val entryId =
                harness.viewModel
                    .packing()
                    .items
                    .first { it.name == "Canon R5 body" }
                    .id
            harness.viewModel.setPacked(entryId, true)

            val summary = harness.viewModel.packing()
            assertEquals(2, summary.items.size)
            assertEquals(
                1,
                summary.missing,
                "gear that never left the studio is not something to go back to the venue for",
            )
        }

    // --- Gear that has since gone -----------------------------------------------------------

    @Test
    fun `an entry whose gear was deleted is dropped rather than shown blank`() =
        runTest {
            val body = gearItem("Canon R5 body")
            val harness = harness(listOf(body))

            harness.viewModel.addToPackingList(body.id)
            harness.gear.deleteGearItem(body.id)

            assertTrue(
                harness.viewModel
                    .packing()
                    .items
                    .isEmpty(),
                "a line naming nothing cannot be looked for",
            )
        }

    @Test
    fun `taking something off the list puts it back on offer`() =
        runTest {
            val body = gearItem("Canon R5 body")
            val harness = harness(listOf(body))

            harness.viewModel.addToPackingList(body.id)
            val entryId =
                harness.viewModel
                    .packing()
                    .items
                    .single()
                    .id
            harness.viewModel.removeFromPackingList(entryId)

            val summary = harness.viewModel.packing()
            assertTrue(summary.items.isEmpty())
            assertEquals(listOf("Canon R5 body"), summary.available.map { it.label })
        }
}
