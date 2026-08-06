package com.yellowtrack.platform.core.model.quote

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.billing.grandTotal
import com.yellowtrack.platform.core.model.billing.subtotal
import com.yellowtrack.platform.core.model.billing.taxTotal
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A priced proposal for a booking.
 *
 * @param validUntil proposals expire on purpose. An open-ended quote is a price the studio
 *   has to honour indefinitely, through cost increases and fully-booked dates.
 */
@Serializable
data class Quote(
    val id: QuoteId,
    override val studioId: StudioId,
    val projectId: ProjectId,
    val number: String,
    val status: QuoteStatus,
    val currency: CurrencyCode,
    val lines: List<LineItem> = emptyList(),
    val issuedAt: Instant? = null,
    val validUntil: Instant? = null,
    val acceptedAt: Instant? = null,
    val declinedAt: Instant? = null,
    val notes: String? = null,
    val terms: String? = null,
    /** See [com.yellowtrack.platform.core.model.invoice.Invoice.lastEmailedAt]. */
    val lastEmailedAt: Instant? = null,
    val lastEmailedTo: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    val subtotal: Money get() = lines.subtotal(currency)

    val tax: Money get() = lines.taxTotal(currency)

    val total: Money get() = lines.grandTotal(currency)

    fun isExpired(now: Instant): Boolean = status == QuoteStatus.Sent && validUntil != null && now > validUntil

    /**
     * The state to show, which is not always the stored one: a sent quote whose validity
     * has lapsed is expired whether or not anyone has updated the record.
     */
    fun effectiveStatus(now: Instant): QuoteStatus = if (isExpired(now)) QuoteStatus.Expired else status
}
