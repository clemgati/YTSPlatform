package com.yellowtrack.platform.feature.ledger.presentation.mapper

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.money.formatted
import com.yellowtrack.platform.core.common.money.sum
import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.codb.CodbBreakdown
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.invoice.PaymentState
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.feature.ledger.presentation.model.DraftInvoiceItem
import com.yellowtrack.platform.feature.ledger.presentation.model.ExpenseSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.MoneyOwedSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.OutstandingInvoiceItem
import com.yellowtrack.platform.feature.ledger.presentation.model.PackagePricing
import com.yellowtrack.platform.feature.ledger.presentation.model.PricingSummary
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * How much of a working day a shoot day really consumes, before the studio has measured it.
 *
 * A shoot is rarely just the shoot: culling, editing, colour, album design, and client
 * admin typically take two to three times as long as the time spent with a camera.
 *
 * This is the fallback. Once enough post-production has been recorded and finished, the
 * factor comes from the studio's own hours instead — see [measuredPostProductionFactor].
 * A guess that never becomes a measurement is a guess the whole pricing floor rests on.
 */
private const val ASSUMED_POST_PRODUCTION_FACTOR = 2.0

/**
 * The fewest finished tasks worth trusting.
 *
 * One long edit is not evidence about how a studio works. Below this the assumption stands,
 * because a floor built on a single unusual job is worse than one built on a stated guess.
 */
private const val MINIMUM_TASKS_TO_MEASURE = 3

/**
 * What post-production actually costs this studio, per hour spent shooting.
 *
 * Null until there is enough finished work to say. Computed from tasks that are done and
 * carry real hours: work still in progress has not overrun, it is simply unfinished, and
 * counting it would flatter every open job.
 */
internal fun measuredPostProductionFactor(
    completedTasks: List<PostProductionTask>,
    shootHours: Double,
): Double? {
    if (shootHours <= 0.0) return null

    val measured = completedTasks.filter { it.isMeasured }
    if (measured.size < MINIMUM_TASKS_TO_MEASURE) return null

    val postHours = measured.sumOf { it.actualHours ?: 0.0 }

    return (postHours / shootHours).takeIf { it > 0.0 }
}

private const val HOURS_IN_WORKING_DAY = 8.0

internal fun buildMoneyOwed(
    invoices: List<Invoice>,
    projects: List<Project>,
    clients: List<Client>,
    now: Instant,
    currency: CurrencyCode,
): MoneyOwedSummary {
    val projectsById = projects.associateBy { it.id }
    val clientsById = clients.associateBy { it.id }

    val outstanding =
        invoices
            .filter { it.paymentState(now).isOutstanding }
            // Overdue first, then by how late — the order to work the list in.
            .sortedWith(
                compareByDescending<Invoice> { it.paymentState(now) == PaymentState.Overdue }
                    .thenBy { it.dueAt ?: Instant.DISTANT_FUTURE },
            )

    val items =
        outstanding.map { invoice ->
            val project = projectsById[invoice.projectId]
            val overdue = invoice.overdueBy(now)

            OutstandingInvoiceItem(
                id = invoice.id,
                number = invoice.number,
                clientName = project?.let { clientsById[it.clientId]?.displayName }.orEmpty(),
                projectName = project?.name.orEmpty(),
                balanceDue = invoice.balanceDue.display(),
                balanceDuePlain = invoice.balanceDue.toPlainString(),
                state = invoice.paymentState(now),
                overdueDays = overdue?.inWholeDays,
                dueLabel = invoice.dueAt?.let { DateFormats.shortDate(it, TimeZone.currentSystemDefault()) },
                canVoid = invoice.payments.isEmpty(),
            )
        }

    val overdueInvoices = outstanding.filter { it.paymentState(now) == PaymentState.Overdue }

    // Money in two currencies cannot be added, and `Money` refuses to try. A studio that
    // has changed what it charges in still has invoices in the old one, so the totals
    // cover what matches and the count of what does not is reported rather than dropped —
    // a total quietly missing an invoice is worse than one that says it is short.
    // `buildExpenseSummary` and `buildProposals` have filtered this way from the start;
    // this one did not, which went unnoticed while every figure in the application was
    // hardcoded to dollars.
    fun List<Invoice>.inStudioCurrency(): List<Invoice> = filter { it.currency == currency }

    // Oldest first: a draft that has sat longest is work agreed longest ago and still not
    // billed. Without this list a draft is invisible — it contributes nothing to money
    // owed, which is the whole point of a draft, and accepting a quote raises one.
    val drafts =
        invoices
            .filter { it.status == InvoiceStatus.Draft }
            .sortedBy { it.audit.createdAt }
            .map { invoice ->
                val project = projectsById[invoice.projectId]

                DraftInvoiceItem(
                    id = invoice.id,
                    number = invoice.number,
                    clientName = project?.let { clientsById[it.clientId]?.displayName }.orEmpty(),
                    projectName = project?.name.orEmpty(),
                    total = invoice.total.display(),
                    raisedLabel = raisedLabel(invoice.audit.createdAt, now),
                )
            }

    return MoneyOwedSummary(
        totalOutstanding =
            outstanding
                .inStudioCurrency()
                .map { it.outstanding(now) }
                .sum(currency)
                .display(),
        overdueAmount =
            overdueInvoices
                .inStudioCurrency()
                .map { it.outstanding(now) }
                .sum(currency)
                .display(),
        otherCurrencyCount = outstanding.count { it.currency != currency },
        overdueCount = overdueInvoices.size,
        invoices = items,
        drafts = drafts,
    )
}

/** How long an invoice has been sitting unsent, in the terms a studio thinks in. */
private fun raisedLabel(
    since: Instant,
    now: Instant,
): String {
    val days = (now - since).inWholeDays

    return when {
        days <= 0L -> "raised today"
        days == 1L -> "raised yesterday"
        days < 14L -> "raised $days days ago"
        days < 60L -> "raised ${days / 7} weeks ago"
        else -> "raised ${days / 30} months ago"
    }
}

internal fun buildPricing(
    breakdown: CodbBreakdown,
    templates: List<ServiceTemplate>,
    /** Measured from finished post-production, or null while the assumption still stands. */
    measuredFactor: Double? = null,
): PricingSummary =
    PricingSummary(
        postProductionFactor = measuredFactor ?: ASSUMED_POST_PRODUCTION_FACTOR,
        isFactorMeasured = measuredFactor != null,
        costPerBillableDay = breakdown.costPerBillableDay.display(),
        annualOverhead = breakdown.annualOverhead.display(),
        targetSalary = breakdown.targetSalary.display(),
        taxAllowance = breakdown.taxAllowance.display(),
        totalAnnualRequirement = breakdown.totalAnnualRequirement.display(),
        billableDaysPerYear = breakdown.billableDaysPerYear,
        packages =
            templates.map { template ->
                val days = template.estimatedDaysConsumed(measuredFactor ?: ASSUMED_POST_PRODUCTION_FACTOR)
                val price = template.basePrice

                if (price == null) {
                    PackagePricing(
                        name = template.name,
                        serviceLine = template.serviceLine.name,
                        price = "—",
                        minimumPrice = breakdown.minimumPriceFor(days).display(),
                        difference = "—",
                        estimatedDays = days.dayLabel(),
                        isBelowCost = false,
                        hasPrice = false,
                    )
                } else {
                    val assessment = breakdown.assess(price, days)

                    PackagePricing(
                        name = template.name,
                        serviceLine = template.serviceLine.name,
                        price = price.display(),
                        minimumPrice = assessment.minimumPrice.display(),
                        difference = assessment.difference.displaySigned(),
                        estimatedDays = days.dayLabel(),
                        isBelowCost = assessment.isBelowCost,
                        hasPrice = true,
                    )
                }
            },
    )

internal fun buildExpenseSummary(
    expenses: List<Expense>,
    mileage: List<Mileage>,
    year: Int,
    currency: CurrencyCode,
): ExpenseSummary {
    val inCurrency = expenses.filter { it.amount.currency == currency }

    return ExpenseSummary(
        year = year,
        overheadTotal =
            inCurrency
                .filter(Expense::isOverhead)
                .map(Expense::amount)
                .sum(currency)
                .display(),
        jobCostTotal =
            inCurrency
                .filter(Expense::isJobCost)
                .map(Expense::amount)
                .sum(currency)
                .display(),
        mileageDeduction =
            mileage
                .filter { it.ratePerUnit.currency == currency }
                .map(Mileage::deductibleAmount)
                .sum(currency)
                .display(),
        recorded = expenses.size,
    )
}

/**
 * Estimates how much sellable time a package consumes, shoot plus everything after it.
 *
 * Deliberately visible in the UI rather than buried: a floor computed from a hidden
 * assumption is a floor nobody should trust.
 */
internal fun ServiceTemplate.estimatedDaysConsumed(postProductionFactor: Double): Double {
    val shootDays =
        (defaultSessionCount * defaultSessionDurationMinutes / 60.0 / HOURS_IN_WORKING_DAY)
            .coerceAtLeast(0.5)

    return shootDays * (1 + postProductionFactor)
}

private fun Double.dayLabel(): String {
    val rounded = (this * 10).toLong() / 10.0
    return if (rounded == 1.0) "1 day" else "$rounded days"
}

internal fun Money.display(): String = formatted()

private fun Money.displaySigned(): String = if (isNegative) display() else "+${display()}"
