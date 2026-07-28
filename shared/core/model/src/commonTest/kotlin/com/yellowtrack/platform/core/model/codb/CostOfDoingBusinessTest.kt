package com.yellowtrack.platform.core.model.codb

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CostOfDoingBusinessTest {
    private val usd = CurrencyCode.USD
    private val now = Instant.fromEpochMilliseconds(1_781_100_000_000)

    private fun profile(
        salary: Long = 40_000,
        billableDays: Int = 100,
        taxBasisPoints: Int = 0,
        overheadOverride: Long? = null,
        profitBasisPoints: Int = 0,
    ) = CodbProfile(
        id = CodbProfileId.new(),
        studioId = StudioId("studio"),
        currency = usd,
        targetAnnualSalary = Money.ofMajor(salary, usd),
        billableDaysPerYear = billableDays,
        taxRateBasisPoints = taxBasisPoints,
        annualOverheadOverride = overheadOverride?.let { Money.ofMajor(it, usd) },
        desiredProfitMarginBasisPoints = profitBasisPoints,
        audit = AuditMetadata.createdAt(now),
    )

    @Test
    fun `divides overhead plus pay across billable days`() {
        val breakdown =
            CostOfDoingBusiness.calculate(
                profile(salary = 40_000, billableDays = 100, overheadOverride = 20_000),
            )

        assertEquals(Money.ofMajor(60_000, usd), breakdown.totalAnnualRequirement)
        assertEquals(Money.ofMajor(600, usd), breakdown.costPerBillableDay)
    }

    @Test
    fun `grosses salary up for tax rather than adding the rate`() {
        val breakdown =
            CostOfDoingBusiness.calculate(
                profile(salary = 40_000, billableDays = 100, taxBasisPoints = 3_000, overheadOverride = 0),
            )

        // To take home 40,000 at 30%, a studio must earn 40,000 / 0.7 = 57,142.86.
        // Adding 30% instead would give 52,000 and leave the studio 5,142 short.
        assertEquals(Money(5_714_286, usd), breakdown.totalAnnualRequirement)
        assertEquals(Money(1_714_286, usd), breakdown.taxAllowance)

        assertTrue(
            breakdown.totalAnnualRequirement > Money.ofMajor(52_000, usd),
            "grossing up must exceed the naive 'add the tax rate' figure",
        )
    }

    @Test
    fun `does not gross up overhead which is already deductible`() {
        val withOverhead =
            CostOfDoingBusiness.calculate(
                profile(salary = 40_000, taxBasisPoints = 3_000, overheadOverride = 20_000),
            )
        val withoutOverhead =
            CostOfDoingBusiness.calculate(
                profile(salary = 40_000, taxBasisPoints = 3_000, overheadOverride = 0),
            )

        assertEquals(
            Money.ofMajor(20_000, usd),
            withOverhead.totalAnnualRequirement - withoutOverhead.totalAnnualRequirement,
            "overhead must pass through untaxed",
        )
    }

    @Test
    fun `sums overhead from expenses when no override is set`() {
        val breakdown =
            CostOfDoingBusiness.calculate(
                profile = profile(salary = 40_000, billableDays = 100),
                overheadFromExpenses = Money.ofMajor(18_000, usd),
            )

        assertEquals(Money.ofMajor(58_000, usd), breakdown.totalAnnualRequirement)
    }

    @Test
    fun `an override takes precedence over recorded expenses`() {
        val breakdown =
            CostOfDoingBusiness.calculate(
                profile = profile(salary = 40_000, overheadOverride = 20_000),
                overheadFromExpenses = Money.ofMajor(999_999, usd),
            )

        assertEquals(Money.ofMajor(20_000, usd), breakdown.annualOverhead)
        assertEquals(Money.ofMajor(60_000, usd), breakdown.totalAnnualRequirement)
    }

    @Test
    fun `applies a profit margin on top of costs and pay`() {
        val breakdown =
            CostOfDoingBusiness.calculate(
                profile(salary = 40_000, overheadOverride = 20_000, profitBasisPoints = 1_500),
            )

        assertEquals(Money.ofMajor(9_000, usd), breakdown.profitAllowance)
        assertEquals(Money.ofMajor(69_000, usd), breakdown.totalAnnualRequirement)
    }

    @Test
    fun `rounds the day rate up so it is never a fraction below cost`() {
        // 10,000 across 3 days is 3,333.33; charging 3,333.33 leaves the studio short.
        val breakdown =
            CostOfDoingBusiness.calculate(
                profile(salary = 10_000, billableDays = 3, overheadOverride = 0),
            )

        assertEquals(Money(333_334, usd), breakdown.costPerBillableDay)
        assertTrue(
            breakdown.impliedAnnualRevenue >= breakdown.totalAnnualRequirement,
            "a year at the computed day rate must cover the year's requirement",
        )
    }

    @Test
    fun `prices a job by the days it consumes end to end`() {
        val breakdown =
            CostOfDoingBusiness.calculate(
                profile(salary = 40_000, billableDays = 100, overheadOverride = 20_000),
            )

        // A wedding is one shooting day plus roughly three of culling, editing, and admin.
        assertEquals(Money.ofMajor(2_400, usd), breakdown.minimumPriceFor(daysConsumed = 4.0))
        assertEquals(Money.ofMajor(300, usd), breakdown.minimumPriceFor(daysConsumed = 0.5))
    }

    @Test
    fun `flags a package priced below cost`() {
        val breakdown =
            CostOfDoingBusiness.calculate(
                profile(salary = 40_000, billableDays = 100, overheadOverride = 20_000),
            )

        // The seeded wedding template charges 4,500 for a job consuming four days.
        val healthy = breakdown.assess(price = Money.ofMajor(4_500, usd), daysConsumed = 4.0)
        assertFalse(healthy.isBelowCost)
        assertEquals(Money.ofMajor(2_100, usd), healthy.difference)

        // The same four days sold at 1,800 loses money, however busy it makes the studio.
        val loss = breakdown.assess(price = Money.ofMajor(1_800, usd), daysConsumed = 4.0)
        assertTrue(loss.isBelowCost)
        assertEquals(Money.ofMajor(-600, usd), loss.difference)
        assertEquals(Money.ofMajor(450, usd), loss.effectiveDayRate)
    }

    @Test
    fun `rejects a tax rate of one hundred percent or more`() {
        assertFailsWith<IllegalArgumentException> { profile(taxBasisPoints = 10_000) }
    }

    @Test
    fun `rejects a studio with no billable days`() {
        assertFailsWith<IllegalArgumentException> { profile(billableDays = 0) }
    }

    @Test
    fun `refuses to mix currencies`() {
        val eurProfile =
            CodbProfile(
                id = CodbProfileId.new(),
                studioId = StudioId("studio"),
                currency = CurrencyCode.EUR,
                targetAnnualSalary = Money.ofMajor(40_000, usd),
                billableDaysPerYear = 100,
                audit = AuditMetadata.createdAt(now),
            )

        assertFailsWith<IllegalArgumentException> { CostOfDoingBusiness.calculate(eurProfile) }
    }
}
