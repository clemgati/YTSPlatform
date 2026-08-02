package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.InvoiceRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.database.YellowTrackDatabase
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yellowtrack.platform.core.database.Invoice as InvoiceRow

internal class SqlDelightInvoiceRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
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
                id = invoice.id.value,
            )

            db.enqueueForSync(
                invoice.studioId.value,
                SyncTables.INVOICE,
                invoice.id.value,
                OutboxOperation.Upsert,
                now,
            )
        }

        // Payments carried on the object are persisted too, so that saving an invoice
        // built in memory does not silently drop the money recorded against it. Each is
        // queued separately by recordPayment: they are their own rows on the wire.
        invoice.payments.forEach { recordPayment(it) }
    }

    override suspend fun deleteInvoice(invoiceId: InvoiceId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.invoiceQueries.softDelete(deletedAt = now, id = invoiceId.value)
            db.enqueueForSync(studioId, SyncTables.INVOICE, invoiceId.value, OutboxOperation.Delete, now)
        }
    }

    override suspend fun recordPayment(payment: Payment) {
        val db = database()
        val now = clock.now().toEpochMillis()

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

            db.enqueueForSync(
                payment.studioId.value,
                SyncTables.PAYMENT,
                payment.id.value,
                OutboxOperation.Upsert,
                now,
            )
        }
    }

    override suspend fun deletePayment(paymentId: PaymentId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.invoiceQueries.softDeletePayment(deletedAt = now, id = paymentId.value)
            db.enqueueForSync(studioId, SyncTables.PAYMENT, paymentId.value, OutboxOperation.Delete, now)
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
}
