package com.yellowtrack.platform.core.data

import app.cash.turbine.test
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.internal.SqlDelightClientRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightProjectRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightSessionRepository
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.session.SessionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class SessionRepositoryTest {
    private class Harness(
        provider: DatabaseProvider = testDatabaseProvider(),
    ) {
        private val clock = AppClock { TEST_NOW }

        val clients =
            SqlDelightClientRepository(
                provider,
                LocalStudioContext(),
                clock,
                Dispatchers.Unconfined,
                RemoteWriter(AcceptingTransport),
            )
        val projects =
            SqlDelightProjectRepository(
                provider,
                LocalStudioContext(),
                clock,
                Dispatchers.Unconfined,
                RemoteWriter(AcceptingTransport),
            )
        val sessions =
            SqlDelightSessionRepository(
                provider,
                LocalStudioContext(),
                clock,
                Dispatchers.Unconfined,
                RemoteWriter(AcceptingTransport),
            )
    }

    @Test
    fun `a wedding is one project holding both the engagement shoot and the wedding day`() =
        runTest {
            val harness = Harness()

            val client = Fixtures.couple()
            harness.clients.saveClient(client)

            val wedding = Fixtures.project(clientId = client.id, name = "Johnson Wedding")
            harness.projects.saveProject(wedding)

            harness.sessions.saveSession(
                Fixtures.session(
                    projectId = wedding.id,
                    title = "Engagement Shoot",
                    startsAt = TEST_NOW - 60.days,
                    durationMinutes = 120,
                ),
            )
            harness.sessions.saveSession(
                Fixtures.session(projectId = wedding.id, title = "Wedding Day", startsAt = TEST_NOW),
            )

            val booked = harness.sessions.observeSessionsForProject(wedding.id).first()

            assertEquals(listOf("Engagement Shoot", "Wedding Day"), booked.map { it.title })
            assertEquals(
                4_500_00L,
                assertNotNull(harness.projects.getProject(wedding.id)?.contractValue).minorUnits,
                "contract value belongs to the project, not to either session",
            )
        }

    @Test
    fun `a commercial job holds a scout then a shoot then a pickup`() =
        runTest {
            val harness = Harness()

            val client = Fixtures.client(accountName = "Harborline Coffee")
            harness.clients.saveClient(client)

            val job = Fixtures.project(clientId = client.id, name = "Harborline Brand Film")
            harness.projects.saveProject(job)

            listOf(
                SessionKind.Scout to TEST_NOW - 7.days,
                SessionKind.Shoot to TEST_NOW,
                SessionKind.Pickup to TEST_NOW + 7.days,
            ).forEach { (kind, startsAt) ->
                harness.sessions.saveSession(
                    Fixtures.session(projectId = job.id, title = kind.name, kind = kind, startsAt = startsAt),
                )
            }

            assertEquals(
                listOf(SessionKind.Scout, SessionKind.Shoot, SessionKind.Pickup),
                harness.sessions
                    .observeSessionsForProject(job.id)
                    .first()
                    .map { it.kind },
            )
        }

    @Test
    fun `the interval query is half-open so a day cannot double-count a session`() =
        runTest {
            val harness = Harness()

            val client = Fixtures.client()
            harness.clients.saveClient(client)
            val project = Fixtures.project(clientId = client.id)
            harness.projects.saveProject(project)

            val boundary = TEST_NOW
            harness.sessions.saveSession(
                Fixtures.session(projectId = project.id, title = "On boundary", startsAt = boundary),
            )

            val before = harness.sessions.observeSessionsBetween(boundary - 1.days, boundary).first()
            val after = harness.sessions.observeSessionsBetween(boundary, boundary + 1.days).first()

            assertTrue(before.isEmpty(), "a session starting exactly at the exclusive end must not be included")
            assertEquals(listOf("On boundary"), after.map { it.title })
        }

    @Test
    fun `upcoming sessions are ordered and limited`() =
        runTest {
            val harness = Harness()

            val client = Fixtures.client()
            harness.clients.saveClient(client)
            val project = Fixtures.project(clientId = client.id)
            harness.projects.saveProject(project)

            listOf(3, 1, 2).forEach { offset ->
                harness.sessions.saveSession(
                    Fixtures.session(projectId = project.id, title = "Day $offset", startsAt = TEST_NOW + offset.days),
                )
            }

            assertEquals(
                listOf("Day 1", "Day 2"),
                harness.sessions
                    .observeUpcomingSessions(from = TEST_NOW, limit = 2)
                    .first()
                    .map { it.title },
            )
        }

    @Test
    fun `preserves the zone a session happens in`() =
        runTest {
            val harness = Harness()

            val client = Fixtures.client()
            harness.clients.saveClient(client)
            val project = Fixtures.project(clientId = client.id)
            harness.projects.saveProject(project)

            val session = Fixtures.session(projectId = project.id)
            harness.sessions.saveSession(session)

            val loaded = assertNotNull(harness.sessions.getSession(session.id))

            assertEquals("America/New_York", loaded.timeZoneId)
            assertEquals(10.hours, loaded.duration)
        }

    @Test
    fun `the dashboard sees a session the moment it is booked`() =
        runTest {
            val harness = Harness()

            val client = Fixtures.client()
            harness.clients.saveClient(client)
            val project = Fixtures.project(clientId = client.id)
            harness.projects.saveProject(project)

            harness.sessions.observeUpcomingSessions(from = TEST_NOW, limit = 5).test {
                assertEquals(emptyList(), awaitItem())

                harness.sessions.saveSession(
                    Fixtures.session(projectId = project.id, title = "Newly booked", startsAt = TEST_NOW + 1.days),
                )

                assertEquals(listOf("Newly booked"), awaitItem().map { it.title })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a session cannot reference a project that does not exist`() =
        runTest {
            val harness = Harness()
            val orphan = Fixtures.session(projectId = Fixtures.project(clientId = Fixtures.client().id).id)

            val result = runCatching { harness.sessions.saveSession(orphan) }

            assertTrue(result.isFailure, "foreign keys must be enforced")
        }
}
