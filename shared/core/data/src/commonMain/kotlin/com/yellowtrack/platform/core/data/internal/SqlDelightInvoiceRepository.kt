package com.yellowtrack.platform.core.data.internal

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.InvoiceRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.database.YellowTrackDatabase
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import com.yellowtrack.platform.core.database.Invoice as InvoiceRow

/**
 * Money, written through the server.
 *
 * The first repository moved to ADR 0012: the server is asked first, and the local tables are
 * a cache written afterwards. Nothing is queued, because nothing is held — a write that did
 * not reach the server did not happen, and says so.
 *
 * The ledger went first deliberately. It is the desk-bound half, where a connection is the
 * normal case rather than the lucky one, and it is where the conflicts came from.
 */
internal class SqlDelightInvoiceRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
    private val remote: RemoteWriter,
) : DatabaseBackedRepository(provider),
    InvoiceRepository {
    private val studioId get() = studioContext.studioId.value

    override fun observeInvoices(): Flow<List<Invoice>> =
        observing { db ->
            db.invoiceQueries
                .selectAll(studioId)
                .asListFlow(dispatcher)
                .withPayments(db)
        }

    override fun observeInvoice(invoiceId: InvoiceId): Flow<Invoice?> =
        observing { db ->
            db.invoiceQueries
                .selectById(invoiceId.value)
                .asListFlow(dispatcher)
                .withPayments(db)
                .map(List<Invoice>::firstOrNull)
        }

    override fun observeInvoicesForProject(projectId: ProjectId): Flow<List<Invoice>> =
        observing { db ->
            db.invoiceQueries
                .selectByProject(projectId.value)
                .asListFlow(dispatcher)
                .withPayments(db)
        }

    override suspend fun getInvoice(invoiceId: InvoiceId): Invoice? = observeInvoice(invoiceId).first()

    override suspend fun saveInvoice(invoice: Invoice) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // The server first. If this throws the cache is untouched, so the screen still shows
        // what is actually stored rather than a save that never happened.
        remote.write(SyncPushRequest(invoices = listOf(invoice)))

        db.transaction {
            db.invoiceQueries.insertOrIgnore(
                id = invoice.id.value,
                studio_id = invoice.studioId.value,
                project_id = invoice.projectId.value,
                number = invoice.number,
                kind = invoice.kind.name,
                status = invoice.status.name,
                currency = invoice.currency.code,
                lines = encodeLines(invoice.lines),
                issued_at = invoice.issuedAt.toEpochMillisOrNull(),
                due_at = invoice.dueAt.toEpochMillisOrNull(),
                notes = invoice.notes,
                created_at = invoice.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = invoice.audit.deletedAt.toEpochMillisOrNull(),
                version = invoice.audit.version.toLong(),
                last_emailed_at = invoice.lastEmailedAt.toEpochMillisOrNull(),
                last_emailed_to = invoice.lastEmailedTo,
            )

            db.invoiceQueries.update(
                projectId = invoice.projectId.value,
                number = invoice.number,
                kind = invoice.kind.name,
                status = invoice.status.name,
                currency = invoice.currency.code,
                lines = encodeLines(invoice.lines),
                issuedAt = invoice.issuedAt.toEpochMillisOrNull(),
                dueAt = invoice.dueAt.toEpochMillisOrNull(),
                notes = invoice.notes,
                updatedAt = now,
                deletedAt = invoice.audit.deletedAt.toEpochMillisOrNull(),
                version = invoice.audit.version.toLong(),
                lastEmailedAt = invoice.lastEmailedAt.toEpochMillisOrNull(),
                lastEmailedTo = invoice.lastEmailedTo,
                id = invoice.id.value,
            )
        }
    }

    override suspend fun deleteInvoice(invoiceId: InvoiceId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // Read first, because a delete travels as the row with a tombstone on it rather than
        // as an instruction. Gone already is not a failure worth reporting to a studio that
        // asked for exactly that.
        val existing = getInvoice(invoiceId) ?: return

        remote.write(SyncPushRequest(invoices = listOf(existing.copy(audit = existing.audit.deleted(instant(now))))))

        db.transaction {
            db.invoiceQueries.softDelete(deletedAt = now, id = invoiceId.value)
        }
    }

    override suspend fun recordInvoiceEmailed(
        invoiceId: InvoiceId,
        to: String,
    ) {
        val db = database()
        val now = clock.now().toEpochMillis()

        val existing = getInvoice(invoiceId) ?: return

        // Sent so the studio's other devices know, without anybody remembering which one did
        // the sending. It is a fact about the document, not about the device.
        remote.write(
            SyncPushRequest(
                invoices =
                    listOf(
                        existing.copy(
                            lastEmailedAt = instant(now),
                            lastEmailedTo = to,
                            audit = existing.audit.touched(instant(now)),
                        ),
                    ),
            ),
        )

        db.transaction {
            db.invoiceQueries.recordEmailed(emailedAt = now, emailedTo = to, id = invoiceId.value)
        }
    }

    override suspend fun recordPayment(payment: Payment) {
        val db = database()
        val now = clock.now().toEpochMillis()

        remote.write(SyncPushRequest(payments = listOf(payment)))

        db.transaction {
            db.invoiceQueries.insertOrIgnorePayment(
                id = payment.id.value,
                studio_id = payment.studioId.value,
                invoice_id = payment.invoiceId.value,
                amount_minor = payment.amount.minorUnits,
                amount_currency = payment.amount.currency.code,
                paid_at = payment.paidAt.toEpochMillis(),
                method = payment.method.name,
                reference = payment.reference,
                notes = payment.notes,
                created_at = payment.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = payment.audit.deletedAt.toEpochMillisOrNull(),
                version = payment.audit.version.toLong(),
            )

            db.invoiceQueries.updatePayment(
                amountMinor = payment.amount.minorUnits,
                amountCurrency = payment.amount.currency.code,
                paidAt = payment.paidAt.toEpochMillis(),
                method = payment.method.name,
                reference = payment.reference,
                notes = payment.notes,
                updatedAt = now,
                deletedAt = payment.audit.deletedAt.toEpochMillisOrNull(),
                version = payment.audit.version.toLong(),
                id = payment.id.value,
            )
        }
    }

    override suspend fun deletePayment(paymentId: PaymentId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        val existing = paymentOf(db, paymentId) ?: return

        remote.write(
            SyncPushRequest(payments = listOf(existing.copy(audit = existing.audit.deleted(instant(now))))),
        )

        db.transaction {
            db.invoiceQueries.softDeletePayment(deletedAt = now, id = paymentId.value)
        }
    }

    /**
     * Attaches payments to their invoices.
     *
     * Recomputed whenever either table changes, so recording a payment updates the
     * balance, the overdue state, and the total outstanding at once.
     */
    private fun Flow<List<InvoiceRow>>.withPayments(db: YellowTrackDatabase): Flow<List<Invoice>> =
        combine(db.invoiceQueries.selectPaymentsForStudio(studioId).asListFlow(dispatcher)) { rows, paymentRows ->
            val paymentsByInvoice = paymentRows.groupBy { it.invoice_id }

            rows.map { row ->
                row.toDomain(payments = paymentsByInvoice[row.id].orEmpty().map { it.toDomain() })
            }
        }

    /**
     * One payment, by id.
     *
     * Read before a delete because a delete travels as the row with a tombstone on it. There
     * is no observing query for a single payment — they are always read through their invoice
     * — so this asks the table directly.
     */
    private suspend fun paymentOf(
        db: YellowTrackDatabase,
        paymentId: PaymentId,
    ): Payment? =
        db.invoiceQueries
            .selectPaymentByIdForSync(paymentId.value)
            .awaitAsOneOrNull()
            ?.toDomain()

    private fun instant(millis: Long) = Instant.fromEpochMilliseconds(millis)
}
