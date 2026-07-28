package com.yellowtrack.platform.core.common.money

import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue

/**
 * An exact monetary amount, stored as integer minor units plus an explicit currency.
 *
 * Money is never represented as a floating-point number. `0.1 + 0.2` is not `0.3`, and
 * an invoice that disagrees with a client's arithmetic by a cent is an invoice that
 * costs an email to explain.
 *
 * @param minorUnits the amount in the currency's smallest unit — cents for USD, pence
 *   for GBP. Negative values are permitted and represent credits or refunds.
 */
@Serializable
data class Money(
    val minorUnits: Long,
    val currency: CurrencyCode,
) : Comparable<Money> {
    val isZero: Boolean get() = minorUnits == 0L
    val isNegative: Boolean get() = minorUnits < 0L
    val isPositive: Boolean get() = minorUnits > 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = minorUnits + other.minorUnits)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = minorUnits - other.minorUnits)
    }

    operator fun times(quantity: Int): Money = copy(minorUnits = minorUnits * quantity)

    operator fun unaryMinus(): Money = copy(minorUnits = -minorUnits)

    /**
     * Multiplies by a non-monetary quantity — miles driven, hours worked — rounding half
     * away from zero.
     *
     * The multiplier is a `Double` because the quantity genuinely is fractional; the
     * amount itself never is. This is not a licence to represent money as a Double.
     */
    fun timesQuantity(multiplier: Double): Money {
        val scaled = minorUnits * multiplier
        val rounded = if (scaled < 0) -kotlin.math.floor(-scaled + 0.5) else kotlin.math.floor(scaled + 0.5)
        return copy(minorUnits = rounded.toLong())
    }

    /**
     * Divides into [parts], rounding up.
     *
     * Rounds up rather than to nearest because every use of this is a cost being spread
     * over a denominator, and under-recovering a cost is the failure mode that matters.
     */
    fun dividedBy(parts: Int): Money {
        require(parts > 0) { "Cannot divide $this into $parts parts" }
        val quotient =
            if (minorUnits >= 0) {
                (minorUnits + parts - 1) / parts
            } else {
                -((-minorUnits + parts - 1) / parts)
            }
        return copy(minorUnits = quotient)
    }

    /**
     * Applies a rate expressed in basis points, rounding half away from zero.
     *
     * Basis points keep tax and discount arithmetic in integers: 8.25% sales tax is
     * `825`, a 15% discount is `1500`.
     */
    fun applyRate(basisPoints: Int): Money {
        val scaled = minorUnits * basisPoints
        val rounded =
            if (scaled < 0) {
                (scaled - BASIS_POINT_SCALE / 2) / BASIS_POINT_SCALE
            } else {
                (scaled + BASIS_POINT_SCALE / 2) / BASIS_POINT_SCALE
            }
        return copy(minorUnits = rounded)
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    /**
     * Renders the amount without a currency symbol or locale grouping — suitable for
     * logs, tests, and export. User-facing formatting is a presentation concern.
     */
    fun toPlainString(fractionDigits: Int = DEFAULT_FRACTION_DIGITS): String {
        if (fractionDigits == 0) return minorUnits.toString()

        var divisor = 1L
        repeat(fractionDigits) { divisor *= 10 }

        val sign = if (minorUnits < 0) "-" else ""
        val absolute = minorUnits.absoluteValue
        val whole = absolute / divisor
        val fraction = (absolute % divisor).toString().padStart(fractionDigits, '0')

        return "$sign$whole.$fraction"
    }

    override fun toString(): String = "${toPlainString()} $currency"

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot combine $currency with ${other.currency}"
        }
    }

    companion object {
        private const val BASIS_POINT_SCALE = 10_000L
        private const val DEFAULT_FRACTION_DIGITS = 2

        fun zero(currency: CurrencyCode): Money = Money(0L, currency)

        /** Builds an amount from whole major units — `Money.ofMajor(250, USD)` is $250.00. */
        fun ofMajor(
            majorUnits: Long,
            currency: CurrencyCode,
            fractionDigits: Int = DEFAULT_FRACTION_DIGITS,
        ): Money {
            var multiplier = 1L
            repeat(fractionDigits) { multiplier *= 10 }
            return Money(majorUnits * multiplier, currency)
        }
    }
}

/** Sums a collection of amounts, which must all share a currency. */
fun Iterable<Money>.sum(currency: CurrencyCode): Money = fold(Money.zero(currency)) { total, amount -> total + amount }
