package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import kotlin.time.Instant

/**
 * The starting templates installed on first run — one per business line the studio runs.
 *
 * These exist to prove the design rather than to prescribe prices: the four lines differ
 * only in these values, not in schema or code. Every figure here is a placeholder the
 * studio is expected to replace once its cost of doing business is known.
 */
internal fun defaultServiceTemplates(
    studioId: StudioId,
    currency: CurrencyCode,
    now: Instant,
): List<ServiceTemplate> {
    val audit = AuditMetadata.createdAt(now)

    fun template(
        name: String,
        line: ServiceLine,
        durationMinutes: Int,
        sessionCount: Int,
        price: Long?,
        deliverables: Int?,
        turnaroundDays: Int?,
        revisions: Int?,
        notes: String,
    ) = ServiceTemplate(
        id = ServiceTemplateId.new(),
        studioId = studioId,
        name = name,
        serviceLine = line,
        defaultSessionDurationMinutes = durationMinutes,
        defaultSessionCount = sessionCount,
        basePrice = price?.let { Money.ofMajor(it, currency) },
        defaultDeliverableCount = deliverables,
        defaultTurnaroundDays = turnaroundDays,
        defaultRevisionRounds = revisions,
        notes = notes,
        audit = audit,
    )

    return listOf(
        template(
            name = "Wedding — Full Day",
            line = ServiceLine.Wedding,
            durationMinutes = 10 * 60,
            // Two sessions: the engagement shoot and the wedding day. A wedding is one
            // booking containing both, which is why Project and Session are separate.
            sessionCount = 2,
            price = 4_500,
            deliverables = 600,
            turnaroundDays = 42,
            revisions = null,
            notes = "Includes engagement session. Second shooter costed as a project expense.",
        ),
        template(
            name = "Brand Video — Single Day",
            line = ServiceLine.Video,
            durationMinutes = 8 * 60,
            // Scout, shoot, pickup.
            sessionCount = 3,
            price = 6_000,
            deliverables = 3,
            turnaroundDays = 21,
            // Bounded revision rounds are the main defence against scope creep on video.
            revisions = 2,
            notes = "Deliver 16:9, 9:16, and 1:1. Usage licence agreed separately.",
        ),
        template(
            name = "Real Estate — Standard Listing",
            line = ServiceLine.RealEstate,
            durationMinutes = 90,
            sessionCount = 1,
            price = 350,
            deliverables = 30,
            // Listings are time-critical; turnaround is the product.
            turnaroundDays = 1,
            revisions = 1,
            notes = "Confirm access and lighting conditions with the agent before arrival.",
        ),
        template(
            name = "Headshots — Personal Branding",
            line = ServiceLine.Headshot,
            durationMinutes = 90,
            sessionCount = 1,
            price = 450,
            deliverables = 5,
            turnaroundDays = 7,
            revisions = 1,
            notes = "Retouching included on selected frames only.",
        ),
    )
}
