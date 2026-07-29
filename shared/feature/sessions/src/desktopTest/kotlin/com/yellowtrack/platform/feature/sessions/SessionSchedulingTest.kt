package com.yellowtrack.platform.feature.sessions

import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.feature.sessions.presentation.SessionsViewModel
import com.yellowtrack.platform.feature.sessions.presentation.model.NewSession
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * Putting a day of work on the calendar.
 *
 * The case that matters most is the one that reads wrong and is right: a wedding running
 * from the afternoon into the small hours of the next morning.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionSchedulingTest {
    private val zone = TimeZone.of("Europe/London")
    private val projectId = ProjectId.new()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun harness(sessions: FakeSessionRepository = FakeSessionRepository()) =
        sessions to
            SessionsViewModel(
                sessionRepository = sessions,
                projectRepository = FakeProjectRepository(),
                clientRepository = FakeClientRepository(),
                studioContext = LocalStudioContext(),
                clock = TestAppClock(),
                deviceZone = zone,
            )

    private fun newSession(
        title: String = "Wedding day",
        date: String = "2026-08-15",
        startTime: String = "14:00",
        endTime: String = "23:00",
        callTime: String = "",
        kind: SessionKind = SessionKind.Shoot,
        status: SessionStatus = SessionStatus.Scheduled,
    ) = NewSession(
        projectId = projectId,
        title = title,
        kind = kind,
        status = status,
        date = date,
        startTime = startTime,
        endTime = endTime,
        callTime = callTime,
        locationName = "Thornbury Manor",
        locationAddress = "",
        notes = "",
    )

    private fun instant(
        date: String,
        time: String,
    ) = LocalDateTime.parse("${date}T$time").toInstant(zone)

    @Test
    fun `a session is scheduled inside its booking, in the zone it happens in`() =
        runTest {
            val (sessions, viewModel) = harness()

            viewModel.addSession(newSession(callTime = "13:00"))

            val stored =
                sessions
                    .observeSessions()
                    .first()
                    .single()
            assertEquals(projectId, stored.projectId)
            assertEquals("Wedding day", stored.title)
            assertEquals(zone.id, stored.timeZoneId, "a zone assumed on read is a zone read wrong")
            assertEquals(instant("2026-08-15", "14:00"), stored.startsAt)
            assertEquals(instant("2026-08-15", "23:00"), stored.endsAt)
            assertEquals(instant("2026-08-15", "13:00"), stored.callTime)
            assertEquals(9.hours, stored.duration)
        }

    @Test
    fun `a wedding running past midnight ends the next morning`() =
        runTest {
            val (sessions, viewModel) = harness()

            viewModel.addSession(newSession(startTime = "14:00", endTime = "01:00"))

            val stored =
                sessions
                    .observeSessions()
                    .first()
                    .single()
            assertEquals(
                instant("2026-08-16", "01:00"),
                stored.endsAt,
                "refusing this would make the commonest job in the business unenterable",
            )
            assertEquals(11.hours, stored.duration)
        }

    @Test
    fun `crew called before the start are called on the shoot's own day`() =
        runTest {
            val (sessions, viewModel) = harness()

            viewModel.addSession(newSession(startTime = "14:00", endTime = "01:00", callTime = "12:30"))

            assertEquals(
                instant("2026-08-15", "12:30"),
                sessions
                    .observeSessions()
                    .first()
                    .single()
                    .callTime,
            )
        }

    @Test
    fun `no call time means nobody is called earlier than the start`() =
        runTest {
            val (sessions, viewModel) = harness()

            viewModel.addSession(newSession(callTime = ""))

            assertNull(
                sessions
                    .observeSessions()
                    .first()
                    .single()
                    .callTime,
            )
        }

    @Test
    fun `an unreadable time is not scheduled`() =
        runTest {
            val (sessions, viewModel) = harness()

            viewModel.addSession(newSession(startTime = "2pm"))

            assertTrue(
                sessions
                    .observeSessions()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `an unreadable date is not scheduled`() =
        runTest {
            val (sessions, viewModel) = harness()

            viewModel.addSession(newSession(date = "next Saturday"))

            assertTrue(
                sessions
                    .observeSessions()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `a session with no title is not scheduled`() =
        runTest {
            val (sessions, viewModel) = harness()

            viewModel.addSession(newSession(title = "  "))

            assertTrue(
                sessions
                    .observeSessions()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `a scheduled session occupies the calendar`() =
        runTest {
            val (sessions, viewModel) = harness()

            viewModel.addSession(newSession(status = SessionStatus.Scheduled))

            assertTrue(
                sessions
                    .observeSessions()
                    .first()
                    .single()
                    .status.occupiesCalendar,
            )
        }
}
