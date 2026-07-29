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
 * How much of a working day a shoot day really consumes.
 *
 * A shoot is rarely just the shoot: culling, editing, colour, album design, and client
 * admin typically take two to three times as long as the time spent with a camera. This
 * factor exists so the pricing floor reflects the whole job rather than the visible part
 * of it.
 *
 * It is an assumption, and the screen shows it as one. It becomes a measurement rather
 * than a guess when post-production hours are tracked in the Pipeline milestone.
 */
private const val POST_PRODUCTION_FACTOR = 2.0

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
        totalOutstanding = outstanding.map { it.outstanding(now) }.sum(currency).display(),
        overdueAmount = overdueInvoices.map { it.outstanding(now) }.sum(currency).display(),
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
): PricingSummary =
    PricingSummary(
        costPerBillableDay = breakdown.costPerBillableDay.display(),
        annualOverhead = breakdown.annualOverhead.display(),
        targetSalary = breakdown.targetSalary.display(),
        taxAllowance = breakdown.taxAllowance.display(),
        totalAnnualRequirement = breakdown.totalAnnualRequirement.display(),
        billableDaysPerYear = breakdown.billableDaysPerYear,
        packages =
            templates.map { template ->
                val days = template.estimatedDaysConsumed()
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
internal fun ServiceTemplate.estimatedDaysConsumed(): Double {
    val shootDays =
        (defaultSessionCount * defaultSessionDurationMinutes / 60.0 / HOURS_IN_WORKING_DAY)
            .coerceAtLeast(0.5)

    return shootDays * (1 + POST_PRODUCTION_FACTOR)
}

private fun Double.dayLabel(): String {
    val rounded = (this * 10).toLong() / 10.0
    return if (rounded == 1.0) "1 day" else "$rounded days"
}

internal fun Money.display(): String = formatted()

private fun Money.displaySigned(): String = if (isNegative) display() else "+${display()}"
