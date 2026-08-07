package com.yellowtrack.platform.core.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.internal.SqlDelightClientRepository
import com.yellowtrack.platform.core.data.internal.SyncTables
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.data.sync.SyncTransport
import com.yellowtrack.platform.core.data.sync.WriteFailed
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.sync.SyncPullResponse
import com.yellowtrack.platform.core.model.sync.SyncPushOutcome
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import com.yellowtrack.platform.core.model.sync.SyncPushResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Clients under ADR 0012, and the last entity of its step 2.
 *
 * Clients are the awkward one, which is why they were left until last. They carry children —
 * the contacts attached to them and the links joining the two — and a link's id is minted in
 * the repository rather than by the database. Under the old design that was fine: write
 * locally, queue three entries, let the outbox carry the news. Writing to the server first
 * means the whole change has to be **decided before any of it is written**, which is what
 * `planContacts` is for.
 *
 * The property these hold: a client, its people and their attachments arrive together or not
 * at all. A client whose contacts landed and whose links did not is a client displaying with
 * nobody attached — which reads as lost data rather than as a failed save.
 */
class ClientWritesOnlineTest {
    @Test
    fun `a client, its contacts and its links travel as one request`() =
        runTest {
            val transport = RecordingTransport()
            val clients = repository(transport = transport)

            clients.saveClient(client(withContact = true))

            val sent = transport.pushed.single()
            assertEquals(listOf("Okafor"), sent.clients.map { it.accountName })
            assertEquals(listOf("Ada"), sent.contacts.map { it.firstName }, "the person never left the device")
            assertEquals(1, sent.clientContactLinks.size, "the attachment never left the device")
        }

    @Test
    fun `a client that could not be sent is not left in the cache`() =
        runTest {
            val clients = repository(transport = RecordingTransport(failing = true))

            assertFailsWith<WriteFailed.Offline> { clients.saveClient(client(withContact = true)) }

            assertNull(
                clients.getClient(ClientId(CLIENT)),
                "a save that did not reach the server must not look like one that did",
            )
        }

    /**
     * The children too, which is the part a partial write would get wrong. A contact left
     * behind by a failed save is a person in the address book who belongs to nobody.
     */
    @Test
    fun `and neither are its contacts`() =
        runTest {
            val provider = testDatabaseProvider()
            val clients = repository(provider, RecordingTransport(failing = true))

            assertFailsWith<WriteFailed.Offline> { clients.saveClient(client(withContact = true)) }

            val contacts =
                provider
                    .database()
                    .contactQueries
                    .selectAll(STUDIO.value)
                    .awaitAsList()
            assertTrue(contacts.isEmpty(), "the contact was written for a client that does not exist")
        }

    @Test
    fun `a delete carries the client and every link it is retiring`() =
        runTest {
            val transport = RecordingTransport()
            val clients = repository(transport = transport)
            clients.saveClient(client(withContact = true))
            transport.pushed.clear()

            clients.deleteClient(ClientId(CLIENT))

            val sent = transport.pushed.single()
            assertTrue(
                sent.clients
                    .single()
                    .audit.isDeleted,
                "a delete is the row carrying a tombstone",
            )
            assertTrue(
                sent.clientContactLinks
                    .single()
                    .audit.isDeleted,
                "a peer learns a link is gone from the link's own tombstone, so it has to be sent",
            )
            assertNull(clients.getClient(ClientId(CLIENT)))
        }

    @Test
    fun `a delete that could not be sent leaves the client where it was`() =
        runTest {
            val transport = RecordingTransport()
            val clients = repository(transport = transport)
            clients.saveClient(client(withContact = true))
            transport.failing = true

            assertFailsWith<WriteFailed.Offline> { clients.deleteClient(ClientId(CLIENT)) }

            assertNotNull(clients.getClient(ClientId(CLIENT)), "the row a studio asked to delete is still there")
        }

    /**
     * The property `planContacts` exists to preserve, carried over from the version that
     * decided and wrote in one pass. Re-sending an unedited person on every save would bump
     * their version for nothing, and under the old design that was how two devices came to
     * disagree about a row neither had touched.
     */
    @Test
    fun `saving twice does not send a person who did not change`() =
        runTest {
            val transport = RecordingTransport()
            val clients = repository(transport = transport)
            val client = client(withContact = true)
            clients.saveClient(client)
            transport.pushed.clear()

            clients.saveClient(client)

            assertTrue(
                transport.pushed
                    .single()
                    .contacts
                    .isEmpty(),
                "the contact was unchanged, so nothing about them needed to travel",
            )
        }

    @Test
    fun `writing a client queues nothing for later`() =
        runTest {
            val provider = testDatabaseProvider()
            val clients = repository(provider, RecordingTransport())

            clients.saveClient(client(withContact = true))

            val queued =
                provider
                    .database()
                    .outboxQueries
                    .selectPendingIdentities(STUDIO.value)
                    .awaitAsList()
                    .map { it.entity_table }

            assertEquals(
                emptyList(),
                queued.filter {
                    it == SyncTables.CLIENT || it == SyncTables.CONTACT || it == SyncTables.CLIENT_CONTACT
                },
                "no outbox means no second write path, and no version this device invented",
            )
        }

    // -- Fixtures ------------------------------------------------------------------------------

    private fun repository(
        provider: DatabaseProvider = testDatabaseProvider(),
        transport: SyncTransport,
    ) = SqlDelightClientRepository(
        provider,
        LocalStudioContext(),
        AppClock { NOW },
        Dispatchers.Unconfined,
        RemoteWriter(transport),
    )

    private fun client(withContact: Boolean) =
        Client(
            id = ClientId(CLIENT),
            studioId = STUDIO,
            accountName = "Okafor",
            accountType = ClientAccountType.Individual,
            contacts =
                if (!withContact) {
                    emptyList()
                } else {
                    listOf(
                        ClientContact(
                            contact =
                                Contact(
                                    id = ContactId("contact-1"),
                                    studioId = STUDIO,
                                    firstName = "Ada",
                                    lastName = "Okafor",
                                    audit = AuditMetadata.createdAt(NOW),
                                ),
                            role = ClientContactRole.Primary,
                        ),
                    )
                },
            audit = AuditMetadata.createdAt(NOW),
        )

    private class RecordingTransport(
        var failing: Boolean = false,
    ) : SyncTransport {
        val pushed = mutableListOf<SyncPushRequest>()

        override suspend fun pull(
            since: Long,
            limit: Int,
        ) = SyncPullResponse(cursor = since, hasMore = false)

        override suspend fun push(changes: SyncPushRequest): List<SyncPushResult> {
            if (failing) throw IllegalStateException("no connection")
            pushed += changes
            return changes.clients.map {
                SyncPushResult(SyncTables.CLIENT, it.id.value, SyncPushOutcome.Applied, 1)
            }
        }
    }

    private companion object {
        val STUDIO = LocalStudioContext.LOCAL_STUDIO_ID
        val NOW: Instant = Instant.fromEpochMilliseconds(1_786_090_014_135)
        const val CLIENT = "client-1"
    }
}
