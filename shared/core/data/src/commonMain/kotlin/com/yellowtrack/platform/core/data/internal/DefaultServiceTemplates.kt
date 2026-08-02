package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import kotlin.time.Instant

/**
 * The starting templates installed on first run — one per business line the studio runs.
 *
 * Deliberately priceless. Duration, session count, deliverables and turnaround are facts
 * about the shape of the work and are the same in every country; a price is not. Seeding
 * one would mean inventing a figure, denominating it in a currency the studio has not
 * chosen yet, and then measuring it against the studio's real pricing floor — which would
 * report a made-up package as under- or over-priced as though it meant something.
 *
 * The Ledger already handles a template with no price: it shows the minimum the floor
 * requires for those days and leaves what the studio charges blank, which is the honest
 * order to fill them in.
 */
internal fun defaultServiceTemplates(
    studioId: StudioId,
    now: Instant,
): List<ServiceTemplate> {
    val audit = AuditMetadata.createdAt(now)

    fun template(
        name: String,
        line: ServiceLine,
        durationMinutes: Int,
        sessionCount: Int,
        deliverables: Int?,
        turnaroundDays: Int?,
        revisions: Int?,
        notes: String,
    ) = ServiceTemplate(
        id = defaultTemplateId(studioId, name),
        studioId = studioId,
        name = name,
        serviceLine = line,
        defaultSessionDurationMinutes = durationMinutes,
        defaultSessionCount = sessionCount,
        basePrice = null,
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
            deliverables = 5,
            turnaroundDays = 7,
            revisions = 1,
            notes = "Retouching included on selected frames only.",
        ),
    )
}

/**
 * Test-visible alias.
 *
 * The builder is `internal` to `core:data` and lives in `internal`, which the module's own
 * tests cannot import across packages. This is the seam rather than widening the builder.
 */
internal fun defaultServiceTemplatesForStudio(
    studioId: StudioId,
    now: Instant,
): List<ServiceTemplate> = defaultServiceTemplates(studioId, now)

/**
 * The id a default template has on every device, rather than a fresh one on each.
 *
 * Seeding runs once per device, so a generated id gave two devices two full sets of the
 * same four templates and the studio saw everything twice. Derived from the studio and the
 * template's name, both devices write the same four rows and reconciliation settles them.
 *
 * Readable rather than a UUID on purpose: this id says why it is what it is, which matters
 * when somebody is looking at a `service_template` row wondering where it came from. It is
 * also what migration 16 has to reproduce in SQL, and a hash would not be reproducible
 * there without shipping a hash function into SQLite.
 *
 * A template the studio renames keeps whatever id it already had. That is correct — it has
 * stopped being one of ours and become one of theirs.
 */
internal fun defaultTemplateId(
    studioId: StudioId,
    name: String,
): ServiceTemplateId = ServiceTemplateId("${studioId.value}:default:$name")
