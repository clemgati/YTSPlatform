package com.yellowtrack.platform.feature.sessions

import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.export.DocumentFormat
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.crew.CrewRole
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
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
import com.yellowtrack.platform.core.testing.RecordingDocumentSink
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Getting the day out of the application.
 *
 * The session page has read as a call sheet since 0.5.0 and has never been able to leave
 * the laptop. These are the two ways out: pasted into a message, and saved as a file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallSheetExportTest {
    private val zone = TimeZone.of("Europe/London")
    private val sessionId = SessionId.new()
    private val projectId = ProjectId.new()
    private val clientId = ClientId.new()
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
            locationName = "Thornbury Manor",
            callTime = LocalDateTime.parse("2026-08-15T12:30").toInstant(zone),
            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
        )

    private class Harness(
        val viewModel: SessionDetailsViewModel,
        val sink: RecordingDocumentSink,
    )

    private fun harness(sessions: List<Session> = listOf(session())): Harness {
        val sink = RecordingDocumentSink()

        return Harness(
            viewModel =
                SessionDetailsViewModel(
                    sessionId = sessionId,
                    sessionRepository = FakeSessionRepository(sessions),
                    shotRepository = FakeShotRepository(),
                    crewRepository =
                        FakeCrewRepository(
                            listOf(
                                CrewMember(
                                    id = CrewMemberId.new(),
                                    studioId = studioId,
                                    sessionId = sessionId,
                                    name = "Sam Ellis",
                                    role = CrewRole.SecondShooter,
                                    phone = "07700 900456",
                                    audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
                                ),
                            ),
                        ),
                    releaseRepository = FakeTalentReleaseRepository(),
                    mediaCopyRepository = FakeMediaCopyRepository(),
                    packingRepository = FakePackingRepository(),
                    gearRepository = FakeGearRepository(),
                    projectRepository =
                        FakeProjectRepository(
                            listOf(
                                Project(
                                    id = projectId,
                                    studioId = studioId,
                                    clientId = clientId,
                                    name = "Johnson Wedding",
                                    serviceLine = ServiceLine.Wedding,
                                    status = ProjectStatus.Booked,
                                    audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
                                ),
                            ),
                        ),
                    clientRepository =
                        FakeClientRepository(
                            listOf(
                                Client(
                                    id = clientId,
                                    studioId = studioId,
                                    accountName = "Sarah & Michael Johnson",
                                    accountType = ClientAccountType.Couple,
                                    audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
                                ),
                            ),
                        ),
                    documentSink = sink,
                    studioContext = LocalStudioContext(),
                    clock = TestAppClock(),
                    deviceZone = zone,
                ),
            sink = sink,
        )
    }

    @Test
    fun `the saved document is a web page named after the day`() =
        runTest {
            val harness = harness()

            harness.viewModel.exportCallSheet {}

            val document = assertNotNull(harness.sink.last)
            assertEquals(DocumentFormat.Html, document.format)
            assertEquals("call-sheet-wedding-day.html", document.fileName)
        }

    @Test
    fun `where the file went is reported back`() =
        runTest {
            val harness = harness()
            var reported: String? = null

            harness.viewModel.exportCallSheet { reported = it }

            assertEquals(
                "recorded/call-sheet-wedding-day.html",
                reported,
                "a document nobody can find was not saved",
            )
        }

    @Test
    fun `the sheet carries the day, the place, and the people`() =
        runTest {
            val harness = harness()
            val text = assertNotNull(harness.viewModel.callSheetText())

            assertTrue(text.contains("Wedding day"))
            assertTrue(text.contains("Thornbury Manor"))
            assertTrue(text.contains("12:30 PM"), "the call time is why the sheet exists")
            assertTrue(text.contains("Sam Ellis"))
            assertTrue(text.contains("07700 900456"), "a number to ring when someone is late")
        }

    @Test
    fun `an ampersand in a client name survives into the page`() =
        runTest {
            val harness = harness()

            harness.viewModel.exportCallSheet {}

            val html = assertNotNull(harness.sink.last).content
            assertTrue(html.contains("Sarah &amp; Michael Johnson"))
            assertFalse(html.contains("Michael Johnson</p>\n<p>"), "was: $html")
        }

    @Test
    fun `a session that has gone produces no document rather than an empty one`() =
        runTest {
            val harness = harness(sessions = emptyList())
            var reported: String? = null

            harness.viewModel.exportCallSheet { reported = it }

            assertTrue(harness.sink.documents.isEmpty())
            assertNull(reported)
            assertNull(harness.viewModel.callSheetText())
        }
}
