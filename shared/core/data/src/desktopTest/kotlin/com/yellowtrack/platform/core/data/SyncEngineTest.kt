package com.yellowtrack.platform.core.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.internal.SqlDelightClientRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightProjectRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightSessionRepository
import com.yellowtrack.platform.core.data.sync.ChangesToPush
import com.yellowtrack.platform.core.data.sync.PullPage
import com.yellowtrack.platform.core.data.sync.PushAck
import com.yellowtrack.platform.core.data.sync.PushOutcome
import com.yellowtrack.platform.core.data.sync.SyncEngine
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.sync.SyncConflict
import com.yellowtrack.platform.core.model.sync.SyncConflictId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The device half of reconciliation.
 *
 * Every property here fails silently in the field. A row that never uploads is a booking
 * the other device does not have; a cursor advanced too far is a change nobody is offered
 * again; a pulled row queued back for upload is two devices talking past each other
 * forever. None of them throw, and none of them look wrong on the screen of the device that
 * caused them.
 */
class SyncEngineTest {
    // -- The outbox fills ---------------------------------------------------------------

    @Test
    fun `saving a client notes that it needs uploading`() =
        runTest {
            val world = world()

            world.clients.saveClient(client("c1", "Ada Okafor"))

            val pending = world.outbox()
            assertEquals(1, pending.size)
            assertEquals("client" to "c1", pending.single())
        }

    @Test
    fun `deleting a client queues the tombstone, rather than nothing`() =
        runTest {
            val world = world()
            world.clients.saveClient(client("c1", "Ada Okafor"))
            world.drainQuietly()

            world.clients.deleteClient(ClientId("c1"))

            assertEquals(
                listOf("client" to "c1"),
                world.outbox(),
                "a delete that never uploads is a booking that comes back from the dead on the " +
                    "other device",
            )
        }

    @Test
    fun `the queued payload stays empty, because the row is re-read at upload time`() =
        runTest {
            val world = world()
            world.clients.saveClient(client("c1", "Ada Okafor"))

            val payloads =
                world.database.outboxQueries
                    .selectPending(STUDIO.value, 10)
                    .awaitAsList()
                    .map { it.payload }

            assertEquals(
                listOf(null),
                payloads,
                "a payload captured at queue time is a photograph of a row that has since changed",
            )
        }

    // -- Draining ------------------------------------------------------------------------

    @Test
    fun `a saved client is uploaded and the entry is cleared`() =
        runTest {
            val world = world()
            world.clients.saveClient(client("c1", "Ada Okafor"))

            val report = world.engine.sync()

            assertEquals(1, report.uploaded)
            assertEquals(
                listOf("Ada Okafor"),
                world.transport.pushed
                    .single()
                    .clients
                    .map { it.accountName },
            )
            assertTrue(world.outbox().isEmpty(), "an entry that survives its upload is uploaded again forever")
        }

    @Test
    fun `three edits to one booking upload once, not three times`() =
        runTest {
            val world = world()
            val original = client("c1", "Ada Okafor")
            world.clients.saveClient(original)
            world.clients.saveClient(original.copy(accountName = "Ada Okafor-B"))
            world.clients.saveClient(original.copy(accountName = "Ada Okafor-Bell"))

            assertEquals(3, world.outbox().size, "each edit queues an entry")

            world.engine.sync()

            assertEquals(
                listOf("Ada Okafor-Bell"),
                world.transport.pushed
                    .single()
                    .clients
                    .map { it.accountName },
                "but there is one row to send, and it is the current one. Sending it three times " +
                    "would be three chances to conflict over work already superseded",
            )
            assertTrue(world.outbox().isEmpty(), "and all three entries are settled by the one upload")
        }

    @Test
    fun `nothing to upload means nothing is sent`() =
        runTest {
            val world = world()

            world.engine.sync()

            assertTrue(world.transport.pushed.isEmpty(), "an empty drain must not chatter at the server")
        }

    @Test
    fun `a conflict still clears the entry, because the server stored it`() =
        runTest {
            val world =
                world(
                    onPush = { changes ->
                        changes.clients.map { PushAck("client", it.id.value, PushOutcome.Conflicted, 7) }
                    },
                )
            world.clients.saveClient(client("c1", "Ada Okafor"))

            val report = world.engine.sync()

            assertEquals(1, report.conflicted)
            assertTrue(
                world.outbox().isEmpty(),
                "Conflicted means the server took this version and kept the one it displaced. " +
                    "Retrying would push it at the other device again",
            )
        }

    @Test
    fun `a rejected row is kept, with the reason recorded`() =
        runTest {
            val world =
                world(
                    onPush = { changes ->
                        changes.clients.map {
                            PushAck(
                                "client",
                                it.id.value,
                                PushOutcome.Rejected,
                                1,
                                "that row belongs to another studio",
                            )
                        }
                    },
                )
            world.clients.saveClient(client("c1", "Ada Okafor"))

            val report = world.engine.sync()

            assertEquals(1, report.rejected)
            assertEquals(1, world.outbox().size, "a rejection is a bug to look at, not work to discard")

            val entry =
                world.database.outboxQueries
                    .selectPending(STUDIO.value, 10)
                    .awaitAsList()
                    .single()
            assertEquals(1L, entry.attempts)
            assertEquals("that row belongs to another studio", entry.last_error)
        }

    @Test
    fun `a push that never answers leaves the work queued`() =
        runTest {
            val world = world()
            world.clients.saveClient(client("c1", "Ada Okafor"))
            world.transport.failNextPush = RuntimeException("connection dropped")

            runCatching { world.engine.sync() }

            assertEquals(
                1,
                world.outbox().size,
                "a phone that lost signal mid-upload must still have the work when it comes back",
            )
        }

    // -- Applying ------------------------------------------------------------------------

    @Test
    fun `a client from the server lands in the local database`() =
        runTest {
            val world = world()
            world.transport.pages +=
                PullPage(cursor = 12, hasMore = false, clients = listOf(client("remote-1", "Harbourline Coffee")))

            val report = world.engine.sync()

            assertEquals(1, report.downloaded)
            assertEquals("Harbourline Coffee", world.clients.getClient(ClientId("remote-1"))?.accountName)
        }

    @Test
    fun `applying what arrived does not queue it straight back for upload`() =
        runTest {
            val world = world()
            world.transport.pages +=
                PullPage(cursor = 12, hasMore = false, clients = listOf(client("remote-1", "Harbourline Coffee")))

            world.engine.sync()

            assertTrue(
                world.outbox().isEmpty(),
                "a pulled row queued back for upload is two devices pushing the same booking at " +
                    "each other for as long as both are running",
            )
        }

    @Test
    fun `a tombstone from the server deletes the local row`() =
        runTest {
            val world = world()
            world.clients.saveClient(client("c1", "Ada Okafor"))
            world.drainQuietly()

            world.transport.pages +=
                PullPage(
                    cursor = 20,
                    hasMore = false,
                    clients =
                        listOf(
                            client("c1", "Ada Okafor").copy(audit = AuditMetadata.createdAt(NOW).deleted(NOW)),
                        ),
                )
            world.engine.sync()

            assertNull(
                world.clients.getClient(ClientId("c1")),
                "a delete made on the laptop has to reach the phone, or the phone keeps the booking",
            )
        }

    @Test
    fun `the cursor is remembered, and the next pull starts from it`() =
        runTest {
            val world = world()
            world.transport.pages += PullPage(cursor = 42, hasMore = false)

            world.engine.sync()
            assertEquals(42L, world.cursor())

            world.engine.sync()
            assertEquals(
                listOf(0L, 42L),
                world.transport.pullsSince,
                "the second sync must resume where the first stopped, or every sync downloads " +
                    "the studio's whole history",
            )
        }

    @Test
    fun `several pages are followed until the server says there are no more`() =
        runTest {
            val world = world()
            world.transport.pages +=
                PullPage(cursor = 10, hasMore = true, clients = listOf(client("a", "One")))
            world.transport.pages +=
                PullPage(cursor = 20, hasMore = true, clients = listOf(client("b", "Two")))
            world.transport.pages +=
                PullPage(cursor = 30, hasMore = false, clients = listOf(client("c", "Three")))

            val report = world.engine.sync()

            assertEquals(3, report.downloaded)
            assertEquals(30L, world.cursor())
            assertEquals(
                listOf(0L, 10L, 20L),
                world.transport.pullsSince,
                "stopping after the first page would leave the rest unfetched until something " +
                    "else happened to change",
            )
        }

    @Test
    fun `a conflict raised on another device arrives here`() =
        runTest {
            val world = world()
            world.transport.pages +=
                PullPage(
                    cursor = 9,
                    hasMore = false,
                    conflicts =
                        listOf(
                            SyncConflict(
                                id = SyncConflictId("conflict-1"),
                                studioId = STUDIO,
                                entityTable = "session",
                                entityId = "s1",
                                losingPayload = """{"title":"Ceremony — 2pm"}""",
                                winningPayload = """{"title":"Ceremony — 3pm"}""",
                                detectedAt = NOW,
                                audit = AuditMetadata.createdAt(NOW),
                            ),
                        ),
                )

            world.engine.sync()

            val stored =
                world.database.syncQueries
                    .selectUnresolvedConflicts(STUDIO.value)
                    .awaitAsList()
                    .single()
            assertEquals("session", stored.entity_table)
            assertTrue(
                stored.losing_payload.contains("Ceremony — 2pm"),
                "the discarded version has to reach the device, or the studio is never told what " +
                    "reconciliation threw away — which is the condition ADR 0008 put on " +
                    "last-write-wins in the first place",
            )
            assertNull(stored.resolved_at, "and it arrives unresolved rather than pre-dismissed")
        }

    @Test
    fun `the same conflict arriving twice does not reopen one already dealt with`() =
        runTest {
            val world = world()
            val conflict =
                SyncConflict(
                    id = SyncConflictId("conflict-1"),
                    studioId = STUDIO,
                    entityTable = "session",
                    entityId = "s1",
                    losingPayload = "{}",
                    winningPayload = "{}",
                    detectedAt = NOW,
                    audit = AuditMetadata.createdAt(NOW),
                )

            world.transport.pages += PullPage(cursor = 5, hasMore = false, conflicts = listOf(conflict))
            world.engine.sync()

            world.database.syncQueries.markConflictResolved(NOW.toEpochMilliseconds(), "conflict-1")

            world.transport.pages += PullPage(cursor = 6, hasMore = false, conflicts = listOf(conflict))
            world.engine.sync()

            assertEquals(
                0,
                world.database.syncQueries
                    .countUnresolvedConflicts(STUDIO.value)
                    .awaitAsOne()
                    .toInt(),
                "a conflict somebody has already dealt with must not come back on the next sync",
            )
        }

    // -- Order ---------------------------------------------------------------------------

    @Test
    fun `uploading happens before downloading, so the losing version still exists to be kept`() =
        runTest {
            val world = world()
            world.clients.saveClient(client("c1", "Mine"))
            world.transport.pages +=
                PullPage(cursor = 3, hasMore = false, clients = listOf(client("c1", "Theirs")))

            world.engine.sync()

            assertEquals(
                listOf("Mine"),
                world.transport.pushed
                    .single()
                    .clients
                    .map { it.accountName },
                "pulling first would overwrite this device's version and then upload the server's " +
                    "own row back to it — the studio's work gone before anything noticed it was " +
                    "in danger",
            )
            assertEquals("Theirs", world.clients.getClient(ClientId("c1"))?.accountName)
        }

    // -- Plumbing --------------------------------------------------------------------------

    private class World(
        val database: com.yellowtrack.platform.core.database.YellowTrackDatabase,
        val engine: SyncEngine,
        val transport: FakeSyncTransport,
        val clients: ClientRepository,
    ) {
        suspend fun outbox(): List<Pair<String, String>> =
            database.outboxQueries
                .selectPending(STUDIO.value, 100)
                .awaitAsList()
                .map { it.entity_table to it.entity_id }

        suspend fun cursor(): Long =
            database.syncQueries
                .selectCursor(STUDIO.value)
                .awaitAsOneOrNull()
                ?.last_server_seq ?: 0L

        /** Clears the outbox without asserting anything, when a test needs a clean slate. */
        suspend fun drainQuietly() {
            engine.sync()
            transport.pushed.clear()
            transport.pullsSince.clear()
        }
    }

    private suspend fun world(onPush: ((ChangesToPush) -> List<PushAck>)? = null): World {
        // One provider shared by everything, so the engine and the repositories are looking
        // at the same database rather than at three of them.
        val provider = testDatabaseProvider()
        val studioContext = LocalStudioContext()
        val clock = AppClock { NOW }
        val transport = if (onPush == null) FakeSyncTransport() else FakeSyncTransport(onPush)

        val clients = SqlDelightClientRepository(provider, studioContext, clock, Dispatchers.Unconfined)
        val projects = SqlDelightProjectRepository(provider, studioContext, clock, Dispatchers.Unconfined)
        val sessions = SqlDelightSessionRepository(provider, studioContext, clock, Dispatchers.Unconfined)

        return World(
            database = provider.database(),
            engine = SyncEngine(provider, studioContext, transport, clients, projects, sessions, clock),
            transport = transport,
            clients = clients,
        )
    }

    private fun client(
        id: String,
        name: String,
    ) = Client(
        id = ClientId(id),
        studioId = STUDIO,
        accountName = name,
        accountType = ClientAccountType.Individual,
        audit = AuditMetadata.createdAt(NOW),
    )

    private companion object {
        val STUDIO = TEST_STUDIO_ID
        val NOW: Instant = TEST_NOW
    }
}
