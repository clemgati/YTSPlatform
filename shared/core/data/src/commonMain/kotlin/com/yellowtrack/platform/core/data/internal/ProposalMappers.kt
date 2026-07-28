package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.ContractStatus
import com.yellowtrack.platform.core.model.contract.UsageLicense
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import com.yellowtrack.platform.core.database.Contract as ContractRow
import com.yellowtrack.platform.core.database.Quote as QuoteRow

/**
 * The usage licence is stored as JSON, on the same reasoning as invoice lines: it is a
 * value object read only with its contract and never queried independently.
 */
internal fun encodeUsageLicense(license: UsageLicense?): String? = license?.let(ledgerJson::encodeToString)

/** A licence written by a newer version must not crash an older one — see [decodeLines]. */
internal fun decodeUsageLicense(raw: String?): UsageLicense? =
    raw?.let { runCatching { ledgerJson.decodeFromString<UsageLicense>(it) }.getOrNull() }

internal fun QuoteRow.toDomain(): Quote =
    Quote(
        id = QuoteId(id),
        studioId = StudioId(studio_id),
        projectId = ProjectId(project_id),
        number = number,
        status = enumOrDefault(status, QuoteStatus.Draft),
        currency = CurrencyCode(currency),
        lines = decodeLines(lines),
        issuedAt = issued_at.toInstantOrNull(),
        validUntil = valid_until.toInstantOrNull(),
        acceptedAt = accepted_at.toInstantOrNull(),
        declinedAt = declined_at.toInstantOrNull(),
        notes = notes,
        terms = terms,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )

internal fun ContractRow.toDomain(): Contract =
    Contract(
        id = ContractId(id),
        studioId = StudioId(studio_id),
        projectId = ProjectId(project_id),
        title = title,
        status = enumOrDefault(status, ContractStatus.Draft),
        sentAt = sent_at.toInstantOrNull(),
        signedAt = signed_at.toInstantOrNull(),
        signerName = signer_name,
        signerEmail = signer_email,
        retainerAmount = moneyOf(retainer_minor, retainer_currency),
        isRetainerRefundable = is_retainer_refundable != 0L,
        turnaroundDays = turnaround_days?.toInt(),
        revisionRounds = revision_rounds?.toInt(),
        cancellationTerms = cancellation_terms,
        rescheduleTerms = reschedule_terms,
        weatherClause = weather_clause,
        usageLicense = decodeUsageLicense(usage_license),
        documentReference = document_reference,
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
