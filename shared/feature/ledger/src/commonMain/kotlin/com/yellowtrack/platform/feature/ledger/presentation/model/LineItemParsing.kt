package com.yellowtrack.platform.feature.ledger.presentation.model

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.basisPointsAsPercentage
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.common.money.parsePercentageToBasisPoints
import com.yellowtrack.platform.core.model.billing.LineItem

/**
 * Turns a typed line into a billable one, or null if it does not hold up.
 *
 * This rule lives beside the form models rather than inside the ViewModel because both
 * need the same answer: the form shows a running total and decides whether saving is
 * allowed, and the ViewModel decides what is stored. Written twice, the figure a studio
 * watches while typing and the figure that reaches the client could drift apart.
 *
 * A blank tax rate is zero rather than a rejection — most portrait and wedding work is
 * quoted tax-free or tax-inclusive, and demanding a `0` to proceed would be friction with
 * no meaning.
 */
internal fun NewLineItem.toLineItem(currency: CurrencyCode): LineItem? {
    if (description.isBlank()) return null

    val unit = parseMoney(unitPrice, currency)?.takeIf { it.isPositive } ?: return null
    val count = quantity.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val basisPoints =
        when {
            taxRate.isBlank() -> 0
            else -> parsePercentageToBasisPoints(taxRate)?.takeIf { it >= 0 } ?: return null
        }

    return LineItem(
        description = description.trim(),
        unitPrice = unit,
        quantity = count,
        taxRateBasisPoints = basisPoints,
    )
}

/**
 * And back again, for a draft being corrected.
 *
 * Beside its inverse for the same reason that one is here: two functions that disagree
 * about what a blank tax rate means would let a line open showing "0" where the studio
 * typed nothing, or nothing where it typed zero, and neither is what was entered.
 */
internal fun LineItem.toForm(): NewLineItem =
    NewLineItem(
        description = description,
        quantity = quantity.toString(),
        // Digits rather than a rendering, as every other reopened form does.
        unitPrice = unitPrice.toPlainString(),
        taxRate = if (taxRateBasisPoints == 0) "" else taxRateBasisPoints.basisPointsAsPercentage(),
    )

/**
 * Every line, or null if any single one of them does not hold up.
 *
 * All or nothing: a document saved with the bad line quietly dropped would bill a client
 * for less than the studio believed it had entered, and nothing on screen would say so.
 */
internal fun List<NewLineItem>.toLineItems(currency: CurrencyCode): List<LineItem>? {
    if (isEmpty()) return null

    return map { line -> line.toLineItem(currency) ?: return null }
}
