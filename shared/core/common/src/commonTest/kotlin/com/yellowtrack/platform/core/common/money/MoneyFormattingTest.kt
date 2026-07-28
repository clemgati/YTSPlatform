package com.yellowtrack.platform.core.common.money

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormattingTest {
    private val usd = CurrencyCode.USD

    @Test
    fun `groups thousands and prefixes a symbol`() {
        assertEquals("$4,000.00", Money.ofMajor(4_000, usd).formatted())
        assertEquals("$725.12", Money(72_512, usd).formatted())
        assertEquals("$65,260.00", Money.ofMajor(65_260, usd).formatted())
        assertEquals("$1,234,567.89", Money(123_456_789, usd).formatted())
    }

    @Test
    fun `does not group amounts below a thousand`() {
        assertEquals("$0.07", Money(7, usd).formatted())
        assertEquals("$999.99", Money(99_999, usd).formatted())
    }

    @Test
    fun `shows a leading minus for credits and overpayments`() {
        assertEquals("-$500.00", Money.ofMajor(-500, usd).formatted())
        assertEquals("-$1,250.00", Money.ofMajor(-1_250, usd).formatted())
    }

    @Test
    fun `falls back to the ISO code where no symbol is unambiguous`() {
        assertEquals("KES 4,000.00", Money.ofMajor(4_000, CurrencyCode.KES).formatted())
        assertEquals("CAD 100.00", Money.ofMajor(100, CurrencyCode.CAD).formatted())
    }

    @Test
    fun `uses the code when a symbol is suppressed`() {
        assertEquals("USD 4,000.00", Money.ofMajor(4_000, usd).formatted(showSymbol = false))
    }

    @Test
    fun `the plain form stays free of grouping so it can be parsed back`() {
        val amount = Money.ofMajor(4_000, usd)

        assertEquals("4000.00", amount.toPlainString())
        assertEquals(amount, parseMoney(amount.toPlainString(), usd))
    }
}
