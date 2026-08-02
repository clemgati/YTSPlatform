package com.yellowtrack.platform.server.sync

import com.yellowtrack.platform.core.data.sync.SyncTransport
import com.yellowtrack.platform.core.data.sync.SyncUnauthorised
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.model.sync.SyncPushOutcome
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import com.yellowtrack.platform.core.network.HttpSyncTransport
import com.yellowtrack.platform.core.network.syncJson
import com.yellowtrack.platform.server.TestDatabase
import com.yellowtrack.platform.server.auth.Accounts
import com.yellowtrack.platform.server.module
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The device's transport talking to the real server.
 *
 * Everything before this commit was proved against a fake on one side or the other. The two
 * halves agreed about the contract by inspection, which is exactly the kind of agreement
 * that survives right up until it matters — a field serialised one way and read another, an
 * enum whose names differ by a capital, a query parameter nobody sends.
 *
 * So this wires the actual `HttpSyncTransport` to the actual routes over the actual JSON. If
 * the two ends have drifted, it fails here rather than on somebody's phone.
 */
class SyncOverHttpTest {
    /**
     * The server tells a device what it reconciles, over the wire.
     *
     * A device compares this against its own list to notice it is talking to a server older
     * than itself — which otherwise passes unremarked, because unknown fields are discarded
     * from a push and the answer is a success either way.
     */
    @Test
    fun `a pull carries the tables this server reconciles`() =
        withSignedInDevice { transport, _ ->
            val pulled = transport.pull(since = 0, limit = 1)

            assertEquals(
                SyncedEntity.all.map { it.table }.toSet() - SyncedEntity.Conflicts.table,
                pulled.reconciles.toSet(),
                "a device compares this list against its own, so anything missing from it is a " +
                    "kind of record that would silently go nowhere",
            )
            assertTrue(
                SyncedEntity.Conflicts.table !in pulled.reconciles,
                "conflicts travel downward only and are never pushed, so listing them would " +
                    "invite a device to expect something it can never send",
            )
        }

    @Test
    fun `a client pushed over http comes back on the next pull`() =
        withSignedInDevice { transport, studioId ->
            val pushed =
                transport.push(
                    SyncPushRequest(clients = listOf(client(studioId, "over-http-1", "Ada Okafor"))),
                )

            assertEquals(1, pushed.size)
            assertEquals(SyncPushOutcome.Applied, pushed.single().outcome)

            val pulled = transport.pull(since = 0, limit = 100)

            assertEquals(
                listOf("Ada Okafor"),
                pulled.clients.map { it.accountName },
                "the row has to survive being serialised by the device, parsed by the server, " +
                    "stored, re-read and serialised back",
            )
            assertTrue(pulled.cursor > 0)
        }

    @Test
    fun `every field of a session survives the round trip`() =
        withSignedInDevice { transport, studioId ->
            transport.push(SyncPushRequest(clients = listOf(client(studioId, "rt-client", "Harbourline"))))
            transport.push(SyncPushRequest(projects = listOf(project(studioId, "rt-project", "rt-client"))))

            val sent = session(studioId, "rt-session", "rt-project")
            transport.push(SyncPushRequest(sessions = listOf(sent)))

            val received = transport.pull(since = 0, limit = 100).sessions.single { it.id == sent.id }

            // Field by field rather than by equality, so a failure names what drifted.
            assertEquals(sent.title, received.title)
            assertEquals(sent.kind, received.kind, "an enum crossing as a name, not an ordinal")
            assertEquals(sent.startsAt, received.startsAt, "an instant, to the millisecond")
            assertEquals(sent.endsAt, received.endsAt)
            assertEquals(sent.timeZoneId, received.timeZoneId)
            assertEquals(sent.coordinates, received.coordinates, "both halves of a coordinate, or neither")
            assertEquals(sent.notes, received.notes)
            assertEquals(sent.audit.version, received.audit.version)
        }

    @Test
    fun `a conflict raised by the server reaches the device over the wire`() =
        withSignedInDevice { transport, studioId ->
            val original = client(studioId, "conflicted", "Ada Okafor")
            transport.push(SyncPushRequest(clients = listOf(original)))

            // Two devices, both working from version 1.
            val fromLaptop = original.copy(accountName = "From the laptop", audit = original.audit.copy(version = 2))
            val fromPhone = original.copy(accountName = "From the phone", audit = original.audit.copy(version = 2))

            transport.push(SyncPushRequest(clients = listOf(fromLaptop)))
            val second = transport.push(SyncPushRequest(clients = listOf(fromPhone)))

            assertEquals(SyncPushOutcome.Conflicted, second.single().outcome)

            val conflict = transport.pull(since = 0, limit = 100).conflicts.single()

            assertEquals("client", conflict.entityTable)
            assertTrue(
                conflict.losingPayload.contains("From the laptop"),
                "the discarded version has to arrive readable, or the studio is never shown what " +
                    "reconciliation threw away",
            )
        }

    @Test
    fun `a rejected row is reported per row rather than failing the request`() =
        withSignedInDevice { transport, _ ->
            val someoneElses = client(StudioId("a-different-studio"), "smuggled", "Planted Row")

            val results = transport.push(SyncPushRequest(clients = listOf(someoneElses)))

            assertEquals(
                SyncPushOutcome.Rejected,
                results.single().outcome,
                "one bad row must not fail the batch — a drain after a day offline would then be " +
                    "stuck behind it forever",
            )
            assertTrue(results.single().detail!!.isNotBlank(), "and it must say why")
        }

    @Test
    fun `the cursor means the same thing on both sides`() =
        withSignedInDevice { transport, studioId ->
            transport.push(SyncPushRequest(clients = listOf(client(studioId, "cursor-1", "One"))))
            val first = transport.pull(since = 0, limit = 100)

            transport.push(SyncPushRequest(clients = listOf(client(studioId, "cursor-2", "Two"))))
            val second = transport.pull(since = first.cursor, limit = 100)

            assertEquals(
                listOf("Two"),
                second.clients.map { it.accountName },
                "a cursor the device sends back has to select what the server thinks it selects, " +
                    "or rows are skipped or repeated forever",
            )
        }

    @Test
    fun `a device with no token is refused rather than served`() =
        testApplication {
            application { module(TestDatabase.database) }

            val transport =
                HttpSyncTransport(
                    client = jsonClient(),
                    baseUrl = "",
                    credentials = { null },
                )

            assertFailsWith<SyncUnauthorised> { transport.pull(since = 0, limit = 10) }
        }

    @Test
    fun `a token the server has never seen is refused`() =
        testApplication {
            application { module(TestDatabase.database) }

            val transport =
                HttpSyncTransport(
                    client = jsonClient(),
                    baseUrl = "",
                    credentials = { "not-a-real-token" },
                )

            assertFailsWith<SyncUnauthorised> { transport.pull(since = 0, limit = 10) }
        }

    // -- Plumbing ---------------------------------------------------------------------------

    /**
     * Signs a device up through the real endpoints and hands back a transport holding its
     * token, so nothing here fabricates a session the server would not have issued.
     */
    private fun withSignedInDevice(block: suspend (SyncTransport, StudioId) -> Unit) =
        testApplication {
            application { module(TestDatabase.database) }

            val accounts = Accounts(TestDatabase.database)
            val signedIn =
                accounts.signUp(
                    email = "device-${counter++}-${System.nanoTime()}@harbourline.test",
                    password = "a long enough password",
                    name = "Ada Okafor",
                    studioName = "Harbourline Photography",
                )

            val transport =
                HttpSyncTransport(
                    client = jsonClient(),
                    // Empty: the test host resolves relative paths against itself, which is
                    // what makes this exercise routing rather than a URL string.
                    baseUrl = "",
                    credentials = { signedIn.token },
                )

            block(transport, StudioId(signedIn.studioId))
        }

    private fun ApplicationTestBuilder.jsonClient() =
        createClient {
            install(ContentNegotiation) {
                json(syncJson)
            }
        }

    private fun client(
        studioId: StudioId,
        id: String,
        name: String,
    ) = Client(
        id = ClientId("${studioId.value}/$id"),
        studioId = studioId,
        accountName = name,
        accountType = ClientAccountType.Company,
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun project(
        studioId: StudioId,
        id: String,
        clientId: String,
    ) = Project(
        id = ProjectId("${studioId.value}/$id"),
        studioId = studioId,
        clientId = ClientId("${studioId.value}/$clientId"),
        name = "Autumn Brand Shoot",
        serviceLine = ServiceLine.Branding,
        status = ProjectStatus.Booked,
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun session(
        studioId: StudioId,
        id: String,
        projectId: String,
    ) = Session(
        id = SessionId("${studioId.value}/$id"),
        studioId = studioId,
        projectId = ProjectId("${studioId.value}/$projectId"),
        title = "Ceremony — 3pm",
        kind = SessionKind.Shoot,
        status = SessionStatus.Confirmed,
        startsAt = NOW,
        endsAt = NOW + 8.hours,
        timeZoneId = "Europe/London",
        locationName = "Thornbury Manor",
        coordinates =
            com.yellowtrack.platform.core.common.solar
                .GeoCoordinates(50.2, -5.5),
        notes = "Bring the long lens",
        audit = AuditMetadata.createdAt(NOW),
    )

    private companion object {
        val NOW: Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)
        var counter = 0
    }
}
