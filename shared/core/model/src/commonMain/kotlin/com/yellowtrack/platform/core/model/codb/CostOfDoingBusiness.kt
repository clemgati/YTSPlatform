package com.yellowtrack.platform.core.model.codb

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.codb.CodbProfile.Companion.BASIS_POINT_SCALE

/**
 * Works out what a studio must charge in order not to lose money.
 *
 * New photographers underprice because they compare their prices to other photographers'
 * prices, which are themselves guesses. This computes the floor from the studio's own
 * costs instead.
 *
 * The step most naive calculators miss is the **tax gross-up**. To take home 40,000 at an
 * effective rate of 30%, a studio must earn 57,143 in taxable income — not 52,000. Adding
 * the tax rate to the total instead of dividing by its complement under-collects by a
 * margin that grows with the rate, and it under-collects in the one direction that puts
 * a business out of operation.
 */
object CostOfDoingBusiness {
    /**
     * @param overheadFromExpenses the studio's unlinked (overhead) expenses for the year.
     *   Ignored when the profile states an override.
     */
    fun calculate(
        profile: CodbProfile,
        overheadFromExpenses: Money = Money.zero(profile.currency),
    ): CodbBreakdown {
        val currency = profile.currency
        val overhead = profile.annualOverheadOverride ?: overheadFromExpenses

        require(overhead.currency == currency && profile.targetAnnualSalary.currency == currency) {
            "Overhead and salary must be in the studio's currency ($currency)"
        }

        // Overhead is a deductible business cost, so it is not grossed up. Only the
        // owner's pay is taxed, and only it needs to be earned before tax.
        val grossSalary = profile.targetAnnualSalary.grossedUpForTax(profile.taxRateBasisPoints)
        val taxAllowance = grossSalary - profile.targetAnnualSalary

        val beforeProfit = overhead + grossSalary
        val profitAllowance = beforeProfit.applyRate(profile.desiredProfitMarginBasisPoints)
        val totalRequired = beforeProfit + profitAllowance

        return CodbBreakdown(
            currency = currency,
            annualOverhead = overhead,
            targetSalary = profile.targetAnnualSalary,
            taxAllowance = taxAllowance,
            profitAllowance = profitAllowance,
            totalAnnualRequirement = totalRequired,
            billableDaysPerYear = profile.billableDaysPerYear,
            // Rounded up: a day rate a fraction below cost is still below cost.
            costPerBillableDay = totalRequired.dividedBy(profile.billableDaysPerYear),
        )
    }
}

/**
 * Grosses an after-tax amount up to the pre-tax amount required to produce it.
 *
 * `gross × (1 − rate) = net`, so `gross = net ÷ (1 − rate)`. Rounded up, because the
 * entire purpose of this calculation is to stop a studio charging too little.
 */
internal fun Money.grossedUpForTax(taxRateBasisPoints: Int): Money {
    require(taxRateBasisPoints in 0 until BASIS_POINT_SCALE) {
        "Tax rate must be below 100%, was $taxRateBasisPoints basis points"
    }
    if (taxRateBasisPoints == 0) return this

    val numerator = minorUnits * BASIS_POINT_SCALE
    val denominator = (BASIS_POINT_SCALE - taxRateBasisPoints).toLong()
    val gross =
        if (numerator >= 0) {
            (numerator + denominator - 1) / denominator
        } else {
            -((-numerator + denominator - 1) / denominator)
        }

    return copy(minorUnits = gross)
}

/** The answer, with its working shown so a studio can see which input to change. */
data class CodbBreakdown(
    val currency: CurrencyCode,
    val annualOverhead: Money,
    val targetSalary: Money,
    val taxAllowance: Money,
    val profitAllowance: Money,
    val totalAnnualRequirement: Money,
    val billableDaysPerYear: Int,
    /** What one sellable day has to earn. The number that changes how a studio prices. */
    val costPerBillableDay: Money,
) {
    /**
     * The least a job may be sold for, given how many sellable days it consumes.
     *
     * Days consumed is end-to-end, not shoot days. A wedding is roughly one shooting day
     * plus two to three of culling, editing, and album work.
     */
    fun minimumPriceFor(daysConsumed: Double): Money {
        require(daysConsumed > 0) { "A job consumes at least part of a day" }
        return costPerBillableDay.timesQuantity(daysConsumed)
    }

    /** Sanity check: the revenue the studio must book across a full year. */
    val impliedAnnualRevenue: Money get() = costPerBillableDay * billableDaysPerYear

    /**
     * Compares a real price against the floor.
     *
     * This is the question a photographer actually has: *is this package losing me money?*
     */
    fun assess(
        price: Money,
        daysConsumed: Double,
    ): PricingAssessment {
        val minimum = minimumPriceFor(daysConsumed)

        return PricingAssessment(
            price = price,
            minimumPrice = minimum,
            difference = price - minimum,
            daysConsumed = daysConsumed,
        )
    }
}

data class PricingAssessment(
    val price: Money,
    val minimumPrice: Money,
    /** Negative when the price is below cost. */
    val difference: Money,
    val daysConsumed: Double,
) {
    val isBelowCost: Boolean get() = difference.isNegative

    /** What the job actually pays per sellable day, for comparison against the floor. */
    val effectiveDayRate: Money get() = price.timesQuantity(1.0 / daysConsumed)
}
