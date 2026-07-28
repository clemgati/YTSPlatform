package com.yellowtrack.platform.core.model.billing

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.money.sum
import kotlinx.serialization.Serializable

/**
 * A billable line, shared by quotes and invoices.
 *
 * Deliberately one type rather than two: a quote line and an invoice line have the same
 * shape, and accepting a quote should produce an invoice carrying exactly the lines that
 * were agreed. Duplicating the type would make that conversion a place for figures to
 * drift.
 *
 * @param taxRateBasisPoints per line rather than per document, because tax often differs
 *   between a service and a physical product such as an album or print.
 */
@Serializable
data class LineItem(
    val description: String,
    val unitPrice: Money,
    val quantity: Int = 1,
    val taxRateBasisPoints: Int = 0,
) {
    init {
        require(quantity >= 0) { "Line quantity cannot be negative" }
        require(taxRateBasisPoints >= 0) { "Tax rate cannot be negative" }
    }

    val subtotal: Money get() = unitPrice * quantity

    val tax: Money get() = subtotal.applyRate(taxRateBasisPoints)

    val total: Money get() = subtotal + tax
}

/**
 * Tax is summed per line and never recomputed from the document subtotal — with mixed
 * rates those two figures disagree, and the per-line total is the one that matches what
 * the client was shown.
 */
fun List<LineItem>.subtotal(currency: CurrencyCode): Money = map(LineItem::subtotal).sum(currency)

fun List<LineItem>.taxTotal(currency: CurrencyCode): Money = map(LineItem::tax).sum(currency)

fun List<LineItem>.grandTotal(currency: CurrencyCode): Money = map(LineItem::total).sum(currency)
