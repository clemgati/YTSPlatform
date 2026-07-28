package com.yellowtrack.platform.core.common.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyParsingTest {
    private val usd = CurrencyCode.USD

    @Test
    fun `parses whole and fractional amounts exactly`() {
        assertEquals(Money(4_500_00, usd), parseMoney("4500", usd))
        assertEquals(Money(4_500_00, usd), parseMoney("4500.00", usd))
        assertEquals(Money(4_500_50, usd), parseMoney("4500.5", usd))
        assertEquals(Money(7, usd), parseMoney("0.07", usd))
        assertEquals(Money(7, usd), parseMoney(".07", usd))
    }

    @Test
    fun `accepts what a person actually types or pastes`() {
        assertEquals(Money(1_250_00, usd), parseMoney("$1,250.00", usd))
        assertEquals(Money(1_250_00, usd), parseMoney("  1 250.00 ", usd))
        assertEquals(Money(1_250_00, usd), parseMoney("USD1250", usd))
    }

    @Test
    fun `parses negative amounts for credits and refunds`() {
        assertEquals(Money(-500_00, usd), parseMoney("-500", usd))
    }

    @Test
    fun `rejects anything it cannot read exactly`() {
        assertNull(parseMoney("", usd))
        assertNull(parseMoney("abc", usd))
        assertNull(parseMoney("12.34.56", usd))
        assertNull(parseMoney("1.2.3", usd))
        // Three decimal places is more precision than the currency has; silently
        // truncating would change the amount without telling anyone.
        assertNull(parseMoney("10.005", usd))
    }

    @Test
    fun `parses a tax rate into basis points`() {
        assertEquals(2_800, parsePercentageToBasisPoints("28"))
        assertEquals(2_850, parsePercentageToBasisPoints("28.5"))
        assertEquals(2_800, parsePercentageToBasisPoints("28%"))
        assertEquals(0, parsePercentageToBasisPoints("0"))
    }

    @Test
    fun `rejects a tax rate outside nought to one hundred percent`() {
        assertNull(parsePercentageToBasisPoints("101"))
        assertNull(parsePercentageToBasisPoints("-5"))
        assertNull(parsePercentageToBasisPoints("abc"))
    }

    @Test
    fun `renders basis points back as a percentage`() {
        assertEquals("28", 2_800.basisPointsAsPercentage())
        assertEquals("28.5", 2_850.basisPointsAsPercentage())
        assertEquals("28.25", 2_825.basisPointsAsPercentage())
        assertEquals("0", 0.basisPointsAsPercentage())
    }

    @Test
    fun `round trips through the display form`() {
        val original = Money(4_500_75, usd)
        assertEquals(original, parseMoney(original.toPlainString(), usd))
    }
}
