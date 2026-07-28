package com.yellowtrack.platform.core.common.money

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * An ISO 4217 currency code.
 *
 * Currency is stored explicitly rather than assumed: commercial and
 * destination-wedding work regularly crosses currencies, and a studio's
 * default currency is a per-studio setting rather than a global constant.
 */
@Serializable
@JvmInline
value class CurrencyCode(
    val code: String,
) {
    init {
        require(code.length == 3 && code.all { it in 'A'..'Z' }) {
            "Currency code must be three uppercase letters, was '$code'"
        }
    }

    override fun toString(): String = code

    companion object {
        val USD = CurrencyCode("USD")
        val EUR = CurrencyCode("EUR")
        val GBP = CurrencyCode("GBP")
        val CAD = CurrencyCode("CAD")
        val AUD = CurrencyCode("AUD")
        val KES = CurrencyCode("KES")
    }
}
