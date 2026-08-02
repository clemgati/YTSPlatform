package com.yellowtrack.platform.core.common.money

/**
 * Parses a typed amount into exact minor units.
 *
 * Never routes through `Double`: "0.07" via a floating-point parse is not exactly seven
 * cents, and an amount that is wrong in the fourth decimal place is wrong on an invoice.
 * The string is split on its separator and each half converted as an integer.
 *
 * Accepts grouping separators and a leading currency symbol so that pasting "$1,250.00"
 * behaves the way a person expects. Returns null for anything it cannot read exactly.
 */
fun parseMoney(
    text: String,
    currency: CurrencyCode,
    fractionDigits: Int = 2,
): Money? {
    val cleaned =
        text
            .trim()
            .replace(",", "")
            .replace(" ", "")
            .removePrefix(currency.code)
            .trimStart('$', '£', '€', '¥')
            .trim()

    if (cleaned.isEmpty()) return null

    val negative = cleaned.startsWith('-')
    val digits = cleaned.removePrefix("-").removePrefix("+")

    if (digits.isEmpty() || digits.count { it == '.' } > 1) return null

    val (wholePart, fractionPart) =
        if (digits.contains('.')) {
            digits.substringBefore('.') to digits.substringAfter('.')
        } else {
            digits to ""
        }

    if (wholePart.any { !it.isDigit() } || fractionPart.any { !it.isDigit() }) return null
    if (fractionPart.length > fractionDigits) return null

    val whole = if (wholePart.isEmpty()) 0L else wholePart.toLongOrNull() ?: return null
    val fraction =
        if (fractionPart.isEmpty()) {
            0L
        } else {
            fractionPart.padEnd(fractionDigits, '0').toLongOrNull() ?: return null
        }

    var multiplier = 1L
    repeat(fractionDigits) { multiplier *= 10 }

    val minorUnits = whole * multiplier + fraction

    return Money(if (negative) -minorUnits else minorUnits, currency)
}

/**
 * Parses a percentage such as "28" or "28.5" into basis points.
 *
 * Basis points keep tax arithmetic in integers all the way through.
 */
fun parsePercentageToBasisPoints(text: String): Int? {
    val cleaned = text.trim().removeSuffix("%").trim()
    if (cleaned.isEmpty()) return null

    val asMoneyLike = parseMoney(cleaned, CurrencyCode.USD, fractionDigits = 2) ?: return null
    val basisPoints = asMoneyLike.minorUnits

    return if (basisPoints in 0..10_000) basisPoints.toInt() else null
}

/** Renders basis points back as a percentage: 2800 becomes "28". */
fun Int.basisPointsAsPercentage(): String {
    val whole = this / 100
    val remainder = this % 100

    return when {
        remainder == 0 -> "$whole"
        remainder % 10 == 0 -> "$whole.${remainder / 10}"
        else -> "$whole.${remainder.toString().padStart(2, '0')}"
    }
}

/**
 * The amount as a form would hold it, so an edit reopens on what was recorded.
 *
 * The inverse of [parseMoney], and deliberately not [Money.display]: a display carries a
 * currency symbol and grouping separators, and a form that opens showing "$1,240.00" makes
 * the studio delete punctuation before it can change a digit.
 *
 * Integer arithmetic throughout, for the reason [parseMoney] gives — a round trip through
 * `Double` is not the amount that was recorded.
 */
fun Money.editableAmount(fractionDigits: Int = 2): String {
    var scale = 1L
    repeat(fractionDigits) { scale *= 10 }

    val sign = if (minorUnits < 0) "-" else ""
    val magnitude = if (minorUnits < 0) -minorUnits else minorUnits
    val whole = magnitude / scale
    val fraction = magnitude % scale

    if (fractionDigits == 0) return "$sign$whole"

    return "$sign$whole.${fraction.toString().padStart(fractionDigits, '0')}"
}
