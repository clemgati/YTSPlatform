package com.yellowtrack.platform.feature.ledger.presentation.mapper

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.money.editableAmount
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
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.feature.ledger.presentation.model.CostEdit
import com.yellowtrack.platform.feature.ledger.presentation.model.DraftInvoiceItem
import com.yellowtrack.platform.feature.ledger.presentation.model.ExpenseSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.MoneyOwedSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.NewExpense
import com.yellowtrack.platform.feature.ledger.presentation.model.NewInvoice
import com.yellowtrack.platform.feature.ledger.presentation.model.NewMileage
import com.yellowtrack.platform.feature.ledger.presentation.model.NewServiceTemplate
import com.yellowtrack.platform.feature.ledger.presentation.model.OutstandingInvoiceItem
import com.yellowtrack.platform.feature.ledger.presentation.model.PackagePricing
import com.yellowtrack.platform.feature.ledger.presentation.model.PricingSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.ReceivedPayment
import com.yellowtrack.platform.feature.ledger.presentation.model.RecordedCost
import com.yellowtrack.platform.feature.ledger.presentation.model.SendTo
import com.yellowtrack.platform.feature.ledger.presentation.model.toForm
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
                sendTo = project?.let { clientsById[it.clientId] }?.toSendTo(),
                emailedLabel =
                    invoice.lastEmailedAt?.let { at ->
                        "Emailed to ${invoice.lastEmailedTo.orEmpty()} on " +
                            DateFormats.shortDate(at, TimeZone.currentSystemDefault())
                    },
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
                    editable = invoice.toForm(),
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
        // From every invoice, not only the outstanding ones. A payment put against the
        // wrong invoice settles it, and a settled invoice is not in `items` — so listing
        // only those would hide exactly the payment somebody is looking for.
        received =
            invoices
                .flatMap { invoice -> invoice.payments.map { invoice to it } }
                .sortedByDescending { (_, payment) -> payment.paidAt }
                .map { (invoice, payment) ->
                    ReceivedPayment(
                        id = payment.id,
                        date = DateFormats.fullDate(payment.paidAt, TimeZone.currentSystemDefault()),
                        amount = payment.amount.display(),
                        against =
                            "Invoice ${invoice.number} — " +
                                (
                                    projectsById[invoice.projectId]
                                        ?.let { clientsById[it.clientId]?.accountName }
                                        ?: "unknown client"
                                ),
                        method = payment.method.name,
                        reference = payment.reference,
                    )
                },
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

/**
 * The studio's packages, with the floor comparison when there is a floor.
 *
 * Built apart from [buildPricing] because a package exists whether or not a pricing basis
 * has been stated. Folded into the pricing summary, as it was, the entire list vanished
 * until a studio entered a salary target — so the four seeded packages could not be seen,
 * corrected or removed by exactly the studio that had just installed the application.
 */
internal fun buildPackages(
    templates: List<ServiceTemplate>,
    breakdown: CodbBreakdown?,
    measuredFactor: Double? = null,
): List<PackagePricing> {
    val factor = measuredFactor ?: ASSUMED_POST_PRODUCTION_FACTOR

    return templates.map { template ->
        val days = template.estimatedDaysConsumed(factor)
        val price = template.basePrice
        val assessment = if (price != null && breakdown != null) breakdown.assess(price, days) else null

        PackagePricing(
            id = template.id.value,
            name = template.name,
            serviceLine = template.serviceLine.name,
            price = price?.display() ?: "—",
            minimumPrice = breakdown?.minimumPriceFor(days)?.display(),
            difference = assessment?.difference?.displaySigned(),
            estimatedDays = days.dayLabel(),
            isBelowCost = assessment?.isBelowCost == true,
            hasPrice = price != null,
            editable = template.toForm(),
        )
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
        profitAllowance = breakdown.profitAllowance.takeIf { it.isPositive }?.display(),
        totalAnnualRequirement = breakdown.totalAnnualRequirement.display(),
        billableDaysPerYear = breakdown.billableDaysPerYear,
    )

internal fun buildExpenseSummary(
    expenses: List<Expense>,
    mileage: List<Mileage>,
    year: Int,
    currency: CurrencyCode,
    /** Booking labels, so a cost says which job it came out of rather than showing an id. */
    projectNames: Map<ProjectId, String> = emptyMap(),
): ExpenseSummary {
    val inCurrency = expenses.filter { it.amount.currency == currency }

    fun allocation(projectId: ProjectId?): String = projectId?.let { projectNames[it] ?: "A booking" } ?: "Overhead"

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
        // Newest first: the reason to open this is almost always to check what was just
        // entered, or to find the thing entered twice.
        items =
            (
                inCurrency.map { expense ->
                    expense.incurredOn to
                        RecordedCost(
                            id = expense.id.value,
                            date = DateFormats.shortDate(expense.incurredOn),
                            description = expense.description,
                            amount = expense.amount.display(),
                            allocation = allocation(expense.projectId),
                            // Seeded from the cost as recorded, so opening it shows what was
                            // entered rather than an empty form to type again.
                            editable =
                                CostEdit.OfExpense(
                                    NewExpense(
                                        description = expense.description,
                                        amount = expense.amount.editableAmount(),
                                        category = expense.category,
                                        incurredOn = expense.incurredOn.toString(),
                                        projectId = expense.projectId,
                                        vendor = expense.vendor,
                                        isTaxDeductible = expense.isTaxDeductible,
                                    ),
                                ),
                        )
                } +
                    mileage.filter { it.ratePerUnit.currency == currency }.map { journey ->
                        journey.travelledOn to
                            RecordedCost(
                                id = journey.id.value,
                                date = DateFormats.shortDate(journey.travelledOn),
                                // The purpose is what a studio wrote down; without one the
                                // distance is the only honest description of the journey.
                                description =
                                    journey.purpose?.takeIf { it.isNotBlank() }
                                        ?: "${journey.distance} ${journey.unit.name.lowercase()}",
                                amount = journey.deductibleAmount.display(),
                                allocation = allocation(journey.projectId),
                                editable =
                                    CostEdit.OfJourney(
                                        NewMileage(
                                            travelledOn = journey.travelledOn.toString(),
                                            distance = journey.distance.toString(),
                                            unit = journey.unit,
                                            ratePerUnit = journey.ratePerUnit.editableAmount(),
                                            projectId = journey.projectId,
                                            purpose = journey.purpose,
                                            fromLocation = journey.fromLocation,
                                            toLocation = journey.toLocation,
                                        ),
                                    ),
                            )
                    }
            )
                // Sorted on the date itself, never on the rendering of it: "Jul 21" sorts
                // before "Mar 3" alphabetically, which would put the year in the wrong order
                // and look entirely plausible doing it.
                .sortedByDescending { (date, _) -> date }
                .map { (_, cost) -> cost },
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

/**
 * A draft invoice's own values, as the form holds them.
 *
 * `sendNow` comes back false whatever the draft was raised as. It is not a property of the
 * invoice but an instruction about what to do on saving, and a correction that re-issued
 * the document because the tick was remembered from last time would be a demand nobody
 * meant to send.
 */
private fun Invoice.toForm(): NewInvoice =
    NewInvoice(
        number = number,
        projectId = projectId,
        kind = kind,
        lines = lines.map { it.toForm() },
        dueOn =
            dueAt
                ?.toLocalDateTime(TimeZone.currentSystemDefault())
                ?.date
                ?.toString()
                .orEmpty(),
        sendNow = false,
    )

/**
 * The package's own values, as the form holds them.
 *
 * Digits rather than renderings: a form opening on "£1,240.00" makes somebody delete
 * punctuation before they can change a figure.
 */
private fun ServiceTemplate.toForm() =
    NewServiceTemplate(
        name = name,
        serviceLine = serviceLine,
        sessionDurationMinutes = defaultSessionDurationMinutes.toString(),
        sessionCount = defaultSessionCount.toString(),
        basePrice = basePrice?.toPlainString().orEmpty(),
        deliverableCount = defaultDeliverableCount?.toString().orEmpty(),
        turnaroundDays = defaultTurnaroundDays?.toString().orEmpty(),
        revisionRounds = defaultRevisionRounds?.toString().orEmpty(),
        notes = notes.orEmpty(),
    )

private fun Double.dayLabel(): String {
    val rounded = (this * 10).toLong() / 10.0
    return if (rounded == 1.0) "1 day" else "$rounded days"
}

internal fun Money.display(): String = formatted()

private fun Money.displaySigned(): String = if (isNegative) display() else "+${display()}"

/**
 * Where a document would be emailed, and to whom.
 *
 * The billing contact rather than the primary one, which is the model's own distinction: on a
 * wedding the person who booked and the person who pays are often different people, and an
 * invoice sent to the wrong one of those is an awkward conversation the studio did not choose.
 *
 * Null when there is no address to offer. An empty box is the honest prompt then, rather than
 * a name with nothing behind it.
 */
internal fun Client.toSendTo(): SendTo? =
    billingContact?.emails?.firstOrNull()?.value?.takeIf { it.isNotBlank() }?.let { address ->
        SendTo(name = billingContact?.displayName.orEmpty().ifBlank { displayName }, email = address)
    }
