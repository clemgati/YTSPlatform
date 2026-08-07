package com.yellowtrack.platform.core.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.internal.SqlDelightClientRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightInvoiceRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightProjectRepository
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.data.sync.SyncTransport
import com.yellowtrack.platform.core.data.sync.WriteFailed
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.project.ProjectId
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
 * The ledger under ADR 0012: the server is asked first, and the local tables are a cache
 * written afterwards.
 *
 * The property worth holding is that a write which did not reach the server did not happen —
 * anywhere. The old design's failure was the opposite: it happened locally and then argued
 * about it later.
 */
class LedgerWritesOnlineTest {
    @Test
    fun `an invoice reaches the server before it reaches the cache`() =
        runTest {
            val transport = RecordingTransport()
            val invoices = repository(transport)

            invoices.saveInvoice(invoice())

            assertEquals(1, transport.pushed.size, "the server should have been asked")
            assertEquals(
                "INV-001",
                transport.pushed
                    .single()
                    .invoices
                    .single()
                    .number,
            )
            assertNotNull(invoices.getInvoice(InvoiceId("inv-1")), "and the cache should hold it")
        }

    /**
     * The whole of the decision in one test. A studio told now can write it down or wait; one
     * told nothing has an invoice that exists on its screen and nowhere else.
     */
    @Test
    fun `an invoice that could not be sent is not left in the cache`() =
        runTest {
            val transport = RecordingTransport(failing = true)
            val invoices = repository(transport)

            assertFailsWith<WriteFailed.Offline> { invoices.saveInvoice(invoice()) }

            assertNull(
                invoices.getInvoice(InvoiceId("inv-1")),
                "a save that did not reach the server must not look like one that did",
            )
        }

    @Test
    fun `a delete travels as a tombstone and only then leaves the cache`() =
        runTest {
            val transport = RecordingTransport()
            val invoices = repository(transport)
            invoices.saveInvoice(invoice())
            transport.pushed.clear()

            invoices.deleteInvoice(InvoiceId("inv-1"))

            val sent =
                transport.pushed
                    .single()
                    .invoices
                    .single()
            assertTrue(sent.audit.isDeleted, "a delete is the row carrying a tombstone, not an instruction")
            assertNull(invoices.getInvoice(InvoiceId("inv-1")))
        }

    @Test
    fun `a delete that could not be sent leaves the invoice where it was`() =
        runTest {
            val transport = RecordingTransport()
            val invoices = repository(transport)
            invoices.saveInvoice(invoice())
            transport.failing = true

            assertFailsWith<WriteFailed.Offline> { invoices.deleteInvoice(InvoiceId("inv-1")) }

            assertNotNull(
                invoices.getInvoice(InvoiceId("inv-1")),
                "the row a studio asked to delete is still there, which is the truth",
            )
        }

    /** Nothing is queued any more, because nothing is held. */
    @Test
    fun `writing the ledger queues nothing for later`() =
        runTest {
            val provider = testDatabaseProvider()
            val transport = RecordingTransport()
            val invoices = repository(transport, provider)

            invoices.saveInvoice(invoice())

            // Only the ledger's own rows. The client and project seeded above are still
            // offline-first and still queue, which is what a migration done entity by entity
            // looks like from inside.
            val queued =
                provider
                    .database()
                    .outboxQueries
                    .selectPendingIdentities(STUDIO.value)
                    .awaitAsList()
                    .map { it.entity_table }

            assertEquals(
                emptyList(),
                queued.filter { it == "invoice" || it == "payment" },
                "the ledger no longer holds work — a write either happened or did not",
            )
        }

    // -- Fixtures ------------------------------------------------------------------------------

    /**
     * An invoice references a project, which references a client, so both are seeded — through
     * their own repositories, which are still offline-first and unaffected by this step.
     */
    private suspend fun repository(
        transport: SyncTransport,
        provider: DatabaseProvider = testDatabaseProvider(),
    ): SqlDelightInvoiceRepository {
        val clock = AppClock { NOW }
        val clients = SqlDelightClientRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined)
        val projects =
            SqlDelightProjectRepository(
                provider,
                LocalStudioContext(),
                clock,
                Dispatchers.Unconfined,
                RemoteWriter(AcceptingTransport),
            )

        val client = Fixtures.client()
        clients.saveClient(client)
        projects.saveProject(Fixtures.project(clientId = client.id).copy(id = PROJECT))

        return SqlDelightInvoiceRepository(
            provider,
            LocalStudioContext(),
            clock,
            Dispatchers.Unconfined,
            RemoteWriter(transport),
        )
    }

    private fun invoice() =
        Invoice(
            id = InvoiceId("inv-1"),
            studioId = STUDIO,
            projectId = PROJECT,
            number = "INV-001",
            kind = InvoiceKind.Full,
            status = InvoiceStatus.Draft,
            currency = CurrencyCode("USD"),
            audit = AuditMetadata(createdAt = NOW, updatedAt = NOW),
        )

    /** Answers like the server does, or refuses to answer at all. */
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
            return changes.identitiesForTest().map {
                SyncPushResult(it.first, it.second, SyncPushOutcome.Applied, 1)
            }
        }
    }

    private companion object {
        val STUDIO: StudioId = LocalStudioContext.LOCAL_STUDIO_ID
        val NOW: Instant = Instant.fromEpochMilliseconds(1_781_000_000_000)
        val PROJECT = ProjectId("project-1")
    }
}

/** Enough identity to answer a push in a test, without duplicating the engine's own mapping. */
private fun SyncPushRequest.identitiesForTest(): List<Pair<String, String>> =
    invoices.map { "invoice" to it.id.value } + payments.map { "payment" to it.id.value }
