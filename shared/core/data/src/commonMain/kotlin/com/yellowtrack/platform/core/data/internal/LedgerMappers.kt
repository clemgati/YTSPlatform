package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.codb.CodbProfile
import com.yellowtrack.platform.core.model.codb.CodbProfileId
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.expense.DistanceUnit
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.ExpenseCategory
import com.yellowtrack.platform.core.model.expense.ExpenseId
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.expense.MileageId
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.invoice.PaymentMethod
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.core.model.lead.LeadSource
import com.yellowtrack.platform.core.model.lead.LeadStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.service.ServiceLine
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import com.yellowtrack.platform.core.database.Codb_profile as CodbRow
import com.yellowtrack.platform.core.database.Expense as ExpenseRow
import com.yellowtrack.platform.core.database.Invoice as InvoiceRow
import com.yellowtrack.platform.core.database.Lead as LeadRow
import com.yellowtrack.platform.core.database.Mileage as MileageRow
import com.yellowtrack.platform.core.database.Payment as PaymentRow

internal val ledgerJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

internal fun encodeLines(lines: List<LineItem>): String = ledgerJson.encodeToString(lines)

/**
 * A malformed lines column yields an empty document rather than throwing.
 *
 * A row written by a newer version of the app must not crash an older one — that is a
 * real scenario once devices synchronise, and showing a zero total is recoverable where
 * a crash on the invoice list is not.
 */
internal fun decodeLines(raw: String): List<LineItem> =
    runCatching { ledgerJson.decodeFromString<List<LineItem>>(raw) }.getOrDefault(emptyList())

internal fun LeadRow.toDomain(): Lead =
    Lead(
        id = LeadId(id),
        studioId = StudioId(studio_id),
        name = name,
        source = enumOrDefault(source, LeadSource.Other),
        status = enumOrDefault(status, LeadStatus.New),
        receivedAt = received_at.toInstant(),
        email = email,
        phone = phone,
        firstResponseAt = first_response_at.toInstantOrNull(),
        serviceLine = service_line?.let { enumOrDefault(it, ServiceLine.Other) },
        desiredDate = desired_date?.let(LocalDate::parse),
        budgetLow = moneyOf(budget_low_minor, budget_currency),
        budgetHigh = moneyOf(budget_high_minor, budget_currency),
        referredBy = referred_by,
        lostReason = lost_reason,
        convertedProjectId = converted_project_id?.let(::ProjectId),
        convertedClientId = converted_client_id?.let(::ClientId),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )

internal fun InvoiceRow.toDomain(payments: List<Payment>): Invoice =
    Invoice(
        id = InvoiceId(id),
        studioId = StudioId(studio_id),
        projectId = ProjectId(project_id),
        number = number,
        kind = enumOrDefault(kind, InvoiceKind.Full),
        status = enumOrDefault(status, InvoiceStatus.Draft),
        currency = CurrencyCode(currency),
        lines = decodeLines(lines),
        payments = payments,
        issuedAt = issued_at.toInstantOrNull(),
        dueAt = due_at.toInstantOrNull(),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )

internal fun PaymentRow.toDomain(): Payment =
    Payment(
        id = PaymentId(id),
        studioId = StudioId(studio_id),
        invoiceId = InvoiceId(invoice_id),
        amount = Money(amount_minor, CurrencyCode(amount_currency)),
        paidAt = paid_at.toInstant(),
        method = enumOrDefault(method, PaymentMethod.Other),
        reference = reference,
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )

internal fun ExpenseRow.toDomain(): Expense =
    Expense(
        id = ExpenseId(id),
        studioId = StudioId(studio_id),
        category = enumOrDefault(category, ExpenseCategory.Other),
        description = description,
        amount = Money(amount_minor, CurrencyCode(amount_currency)),
        incurredOn = LocalDate.parse(incurred_on),
        projectId = project_id?.let(::ProjectId),
        vendor = vendor,
        isTaxDeductible = is_tax_deductible != 0L,
        receiptReference = receipt_reference,
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )

internal fun MileageRow.toDomain(): Mileage =
    Mileage(
        id = MileageId(id),
        studioId = StudioId(studio_id),
        travelledOn = LocalDate.parse(travelled_on),
        distance = distance,
        unit = enumOrDefault(unit, DistanceUnit.Miles),
        ratePerUnit = Money(rate_minor, CurrencyCode(rate_currency)),
        projectId = project_id?.let(::ProjectId),
        purpose = purpose,
        fromLocation = from_location,
        toLocation = to_location,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )

internal fun CodbRow.toDomain(): CodbProfile {
    val currencyCode = CurrencyCode(currency)

    return CodbProfile(
        id = CodbProfileId(id),
        studioId = StudioId(studio_id),
        currency = currencyCode,
        targetAnnualSalary = Money(target_annual_salary_minor, currencyCode),
        billableDaysPerYear = billable_days_per_year.toInt(),
        taxRateBasisPoints = tax_rate_basis_points.toInt(),
        annualOverheadOverride = annual_overhead_minor?.let { Money(it, currencyCode) },
        desiredProfitMarginBasisPoints = profit_margin_basis_points.toInt(),
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
}
