package com.yellowtrack.platform.core.model.invoice

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.money.sum
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.billing.grandTotal
import com.yellowtrack.platform.core.model.billing.subtotal
import com.yellowtrack.platform.core.model.billing.taxTotal
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A demand for payment against a booking.
 *
 * Payments are held on the invoice rather than looked up separately, so that every figure
 * the studio reads — balance due, overdue state, total outstanding — is computed from the
 * same set of facts and cannot disagree with itself.
 */
@Serializable
data class Invoice(
    val id: InvoiceId,
    override val studioId: StudioId,
    val projectId: ProjectId,
    val number: String,
    val kind: InvoiceKind,
    val status: InvoiceStatus,
    val currency: CurrencyCode,
    val lines: List<LineItem> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val issuedAt: Instant? = null,
    val dueAt: Instant? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    val subtotal: Money get() = lines.subtotal(currency)

    val tax: Money get() = lines.taxTotal(currency)

    val total: Money get() = lines.grandTotal(currency)

    val amountPaid: Money get() = payments.map(Payment::amount).sum(currency)

    /** Negative when a client has overpaid, which happens and should be visible. */
    val balanceDue: Money get() = total - amountPaid

    val isSettled: Boolean get() = status == InvoiceStatus.Sent && amountPaid >= total

    /**
     * The invoice's real state, computed from its payments and due date.
     *
     * A draft owes nothing and a void invoice owes nothing, regardless of their lines.
     */
    fun paymentState(now: Instant): PaymentState =
        when {
            status == InvoiceStatus.Draft -> PaymentState.Draft
            status == InvoiceStatus.Void -> PaymentState.Void
            amountPaid >= total -> PaymentState.Paid
            isPastDue(now) -> PaymentState.Overdue
            amountPaid.isPositive -> PaymentState.PartiallyPaid
            else -> PaymentState.AwaitingPayment
        }

    fun isPastDue(now: Instant): Boolean = dueAt != null && now > dueAt

    /** How late, for sorting the chase list by severity. */
    fun overdueBy(now: Instant): Duration? = if (paymentState(now) == PaymentState.Overdue) now - dueAt!! else null

    /** What this invoice contributes to money owed. Drafts and voids contribute nothing. */
    fun outstanding(now: Instant): Money = if (paymentState(now).isOutstanding) balanceDue else Money.zero(currency)
}
