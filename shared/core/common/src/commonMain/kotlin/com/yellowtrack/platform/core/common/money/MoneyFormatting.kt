package com.yellowtrack.platform.core.common.money

/**
 * Renders an amount the way a person reads money.
 *
 * `toPlainString` exists for logs, tests, and form fields, where grouping separators would
 * have to be stripped again before parsing. This is its counterpart for display: a studio
 * scanning a list of balances reads "$4,000.00" far faster than "USD 4000.00", and the
 * difference matters most on exactly the screen where the figures are largest.
 */
fun Money.formatted(
    showSymbol: Boolean = true,
    fractionDigits: Int = 2,
): String {
    val plain = toPlainString(fractionDigits)
    val negative = plain.startsWith('-')
    val unsigned = plain.removePrefix("-")

    val whole = unsigned.substringBefore('.')
    val fraction = unsigned.substringAfter('.', missingDelimiterValue = "")

    val grouped = whole.groupThousands()
    val amount = if (fraction.isEmpty()) grouped else "$grouped.$fraction"

    val symbol = currency.symbol
    val body =
        if (showSymbol && symbol != null) {
            "$symbol$amount"
        } else {
            "${currency.code} $amount"
        }

    // Leading minus rather than parentheses: a negative here is a credit or an overpayment,
    // not an accounting loss, and the plain form is less easily misread.
    return if (negative) "-$body" else body
}

private fun String.groupThousands(): String {
    if (length <= 3) return this

    return reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()
}

/** Null where no symbol is unambiguous, in which case the ISO code is shown instead. */
private val CurrencyCode.symbol: String?
    get() =
        when (code) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            else -> null
        }
