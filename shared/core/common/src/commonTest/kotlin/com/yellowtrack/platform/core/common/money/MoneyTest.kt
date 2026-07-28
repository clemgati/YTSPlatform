package com.yellowtrack.platform.core.common.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {
    private val usd = CurrencyCode.USD

    @Test
    fun `adds amounts of the same currency`() {
        val retainer = Money.ofMajor(500, usd)
        val balance = Money.ofMajor(1_750, usd)

        assertEquals(Money.ofMajor(2_250, usd), retainer + balance)
    }

    @Test
    fun `refuses to combine different currencies`() {
        assertFailsWith<IllegalArgumentException> {
            Money.ofMajor(100, CurrencyCode.USD) + Money.ofMajor(100, CurrencyCode.EUR)
        }
    }

    @Test
    fun `applies a tax rate in basis points rounding half away from zero`() {
        // 8.25% sales tax on $1,200.00 is $99.00
        assertEquals(Money(9_900, usd), Money.ofMajor(1_200, usd).applyRate(825))

        // 1 cent at 50% rounds up to 1 cent rather than truncating to zero
        assertEquals(Money(1, usd), Money(1, usd).applyRate(5_000))

        // and the same magnitude in the negative direction
        assertEquals(Money(-1, usd), Money(-1, usd).applyRate(5_000))
    }

    @Test
    fun `sums a collection of line items`() {
        val lines =
            listOf(
                Money.ofMajor(2_400, usd),
                Money.ofMajor(350, usd),
                Money.ofMajor(125, usd),
            )

        assertEquals(Money.ofMajor(2_875, usd), lines.sum(usd))
    }

    @Test
    fun `an empty sum is zero in the requested currency`() {
        assertEquals(Money.zero(usd), emptyList<Money>().sum(usd))
    }

    @Test
    fun `renders plain strings with padded fractional digits`() {
        assertEquals("12.00", Money(1_200, usd).toPlainString())
        assertEquals("12.05", Money(1_205, usd).toPlainString())
        assertEquals("0.07", Money(7, usd).toPlainString())
        assertEquals("-3.50", Money(-350, usd).toPlainString())
    }

    @Test
    fun `orders amounts of the same currency`() {
        assertTrue(Money.ofMajor(100, usd) < Money.ofMajor(250, usd))
        assertTrue(Money.ofMajor(-50, usd).isNegative)
        assertTrue(Money.zero(usd).isZero)
    }

    @Test
    fun `rejects malformed currency codes`() {
        assertFailsWith<IllegalArgumentException> { CurrencyCode("usd") }
        assertFailsWith<IllegalArgumentException> { CurrencyCode("DOLLAR") }
    }
}
