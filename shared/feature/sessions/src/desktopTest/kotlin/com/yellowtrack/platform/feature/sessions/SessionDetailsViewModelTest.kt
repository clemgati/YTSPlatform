package com.yellowtrack.platform.feature.sessions

import com.yellowtrack.platform.core.common.solar.GeoCoordinates
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.crew.CrewRole
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeCrewRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.FakeShotRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsViewModel
import com.yellowtrack.platform.feature.sessions.presentation.details.model.SessionDetailsModel
import com.yellowtrack.platform.feature.sessions.presentation.model.NewCrewMember
import com.yellowtrack.platform.feature.sessions.presentation.model.NewSession
import com.yellowtrack.platform.feature.sessions.presentation.model.NewShot
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
import kotlin.time.Instant

/**
 * One shoot day: correcting it, moving it, and the light worked out for where it is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailsViewModelTest {
    private val zone = TimeZone.of("Europe/London")
    private val projectId = ProjectId.new()
    private val sessionId = SessionId.new()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun instant(
        date: String,
        time: String,
    ): Instant = LocalDateTime.parse("${date}T$time").toInstant(zone)

    private fun session(
        coordinates: GeoCoordinates? = null,
        date: String = "2026-08-15",
    ) = Session(
        id = sessionId,
        studioId = LocalStudioContext.LOCAL_STUDIO_ID,
        projectId = projectId,
        title = "Wedding day",
        kind = SessionKind.Shoot,
        status = SessionStatus.Confirmed,
        startsAt = instant(date, "14:00"),
        endsAt = instant(date, "23:00"),
        timeZoneId = zone.id,
        locationName = "Thornbury Manor",
        coordinates = coordinates,
        audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
    )

    private lateinit var shots: FakeShotRepository
    private lateinit var crew: FakeCrewRepository

    private fun harness(existing: Session = session()): Pair<FakeSessionRepository, SessionDetailsViewModel> {
        val sessions = FakeSessionRepository(listOf(existing))
        shots = FakeShotRepository()
        crew = FakeCrewRepository()

        return sessions to
            SessionDetailsViewModel(
                sessionId = sessionId,
                sessionRepository = sessions,
                shotRepository = shots,
                crewRepository = crew,
                projectRepository = FakeProjectRepository(),
                clientRepository = FakeClientRepository(),
                studioContext = LocalStudioContext(),
                clock = TestAppClock(),
                deviceZone = zone,
            )
    }

    private fun newSession(
        title: String = "Wedding day",
        date: String = "2026-08-15",
        startTime: String = "14:00",
        endTime: String = "23:00",
        status: SessionStatus = SessionStatus.Confirmed,
        latitude: String = "",
        longitude: String = "",
    ) = NewSession(
        projectId = projectId,
        title = title,
        kind = SessionKind.Shoot,
        status = status,
        date = date,
        startTime = startTime,
        endTime = endTime,
        callTime = "",
        locationName = "Thornbury Manor",
        locationAddress = "",
        latitude = latitude,
        longitude = longitude,
        notes = "",
    )

    private suspend fun SessionDetailsViewModel.model(): SessionDetailsModel {
        val state = uiState.first { it.session is UiState.Success }
        return (state.session as UiState.Success).data
    }

    // --- Correcting and moving ---------------------------------------------------------

    @Test
    fun `editing corrects the day in place`() =
        runTest {
            val (sessions, viewModel) = harness()

            viewModel.updateSession(newSession(title = "Wedding day — revised", startTime = "15:00"))

            val stored =
                sessions
                    .observeSessions()
                    .first()
                    .single()
            assertEquals(sessionId, stored.id, "correcting a day must not mint a second one")
            assertEquals("Wedding day — revised", stored.title)
            assertEquals(instant("2026-08-15", "15:00"), stored.startsAt)
        }

    @Test
    fun `a moved date keeps the original day on the calendar as postponed`() =
        runTest {
            val (sessions, viewModel) = harness()

            viewModel.moveSession(newSession(date = "2026-09-19"))

            val all = sessions.observeSessions().first()
            assertEquals(2, all.size, "the day that was held is a fact about the booking")

            val kept = assertNotNull(all.firstOrNull { it.id == sessionId })
            assertEquals(SessionStatus.Postponed, kept.status)
            assertEquals(instant("2026-08-15", "14:00"), kept.startsAt, "the original day does not move")

            val replacement = assertNotNull(all.firstOrNull { it.id != sessionId })
            assertEquals(instant("2026-09-19", "14:00"), replacement.startsAt)
            assertEquals(
                SessionStatus.Confirmed,
                replacement.status,
                "the new day carries the status the form gave it",
            )
            assertEquals(zone.id, replacement.timeZoneId, "the shoot did not move to a different zone")
        }

    @Test
    fun `a moved day is never itself marked postponed`() =
        runTest {
            val (sessions, viewModel) = harness()

            // The form was left showing the status of the day being replaced.
            viewModel.moveSession(newSession(date = "2026-09-19", status = SessionStatus.Postponed))

            val replacement =
                assertNotNull(
                    sessions
                        .observeSessions()
                        .first()
                        .firstOrNull { it.id != sessionId },
                )
            assertEquals(
                SessionStatus.Scheduled,
                replacement.status,
                "a day carried over as postponed would be a shoot nobody is expected at",
            )
        }

    @Test
    fun `an edit with an unreadable time leaves the day alone`() =
        runTest {
            val (sessions, viewModel) = harness()

            viewModel.updateSession(newSession(startTime = "half two"))

            assertEquals(
                instant("2026-08-15", "14:00"),
                sessions
                    .observeSessions()
                    .first()
                    .single()
                    .startsAt,
            )
        }

    @Test
    fun `cancelling is done by editing the status, and keeps the day`() =
        runTest {
            val (sessions, viewModel) = harness()

            viewModel.updateSession(newSession(status = SessionStatus.Cancelled))

            val stored =
                sessions
                    .observeSessions()
                    .first()
                    .single()
            assertEquals(SessionStatus.Cancelled, stored.status)
            assertTrue(!stored.status.occupiesCalendar)
        }

    @Test
    fun `a coordinate survives being moved to a new date`() =
        runTest {
            val (sessions, viewModel) =
                harness(session(coordinates = GeoCoordinates(latitude = 50.2, longitude = -5.5)))

            viewModel.moveSession(newSession(date = "2026-09-19", latitude = "50.2", longitude = "-5.5"))

            val replacement =
                assertNotNull(
                    sessions
                        .observeSessions()
                        .first()
                        .firstOrNull { it.id != sessionId },
                )
            assertNotNull(replacement.coordinates, "the shoot did not move to a different place")
        }

    // --- The light ---------------------------------------------------------------------

    @Test
    fun `the light panel is absent without a coordinate`() =
        runTest {
            val (_, viewModel) = harness()

            assertNull(
                viewModel.model().light,
                "nothing is invented for a session whose place is unknown",
            )
        }

    @Test
    fun `the light panel names both golden hours and where the sun is at the start`() =
        runTest {
            val (_, viewModel) =
                harness(session(coordinates = GeoCoordinates(latitude = 50.2, longitude = -5.5)))

            val light = assertNotNull(viewModel.model().light)

            assertEquals(
                2,
                light.rows.count { it.label == "Golden hour" },
                "a day has a golden hour at each end, and both are worth planning around",
            )
            assertTrue(light.rows.any { it.label == "Sunset" })

            // 14:00 on a Cornish August afternoon: the sun is high and to the south.
            val atStart = assertNotNull(light.sunAtStart)
            assertTrue(atStart.contains("south"), "was: $atStart")
            assertNull(light.note, "no polar note applies at this latitude")
        }

    @Test
    fun `the polar case is stated rather than left as a missing window`() =
        runTest {
            val tromso = GeoCoordinates(latitude = 69.6496, longitude = 18.9560)
            // Midsummer inside the Arctic circle. In August the sun does set here, so the
            // date matters as much as the latitude.
            val (_, viewModel) = harness(session(coordinates = tromso, date = "2026-06-21"))

            val light = assertNotNull(viewModel.model().light)

            assertEquals("The sun does not set on this date at this latitude.", light.note)
        }

    // --- The shot list -----------------------------------------------------------------

    @Test
    fun `shots are gathered under their groups, in the order they were written`() =
        runTest {
            val (_, viewModel) = harness()

            viewModel.addShot(NewShot("Bride with her grandmother", "Bride's family", "Grandma Ruth"))
            viewModel.addShot(NewShot("Bride with both parents", "Bride's family", ""))
            viewModel.addShot(NewShot("Groom with his brothers", "Groom's side", ""))

            val groups = viewModel.model().shotGroups
            assertEquals(
                listOf("Bride's family", "Groom's side"),
                groups.map { it.name },
                "the order they were written is the order they will be worked",
            )
            assertEquals(2, groups.first().shots.size)
            assertEquals(
                "Grandma Ruth",
                groups
                    .first()
                    .shots
                    .first()
                    .people,
            )
        }

    @Test
    fun `a shot with no group falls under everything else`() =
        runTest {
            val (_, viewModel) = harness()

            viewModel.addShot(NewShot("Detail of the rings", "", ""))

            assertEquals(
                "Everything else",
                viewModel
                    .model()
                    .shotGroups
                    .single()
                    .name,
            )
        }

    @Test
    fun `a group knows how many shots it still owes`() =
        runTest {
            val (_, viewModel) = harness()
            viewModel.addShot(NewShot("Bride with her grandmother", "Bride's family", ""))
            viewModel.addShot(NewShot("Bride with both parents", "Bride's family", ""))

            val first =
                viewModel
                    .model()
                    .shotGroups
                    .single()
                    .shots
                    .first()
            viewModel.setShotCaptured(first.id, isCaptured = true)

            val group = viewModel.model().shotGroups.single()
            assertEquals(1, group.remaining, "the figure that decides whether this group can go")
            assertFalse(group.isComplete)
        }

    @Test
    fun `a group that owes nothing is complete, and the day says so`() =
        runTest {
            val (_, viewModel) = harness()
            viewModel.addShot(NewShot("Bride with her grandmother", "Bride's family", ""))

            val shot =
                viewModel
                    .model()
                    .shotGroups
                    .single()
                    .shots
                    .single()
            viewModel.setShotCaptured(shot.id, isCaptured = true)

            val model = viewModel.model()
            assertTrue(model.shotGroups.single().isComplete)
            assertEquals(0, model.shotsRemaining)
        }

    @Test
    fun `marking a shot taken stamps when, and unmarking clears it`() =
        runTest {
            val (_, viewModel) = harness()
            viewModel.addShot(NewShot("Bride with her grandmother", "Bride's family", ""))
            val shotId =
                viewModel
                    .model()
                    .shotGroups
                    .single()
                    .shots
                    .single()
                    .id

            viewModel.setShotCaptured(shotId, isCaptured = true)
            assertNotNull(assertNotNull(shots.getShot(shotId)).capturedAt)

            viewModel.setShotCaptured(shotId, isCaptured = false)
            assertNull(
                assertNotNull(shots.getShot(shotId)).capturedAt,
                "a shot put back on the list was not taken at any time",
            )
        }

    @Test
    fun `a shot remembered late lands with its own group, not at the bottom`() =
        runTest {
            val (_, viewModel) = harness()
            viewModel.addShot(NewShot("Bride with her grandmother", "Bride's family", ""))
            viewModel.addShot(NewShot("Groom with his brothers", "Groom's side", ""))
            viewModel.addShot(NewShot("Bride with her godmother", "Bride's family", ""))

            val brideSide = viewModel.model().shotGroups.first { it.name == "Bride's family" }
            assertEquals(
                listOf("Bride with her grandmother", "Bride with her godmother"),
                brideSide.shots.map { it.description },
                "calling a group back is the cost of filing a late shot at the end",
            )
        }

    @Test
    fun `a shot with no description is not added`() =
        runTest {
            val (_, viewModel) = harness()

            viewModel.addShot(NewShot("   ", "Bride's family", ""))

            assertTrue(viewModel.model().shotGroups.isEmpty())
        }

    @Test
    fun `a removed shot leaves the list`() =
        runTest {
            val (_, viewModel) = harness()
            viewModel.addShot(NewShot("Bride with her grandmother", "Bride's family", ""))
            val shotId =
                viewModel
                    .model()
                    .shotGroups
                    .single()
                    .shots
                    .single()
                    .id

            viewModel.deleteShot(shotId)

            assertTrue(viewModel.model().shotGroups.isEmpty())
        }

    // --- Crew --------------------------------------------------------------------------

    @Test
    fun `crew are listed earliest call first, which is the order the morning happens in`() =
        runTest {
            val (_, viewModel) = harness()

            viewModel.addCrewMember(NewCrewMember("Alex Reed", CrewRole.Videographer, "", "15:00"))
            viewModel.addCrewMember(NewCrewMember("Priya Shah", CrewRole.MakeUp, "", "09:00"))
            viewModel.addCrewMember(NewCrewMember("Sam Ellis", CrewRole.SecondShooter, "", "13:30"))

            assertEquals(
                listOf("Priya Shah", "Sam Ellis", "Alex Reed"),
                viewModel.model().crew.map { it.name },
                "hair and make-up are called first and the videographer last",
            )
        }

    @Test
    fun `a call time is read in the session's own zone`() =
        runTest {
            val (_, viewModel) = harness()

            viewModel.addCrewMember(NewCrewMember("Priya Shah", CrewRole.MakeUp, "", "09:00"))

            val member = assertNotNull(crew.observeCrewForSession(sessionId).first().singleOrNull())
            assertEquals(
                instant("2026-08-15", "09:00"),
                member.callTime,
                "the crew are called at nine where the shoot is, not where the studio is",
            )
        }

    @Test
    fun `someone with no call time of their own sorts last rather than first`() =
        runTest {
            val (_, viewModel) = harness()

            viewModel.addCrewMember(NewCrewMember("Sam Ellis", CrewRole.SecondShooter, "", ""))
            viewModel.addCrewMember(NewCrewMember("Priya Shah", CrewRole.MakeUp, "", "09:00"))

            val listed = viewModel.model().crew
            assertEquals(
                listOf("Priya Shah", "Sam Ellis"),
                listed.map { it.name },
                "a missing time means whenever, not before everyone",
            )
            assertNull(listed.last().callTimeLabel)
        }

    @Test
    fun `an unreadable call time is not recorded at all`() =
        runTest {
            val (_, viewModel) = harness()

            viewModel.addCrewMember(NewCrewMember("Priya Shah", CrewRole.MakeUp, "", "half nine"))

            assertTrue(
                crew
                    .observeCrewForSession(sessionId)
                    .first()
                    .isEmpty(),
                "a call sheet with a wrong time is worse than one with none",
            )
        }

    @Test
    fun `a crew member with no name is not added`() =
        runTest {
            val (_, viewModel) = harness()

            viewModel.addCrewMember(NewCrewMember("  ", CrewRole.Assistant, "", "09:00"))

            assertTrue(viewModel.model().crew.isEmpty())
        }

    @Test
    fun `the role is carried through as it would be written on a call sheet`() =
        runTest {
            val (_, viewModel) = harness()

            viewModel.addCrewMember(NewCrewMember("Priya Shah", CrewRole.MakeUp, "07700 900123", "09:00"))

            val member = viewModel.model().crew.single()
            assertEquals("Hair & make-up", member.role)
            assertEquals("07700 900123", member.phone)
        }

    @Test
    fun `someone removed leaves the day`() =
        runTest {
            val (_, viewModel) = harness()
            viewModel.addCrewMember(NewCrewMember("Sam Ellis", CrewRole.SecondShooter, "", "13:30"))
            val id =
                viewModel
                    .model()
                    .crew
                    .single()
                    .id

            viewModel.deleteCrewMember(id)

            assertTrue(viewModel.model().crew.isEmpty())
        }
}
