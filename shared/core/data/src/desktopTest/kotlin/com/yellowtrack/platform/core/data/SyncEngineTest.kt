package com.yellowtrack.platform.core.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.InvoiceRepository
import com.yellowtrack.platform.core.data.internal.OutboxOperation
import com.yellowtrack.platform.core.data.internal.SqlDelightClientRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightGearRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightInvoiceRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightProjectRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightSessionRepository
import com.yellowtrack.platform.core.data.internal.SyncTables
import com.yellowtrack.platform.core.data.internal.enqueueForSync
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.data.sync.SyncEngine
import com.yellowtrack.platform.core.data.sync.applyClient
import com.yellowtrack.platform.core.data.sync.applyCodbProfile
import com.yellowtrack.platform.core.data.sync.applyContract
import com.yellowtrack.platform.core.data.sync.applyCrewMember
import com.yellowtrack.platform.core.data.sync.applyDeliverable
import com.yellowtrack.platform.core.data.sync.applyExpense
import com.yellowtrack.platform.core.data.sync.applyGearItem
import com.yellowtrack.platform.core.data.sync.applyInvoice
import com.yellowtrack.platform.core.data.sync.applyLead
import com.yellowtrack.platform.core.data.sync.applyLightingRecipe
import com.yellowtrack.platform.core.data.sync.applyMediaCopy
import com.yellowtrack.platform.core.data.sync.applyMileage
import com.yellowtrack.platform.core.data.sync.applyPackingEntry
import com.yellowtrack.platform.core.data.sync.applyPayment
import com.yellowtrack.platform.core.data.sync.applyPostTask
import com.yellowtrack.platform.core.data.sync.applyProject
import com.yellowtrack.platform.core.data.sync.applyQuote
import com.yellowtrack.platform.core.data.sync.applyServiceTemplate
import com.yellowtrack.platform.core.data.sync.applySession
import com.yellowtrack.platform.core.data.sync.applyShot
import com.yellowtrack.platform.core.data.sync.applyStorageVolume
import com.yellowtrack.platform.core.data.sync.applyStudioProfile
import com.yellowtrack.platform.core.data.sync.applyTalentRelease
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactLink
import com.yellowtrack.platform.core.model.client.ClientContactLinkId
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.codb.CodbProfile
import com.yellowtrack.platform.core.model.codb.CodbProfileId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.ContractStatus
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.crew.CrewRole
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.delivery.DeliverableId
import com.yellowtrack.platform.core.model.expense.DistanceUnit
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.ExpenseCategory
import com.yellowtrack.platform.core.model.expense.ExpenseId
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.expense.MileageId
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
import com.yellowtrack.platform.core.model.gear.PackingEntry
import com.yellowtrack.platform.core.model.gear.PackingEntryId
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.invoice.PaymentMethod
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.core.model.lead.LeadSource
import com.yellowtrack.platform.core.model.lead.LeadStatus
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import com.yellowtrack.platform.core.model.release.TalentRelease
import com.yellowtrack.platform.core.model.release.TalentReleaseId
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.model.shot.ShotId
import com.yellowtrack.platform.core.model.studio.StudioProfile
import com.yellowtrack.platform.core.model.studio.StudioProfileId
import com.yellowtrack.platform.core.model.sync.SyncConflict
import com.yellowtrack.platform.core.model.sync.SyncConflictId
import com.yellowtrack.platform.core.model.sync.SyncPullResponse
import com.yellowtrack.platform.core.model.sync.SyncPushOutcome
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import com.yellowtrack.platform.core.model.sync.SyncPushResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun `saving a queued entity notes that it needs uploading`() =
        runTest {
            val world = world()

            world.gear.saveGearItem(gearItem("g1"))

            val pending = world.outbox()
            assertEquals(1, pending.size)
            assertEquals(SyncTables.GEAR_ITEM to "g1", pending.single())
        }

    @Test
    fun `deleting one queues the tombstone, rather than nothing`() =
        runTest {
            val world = world()
            world.gear.saveGearItem(gearItem("g1"))
            world.drainQuietly()

            world.gear.deleteGearItem(GearItemId("g1"))

            assertEquals(
                listOf(SyncTables.GEAR_ITEM to "g1"),
                world.outbox(),
                "a delete that never uploads is a row that comes back from the dead on the " +
                    "other device",
            )
        }

    @Test
    fun `the queued payload stays empty, because the row is re-read at upload time`() =
        runTest {
            val world = world()
            world.gear.saveGearItem(gearItem("g1"))

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
    fun `a saved row is uploaded and the entry is cleared`() =
        runTest {
            val world = world()
            world.gear.saveGearItem(gearItem("g1"))

            val report = world.engine.sync()

            assertEquals(1, report.uploaded)
            assertEquals(
                listOf("35mm"),
                world.transport.pushed
                    .single()
                    .gearItems
                    .map { it.name },
            )
            assertTrue(world.outbox().isEmpty(), "an entry that survives its upload is uploaded again forever")
        }

    @Test
    fun `three edits to one booking upload once, not three times`() =
        runTest {
            val world = world()
            val original = gearItem("g1")
            world.gear.saveGearItem(original)
            world.gear.saveGearItem(original.copy(name = "35mm f1.4"))
            world.gear.saveGearItem(original.copy(name = "35mm f1.4 Mk II"))

            assertEquals(3, world.outbox().size, "each edit queues an entry")

            world.engine.sync()

            assertEquals(
                listOf("35mm f1.4 Mk II"),
                world.transport.pushed
                    .single()
                    .gearItems
                    .map { it.name },
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
                        changes.gearItems.map {
                            SyncPushResult(SyncTables.GEAR_ITEM, it.id.value, SyncPushOutcome.Conflicted, 7)
                        }
                    },
                )
            world.gear.saveGearItem(gearItem("g1"))

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
                        changes.gearItems.map {
                            SyncPushResult(
                                SyncTables.GEAR_ITEM,
                                it.id.value,
                                SyncPushOutcome.Rejected,
                                1,
                                "that row belongs to another studio",
                            )
                        }
                    },
                )
            world.gear.saveGearItem(gearItem("g1"))

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
            world.gear.saveGearItem(gearItem("g1"))
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
                SyncPullResponse(
                    cursor = 12,
                    hasMore = false,
                    clients = listOf(client("remote-1", "Harbourline Coffee")),
                )

            val report = world.engine.sync()

            assertEquals(1, report.downloaded)
            assertEquals("Harbourline Coffee", world.clients.getClient(ClientId("remote-1"))?.accountName)
        }

    @Test
    fun `applying what arrived does not queue it straight back for upload`() =
        runTest {
            val world = world()
            world.transport.pages +=
                SyncPullResponse(
                    cursor = 12,
                    hasMore = false,
                    clients = listOf(client("remote-1", "Harbourline Coffee")),
                )

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
                SyncPullResponse(
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
            world.transport.pages += SyncPullResponse(cursor = 42, hasMore = false)

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
                SyncPullResponse(cursor = 10, hasMore = true, clients = listOf(client("a", "One")))
            world.transport.pages +=
                SyncPullResponse(cursor = 20, hasMore = true, clients = listOf(client("b", "Two")))
            world.transport.pages +=
                SyncPullResponse(cursor = 30, hasMore = false, clients = listOf(client("c", "Three")))

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
                SyncPullResponse(
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

            world.transport.pages += SyncPullResponse(cursor = 5, hasMore = false, conflicts = listOf(conflict))
            world.engine.sync()

            world.database.syncQueries.markConflictResolved(NOW.toEpochMilliseconds(), "conflict-1")

            world.transport.pages += SyncPullResponse(cursor = 6, hasMore = false, conflicts = listOf(conflict))
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
            world.gear.saveGearItem(gearItem("g1"))
            world.transport.pages +=
                SyncPullResponse(
                    cursor = 3,
                    hasMore = false,
                    gearItems = listOf(gearItem("g1").copy(name = "Theirs")),
                )

            world.engine.sync()

            assertEquals(
                listOf("35mm"),
                world.transport.pushed
                    .single()
                    .gearItems
                    .map { it.name },
                "pulling first would overwrite this device's version and then upload the server's " +
                    "own row back to it — the studio's work gone before anything noticed it was " +
                    "in danger",
            )
            assertEquals("Theirs", world.gear.getGearItem(GearItemId("g1"))?.name)
        }

    // -- Plumbing --------------------------------------------------------------------------

    private class World(
        val database: com.yellowtrack.platform.core.database.YellowTrackDatabase,
        val engine: SyncEngine,
        val transport: FakeSyncTransport,
        val clients: ClientRepository,
        /**
         * What the client repository sent, which is no longer what the engine sent.
         *
         * Clients write through the server directly under ADR 0012, so "did this leave the
         * device" is answered here rather than by [transport]. The question is the same one;
         * only the road changed.
         */
        val clientWrites: FakeSyncTransport,
        val gear: GearRepository,
        val invoices: InvoiceRepository,
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

    /**
     * Deletes did not travel at all until this was written.
     *
     * The engine re-read each queued row through the repositories, every one of which filters
     * `deleted_at IS NULL`. A deleted row came back null, was taken for one that had never
     * existed, and its outbox entry was dropped. The probe that found it reported
     * `pushes=0 clientsSent=0`: a client deleted on one device stayed on every other one
     * indefinitely, and nothing anywhere said so.
     */
    @Test
    fun `a deleted client is uploaded as a tombstone`() =
        runTest {
            val world = world()
            world.gear.saveGearItem(gearItem("probe-1"))
            world.engine.sync()
            world.transport.pushed.clear()

            world.gear.deleteGearItem(GearItemId("probe-1"))
            world.engine.sync()

            val sent = world.transport.pushed.flatMap { it.gearItems }
            assertTrue(
                sent.any { it.id.value == "probe-1" && it.audit.deletedAt != null },
                "a delete queued to the outbox never reached the server",
            )
        }

    /**
     * A contact added on one device reaches another — which nothing proved until now.
     *
     * Contacts were not in the synchronised slice at all: a client arrived on a second device
     * with an empty contact list and no indication anything was missing. This drives device A
     * through a real save and push, hands what it uploaded to device B as a pull, and asks B
     * whether it can see the person.
     */
    @Test
    fun `a contact added on one device arrives attached on another`() =
        runTest {
            val deviceA = world()
            val deviceB = world()

            val ada =
                Contact(
                    id = ContactId("contact-1"),
                    studioId = STUDIO,
                    firstName = "Ada",
                    lastName = "Okafor",
                    audit = AuditMetadata.createdAt(NOW),
                )

            deviceA.clients.saveClient(
                client("client-1", "Okafor").copy(
                    contacts = listOf(ClientContact(contact = ada, role = ClientContactRole.Primary)),
                ),
            )
            // No engine.sync() on this side any more: the save itself is the send.
            val uploaded = deviceA.clientWrites.pushed.single()
            assertTrue(uploaded.contacts.isNotEmpty(), "the contact itself never left the device")
            assertTrue(uploaded.clientContactLinks.isNotEmpty(), "the attachment never left the device")

            // What the server would hand back, in the order it hands it back.
            deviceB.transport.pages =
                mutableListOf(
                    SyncPullResponse(
                        cursor = 10,
                        hasMore = false,
                        clients = uploaded.clients,
                        contacts = uploaded.contacts,
                        clientContactLinks = uploaded.clientContactLinks,
                    ),
                )

            deviceB.engine.sync()

            val arrived = deviceB.clients.getClient(ClientId("client-1"))
            assertNotNull(arrived, "the client did not arrive at all")
            assertEquals(
                listOf("Ada"),
                arrived.contacts.map { it.contact.firstName },
                "the client arrived without the person attached to it, which is what a studio " +
                    "would notice as a client with no way to contact them",
            )
        }

    /**
     * Two devices each attach a different person to the same client, and both survive.
     *
     * This is ADR 0008 decision 5. Had contacts travelled inside the client, the later save
     * would have carried a contact list missing the other device's addition and silently
     * discarded it — a lost planner on a wedding, found the week of the shoot.
     */
    @Test
    fun `two devices each adding a contact keep both`() =
        runTest {
            val deviceB = world()

            val ada =
                Contact(
                    id = ContactId("contact-1"),
                    studioId = STUDIO,
                    firstName = "Ada",
                    lastName = "Okafor",
                    audit = AuditMetadata.createdAt(NOW),
                )
            val planner =
                Contact(
                    id = ContactId("contact-2"),
                    studioId = STUDIO,
                    firstName = "Rosa",
                    lastName = "Iyer",
                    audit = AuditMetadata.createdAt(NOW),
                )

            // B's own work, done offline.
            deviceB.clients.saveClient(
                client("client-1", "Okafor").copy(
                    contacts = listOf(ClientContact(contact = ada, role = ClientContactRole.Primary)),
                ),
            )

            // A's work, arriving. A separate link row, because it has its own id.
            deviceB.transport.pages =
                mutableListOf(
                    SyncPullResponse(
                        cursor = 20,
                        hasMore = false,
                        contacts = listOf(planner),
                        clientContactLinks =
                            listOf(
                                ClientContactLink(
                                    id = ClientContactLinkId("link-from-a"),
                                    studioId = STUDIO,
                                    clientId = ClientId("client-1"),
                                    contactId = ContactId("contact-2"),
                                    role = ClientContactRole.Planner,
                                    audit = AuditMetadata.createdAt(NOW),
                                ),
                            ),
                    ),
                )

            deviceB.engine.sync()

            val names =
                deviceB.clients
                    .getClient(ClientId("client-1"))
                    ?.contacts
                    ?.map { it.contact.firstName }
                    ?.sorted()

            assertEquals(
                listOf("Ada", "Rosa"),
                names,
                "one device's contact displaced the other's, which is exactly what giving each " +
                    "link its own id is supposed to prevent",
            )
        }

    /**
     * Two devices each record a payment against one invoice, and both survive.
     *
     * This is the case ADR 0008 decision 5 was written for, and the asymmetry it turns on:
     * a lost invoice line is retyped from the quote in seconds, a lost payment is discovered
     * during a tax return, if at all. Had payments travelled inside the invoice, the second
     * device's copy would have carried a payment list missing the first's and discarded it.
     */
    @Test
    fun `two devices each recording a payment keep both`() =
        runTest {
            val device = world()

            // The chain a payment hangs off, arriving from the server.
            device.transport.pages =
                mutableListOf(
                    SyncPullResponse(
                        cursor = 5,
                        hasMore = false,
                        clients = listOf(client("client-1", "Okafor")),
                        projects = listOf(project("project-1", "client-1")),
                        invoices = listOf(invoice("invoice-1", "project-1")),
                    ),
                )
            device.engine.sync()

            // This device's own payment.
            device.invoices.recordPayment(payment("payment-local", "invoice-1", 90_000))

            // The other device's, arriving. A separate row, with its own id.
            device.transport.pages =
                mutableListOf(
                    SyncPullResponse(
                        cursor = 9,
                        hasMore = false,
                        payments = listOf(payment("payment-remote", "invoice-1", 60_000)),
                    ),
                )
            device.engine.sync()

            val amounts =
                device.invoices
                    .getInvoice(InvoiceId("invoice-1"))
                    ?.payments
                    ?.map { it.amount.minorUnits }
                    ?.sorted()

            assertEquals(
                listOf(60_000L, 90_000L),
                amounts,
                "one device's payment displaced the other's — the failure decision 5 exists to " +
                    "prevent, and the one a studio finds at a tax return rather than on the day",
            )
        }

    /**
     * A child can arrive a page before its parent, and the apply order cannot help.
     *
     * `SyncedEntity.all` orders parents before children *within* a page. Across pages there
     * is nothing to order: the server pages by `server_seq`, and an edit bumps it, so a
     * session created before its crew but edited afterwards sorts *after* its own crew
     * member. A device syncing from scratch then receives the child first.
     *
     * The server therefore closes each page over its parents — see `a page carries the
     * parents of everything in it` — and this pins the contract from the other side: a page
     * that does not is one the device cannot apply, and cannot ever apply, because the
     * cursor only advances once a page has been written.
     */
    @Test
    fun `a page missing a parent cannot be applied, which is why the server sends them`() =
        runTest {
            val device = world()

            device.transport.pages =
                mutableListOf(
                    // Page one: the crew member, whose session has been edited since and so
                    // carries a higher server_seq.
                    SyncPullResponse(
                        cursor = 11,
                        hasMore = true,
                        crewMembers = listOf(crewMember("crew-1", "session-1")),
                    ),
                    // Page two: everything it hangs off.
                    SyncPullResponse(
                        cursor = 50,
                        hasMore = false,
                        clients = listOf(client("client-1", "Okafor")),
                        projects = listOf(project("project-1", "client-1")),
                        sessions = listOf(session("session-1", "project-1")),
                    ),
                )

            val failure = runCatching { device.engine.sync() }.exceptionOrNull()

            assertNotNull(failure, "a page whose parent is absent should not apply quietly")
            assertTrue(
                failure.message.orEmpty().contains("FOREIGN KEY", ignoreCase = true),
                "expected a foreign key failure, got: ${failure.message}",
            )
        }

    /**
     * Every table the outbox can carry is actually read when the outbox is drained.
     *
     * Four entities shipped without this: invoices, payments, crew and deliverables were
     * declared, wired into the apply loop and into `identities`, and never read into the
     * push request. A queued row was therefore counted as one that no longer existed, and
     * its outbox entry was deleted with nothing sent. **They had never uploaded.**
     *
     * The two lists disagreeing is the failure, so this asserts against the outcome instead:
     * queue one row of every kind, sync, and something must have been sent for each.
     */
    @Test
    fun `every table the outbox carries is uploaded`() =
        runTest {
            val world = world()
            val db = world.database
            val studio = STUDIO.value

            // Written straight to the tables, in dependency order, so this test says nothing
            // about the repositories — only about what the engine reads back.
            db.applyClient(client("c1", "Okafor"))
            db.applyProject(project("p1", "c1"))
            db.applySession(session("s1", "p1"))
            db.applyInvoice(invoice("i1", "p1"))
            db.applyPayment(payment("pay1", "i1", 1_000))
            db.applyCrewMember(crewMember("cr1", "s1"))
            db.applyDeliverable(deliverable("d1", "p1"))
            db.applyGearItem(gearItem("g1"))
            db.applyPackingEntry(packingEntry("pk1", "s1", "g1"))
            db.applyStorageVolume(storageVolume("v1"))
            db.applyMediaCopy(mediaCopy("m1", "s1", "v1"))
            db.applyLead(lead("l1"))
            db.applyExpense(expense("e1"))
            db.applyMileage(mileage("mi1"))
            db.applyQuote(quote("q1", "p1"))
            db.applyContract(contract("co1", "p1"))
            db.applyShot(shot("sh1", "s1"))
            db.applyPostTask(postTask("pt1", "p1"))
            db.applyTalentRelease(talentRelease("tr1", "s1"))
            db.applyLightingRecipe(lightingRecipe("lr1"))
            db.applyStudioProfile(studioProfile())
            db.applyCodbProfile(codbProfile())
            db.applyServiceTemplate(serviceTemplate())

            val queued =
                listOf(
                    SyncTables.CLIENT to "c1",
                    SyncTables.PROJECT to "p1",
                    SyncTables.SESSION to "s1",
                    SyncTables.INVOICE to "i1",
                    SyncTables.PAYMENT to "pay1",
                    SyncTables.CREW_MEMBER to "cr1",
                    SyncTables.DELIVERABLE to "d1",
                    SyncTables.GEAR_ITEM to "g1",
                    SyncTables.PACKING_ENTRY to "pk1",
                    SyncTables.STORAGE_VOLUME to "v1",
                    SyncTables.MEDIA_COPY to "m1",
                    SyncTables.LEAD to "l1",
                    SyncTables.EXPENSE to "e1",
                    SyncTables.MILEAGE to "mi1",
                    SyncTables.QUOTE to "q1",
                    SyncTables.CONTRACT to "co1",
                    SyncTables.SHOT to "sh1",
                    SyncTables.POST_TASK to "pt1",
                    SyncTables.TALENT_RELEASE to "tr1",
                    SyncTables.LIGHTING_RECIPE to "lr1",
                    SyncTables.STUDIO_PROFILE to STUDIO.value,
                    SyncTables.CODB_PROFILE to STUDIO.value,
                    SyncTables.SERVICE_TEMPLATE to "st1",
                )

            queued.forEach { (table, id) ->
                db.enqueueForSync(studio, table, id, OutboxOperation.Upsert, NOW.toEpochMilliseconds())
            }

            world.engine.sync()

            val sent = world.transport.pushed.single()
            val missing =
                buildList {
                    if (sent.clients.isEmpty()) add(SyncTables.CLIENT)
                    if (sent.projects.isEmpty()) add(SyncTables.PROJECT)
                    if (sent.sessions.isEmpty()) add(SyncTables.SESSION)
                    if (sent.invoices.isEmpty()) add(SyncTables.INVOICE)
                    if (sent.payments.isEmpty()) add(SyncTables.PAYMENT)
                    if (sent.crewMembers.isEmpty()) add(SyncTables.CREW_MEMBER)
                    if (sent.deliverables.isEmpty()) add(SyncTables.DELIVERABLE)
                    if (sent.gearItems.isEmpty()) add(SyncTables.GEAR_ITEM)
                    if (sent.packingEntries.isEmpty()) add(SyncTables.PACKING_ENTRY)
                    if (sent.storageVolumes.isEmpty()) add(SyncTables.STORAGE_VOLUME)
                    if (sent.mediaCopies.isEmpty()) add(SyncTables.MEDIA_COPY)
                    if (sent.leads.isEmpty()) add(SyncTables.LEAD)
                    if (sent.expenses.isEmpty()) add(SyncTables.EXPENSE)
                    if (sent.mileages.isEmpty()) add(SyncTables.MILEAGE)
                    if (sent.quotes.isEmpty()) add(SyncTables.QUOTE)
                    if (sent.contracts.isEmpty()) add(SyncTables.CONTRACT)
                    if (sent.shots.isEmpty()) add(SyncTables.SHOT)
                    if (sent.postTasks.isEmpty()) add(SyncTables.POST_TASK)
                    if (sent.talentReleases.isEmpty()) add(SyncTables.TALENT_RELEASE)
                    if (sent.lightingRecipes.isEmpty()) add(SyncTables.LIGHTING_RECIPE)
                    if (sent.studioProfiles.isEmpty()) add(SyncTables.STUDIO_PROFILE)
                    if (sent.codbProfiles.isEmpty()) add(SyncTables.CODB_PROFILE)
                    if (sent.serviceTemplates.isEmpty()) add(SyncTables.SERVICE_TEMPLATE)
                }

            assertEquals(
                emptyList(),
                missing,
                "these were queued and never sent. The outbox entry is then dropped as a row that " +
                    "no longer exists, so the studio's work is discarded and the sync reports success",
            )
        }

    /**
     * A server older than this build is noticed, rather than trusted.
     *
     * `ApiJson` ignores unknown keys, which is right for forwards compatibility and means a
     * server that has never heard of an entity discards it from a push and answers
     * successfully. Every screen then says the sync worked while a whole category of the
     * studio's work stays on one device. That is what happened with studio profiles, and it
     * took an afternoon to find because nothing anywhere said a word.
     */
    @Test
    fun `a server that reconciles less than this build is reported`() =
        runTest {
            val device = world()

            device.transport.pages =
                mutableListOf(
                    SyncPullResponse(
                        cursor = 4,
                        hasMore = false,
                        // An older server: everything except the three that landed last.
                        reconciles =
                            (
                                SyncTables.all -
                                    setOf(
                                        SyncTables.STUDIO_PROFILE,
                                        SyncTables.CODB_PROFILE,
                                        SyncTables.SERVICE_TEMPLATE,
                                    )
                            ).toList(),
                    ),
                )

            val report = device.engine.sync()

            assertEquals(
                setOf(SyncTables.STUDIO_PROFILE, SyncTables.CODB_PROFILE, SyncTables.SERVICE_TEMPLATE),
                report.notReconciledByServer,
                "the studio should be told which kinds of record are going nowhere",
            )
        }

    @Test
    fun `a server as new as this build reports no gap`() =
        runTest {
            val device = world()

            device.transport.pages =
                mutableListOf(SyncPullResponse(cursor = 4, hasMore = false, reconciles = SyncTables.all.toList()))

            val report = device.engine.sync()

            assertEquals(emptySet(), report.notReconciledByServer)
        }

    /**
     * A server predating the field at all, which is the case that actually bit.
     *
     * It cannot fill `reconciles`, so the list arrives empty — and empty has to read as
     * "knows nothing of these" rather than as "no gap", or the check would be blind to
     * exactly the servers it exists to catch.
     */
    @Test
    fun `a server too old to say anything is treated as knowing nothing`() =
        runTest {
            val device = world()

            device.transport.pages = mutableListOf(SyncPullResponse(cursor = 4, hasMore = false))

            val report = device.engine.sync()

            assertEquals(SyncTables.all, report.notReconciledByServer)
        }

    // -- A delete that will not stay deleted -------------------------------------------------

    /**
     * The fault a studio actually reported: an enquiry deleted, the row gone, and back again
     * after a reload.
     *
     * A pulled row was written straight over the local one, with nothing consulting the
     * outbox. So a push the server did not acknowledge left it still holding the row alive,
     * and the pull that follows in the same run put it back — and the outbox entry, which
     * survives an unacknowledged push, then re-uploaded the resurrection as an upsert.
     *
     * The server answering with silence rather than throwing is the case that reaches this:
     * a throw abandons the run before the pull, which is why this is not the more obvious
     * dropped-connection test.
     */
    @Test
    fun `a delete survives a push the server never acknowledged`() =
        runTest {
            val world = world(onPush = { emptyList() })
            world.gear.saveGearItem(gearItem("g1"))
            world.engine.sync()

            world.gear.deleteGearItem(GearItemId("g1"))

            // The server answers, and says nothing about this row — so it still believes it
            // is alive when the pull in the same run asks.
            world.transport.pages =
                mutableListOf(
                    SyncPullResponse(
                        cursor = 20,
                        hasMore = false,
                        gearItems = listOf(gearItem("g1")),
                    ),
                )

            world.engine.sync()

            assertNull(
                world.gear.getGearItem(GearItemId("g1")),
                "the studio deleted this and the server had not been told yet, so a pull must " +
                    "not put it back",
            )
        }

    /**
     * The same rule, for an edit rather than a delete.
     *
     * A delete is the visible case — the row comes back — but nothing about the fault was
     * specific to deletion. An unsent rename was overwritten by whatever the server still had.
     */
    @Test
    fun `an unsent edit is not overwritten by what the server still has`() =
        runTest {
            val world = world(onPush = { emptyList() })
            world.gear.saveGearItem(gearItem("g1"))
            world.engine.sync()

            world.gear.saveGearItem(gearItem("g1").copy(name = "35mm f1.4"))

            world.transport.pages =
                mutableListOf(
                    SyncPullResponse(
                        cursor = 20,
                        hasMore = false,
                        gearItems = listOf(gearItem("g1")),
                    ),
                )

            world.engine.sync()

            assertEquals(
                "35mm f1.4",
                world.gear.getGearItem(GearItemId("g1"))?.name,
                "the rename had not reached the server, so the server's older copy must not win",
            )
        }

    private suspend fun world(onPush: ((SyncPushRequest) -> List<SyncPushResult>)? = null): World {
        // One provider shared by everything, so the engine and the repositories are looking
        // at the same database rather than at three of them.
        val provider = testDatabaseProvider()
        val studioContext = LocalStudioContext()
        val clock = AppClock { NOW }
        val transport = if (onPush == null) FakeSyncTransport() else FakeSyncTransport(onPush)

        val clientWrites = FakeSyncTransport()
        val clients =
            SqlDelightClientRepository(
                provider,
                studioContext,
                clock,
                Dispatchers.Unconfined,
                RemoteWriter(clientWrites),
            )

        // The vehicle for every test about the outbox itself. Gear is a shoot-day surface,
        // so it keeps the outbox under ADR 0012 decision 3 and will still be queuing long
        // after clients have stopped — which is the point: those tests are about the queue,
        // not about clients, and were only ever written on clients because clients came
        // first.
        val gear = SqlDelightGearRepository(provider, studioContext, clock, Dispatchers.Unconfined)
        val projects =
            SqlDelightProjectRepository(
                provider,
                studioContext,
                clock,
                Dispatchers.Unconfined,
                RemoteWriter(AcceptingTransport),
            )
        val sessions =
            SqlDelightSessionRepository(
                provider,
                studioContext,
                clock,
                Dispatchers.Unconfined,
                RemoteWriter(AcceptingTransport),
            )

        return World(
            database = provider.database(),
            engine = SyncEngine(provider, studioContext, transport, clients, projects, sessions, clock),
            transport = transport,
            clients = clients,
            clientWrites = clientWrites,
            gear = gear,
            invoices =
                SqlDelightInvoiceRepository(
                    provider,
                    studioContext,
                    clock,
                    Dispatchers.Unconfined,
                    RemoteWriter(AcceptingTransport),
                ),
        )
    }

    private fun project(
        id: String,
        clientId: String,
    ) = Project(
        id = ProjectId(id),
        studioId = STUDIO,
        clientId = ClientId(clientId),
        name = "Okafor — Wedding",
        serviceLine = ServiceLine.Wedding,
        status = ProjectStatus.Booked,
        audit = AuditMetadata.createdAt(NOW),
    )

    /** Keyed by the studio, which is what makes one row of it rather than two. */
    private fun studioProfile() =
        StudioProfile(
            id = StudioProfileId(STUDIO.value),
            studioId = STUDIO,
            name = "Harbourline Photography",
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun codbProfile() =
        CodbProfile(
            id = CodbProfileId(STUDIO.value),
            studioId = STUDIO,
            currency = CurrencyCode.GBP,
            targetAnnualSalary = Money(minorUnits = 4_000_000, currency = CurrencyCode.GBP),
            billableDaysPerYear = 120,
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun serviceTemplate() =
        ServiceTemplate(
            id = ServiceTemplateId("st1"),
            studioId = STUDIO,
            name = "Wedding — Full Day",
            serviceLine = ServiceLine.Wedding,
            defaultSessionDurationMinutes = 600,
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun shot(
        id: String,
        sessionId: String,
    ) = Shot(
        id = ShotId(id),
        studioId = STUDIO,
        sessionId = SessionId(sessionId),
        description = "Rings on the windowsill",
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun postTask(
        id: String,
        projectId: String,
    ) = PostProductionTask(
        id = PostProductionTaskId(id),
        studioId = STUDIO,
        projectId = ProjectId(projectId),
        name = "Cull",
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun talentRelease(
        id: String,
        sessionId: String,
    ) = TalentRelease(
        id = TalentReleaseId(id),
        studioId = STUDIO,
        sessionId = SessionId(sessionId),
        personName = "Rosa Iyer",
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun lightingRecipe(id: String) =
        LightingRecipe(
            id = LightingRecipeId(id),
            studioId = STUDIO,
            name = "Two-light clamshell",
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun quote(
        id: String,
        projectId: String,
    ) = Quote(
        id = QuoteId(id),
        studioId = STUDIO,
        projectId = ProjectId(projectId),
        number = "Q-2026-004",
        status = QuoteStatus.Sent,
        currency = CurrencyCode.GBP,
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun contract(
        id: String,
        projectId: String,
    ) = Contract(
        id = ContractId(id),
        studioId = STUDIO,
        projectId = ProjectId(projectId),
        title = "Wedding coverage agreement",
        status = ContractStatus.Sent,
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun lead(id: String) =
        Lead(
            id = LeadId(id),
            studioId = STUDIO,
            name = "Ada Okafor",
            source = LeadSource.ClientReferral,
            status = LeadStatus.New,
            receivedAt = NOW,
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun expense(id: String) =
        Expense(
            id = ExpenseId(id),
            studioId = STUDIO,
            category = ExpenseCategory.Other,
            description = "Parking at the venue",
            amount = Money(minorUnits = 1_200, currency = CurrencyCode.GBP),
            incurredOn = LocalDate.parse("2026-08-01"),
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun mileage(id: String) =
        Mileage(
            id = MileageId(id),
            studioId = STUDIO,
            travelledOn = LocalDate.parse("2026-08-01"),
            distance = 42.0,
            unit = DistanceUnit.Miles,
            ratePerUnit = Money(minorUnits = 45, currency = CurrencyCode.GBP),
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun deliverable(
        id: String,
        projectId: String,
    ) = Deliverable(
        id = DeliverableId(id),
        studioId = STUDIO,
        projectId = ProjectId(projectId),
        name = "Full gallery",
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun gearItem(id: String) =
        GearItem(
            id = GearItemId(id),
            studioId = STUDIO,
            name = "35mm",
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun packingEntry(
        id: String,
        sessionId: String,
        gearItemId: String,
    ) = PackingEntry(
        id = PackingEntryId(id),
        studioId = STUDIO,
        sessionId = SessionId(sessionId),
        gearItemId = GearItemId(gearItemId),
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun storageVolume(id: String) =
        StorageVolume(
            id = StorageVolumeId(id),
            studioId = STUDIO,
            label = "Shuttle 1",
            kind = StorageKind.CameraCard,
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun mediaCopy(
        id: String,
        sessionId: String,
        volumeId: String,
    ) = MediaCopy(
        id = MediaCopyId(id),
        studioId = STUDIO,
        sessionId = SessionId(sessionId),
        volumeId = StorageVolumeId(volumeId),
        volumeName = "Shuttle 1",
        kind = StorageKind.CameraCard,
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun session(
        id: String,
        projectId: String,
    ) = Session(
        id = SessionId(id),
        studioId = STUDIO,
        projectId = ProjectId(projectId),
        title = "Ceremony",
        kind = SessionKind.Shoot,
        status = SessionStatus.Scheduled,
        startsAt = NOW,
        endsAt = NOW,
        timeZoneId = "Europe/London",
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun crewMember(
        id: String,
        sessionId: String,
    ) = CrewMember(
        id = CrewMemberId(id),
        studioId = STUDIO,
        sessionId = SessionId(sessionId),
        name = "Rosa Iyer",
        role = CrewRole.SecondShooter,
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun invoice(
        id: String,
        projectId: String,
    ) = Invoice(
        id = InvoiceId(id),
        studioId = STUDIO,
        projectId = ProjectId(projectId),
        number = "2026-014",
        kind = InvoiceKind.Balance,
        status = InvoiceStatus.Sent,
        currency = CurrencyCode.GBP,
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun payment(
        id: String,
        invoiceId: String,
        minorUnits: Long,
    ) = Payment(
        id = PaymentId(id),
        studioId = STUDIO,
        invoiceId = InvoiceId(invoiceId),
        amount = Money(minorUnits = minorUnits, currency = CurrencyCode.GBP),
        paidAt = NOW,
        method = PaymentMethod.BankTransfer,
        audit = AuditMetadata.createdAt(NOW),
    )

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
