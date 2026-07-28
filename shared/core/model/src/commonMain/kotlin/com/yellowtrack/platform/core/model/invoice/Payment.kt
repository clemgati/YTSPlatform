package com.yellowtrack.platform.core.model.invoice

import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Money actually received against an invoice. */
@Serializable
data class Payment(
    val id: PaymentId,
    override val studioId: StudioId,
    val invoiceId: InvoiceId,
    val amount: Money,
    val paidAt: Instant,
    val method: PaymentMethod,
    /** Bank reference, cheque number, or processor transaction id. */
    val reference: String? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped

@Serializable
enum class PaymentMethod {
    BankTransfer,
    Card,
    Cash,
    Cheque,
    DirectDebit,
    Online,
    Other,
}
