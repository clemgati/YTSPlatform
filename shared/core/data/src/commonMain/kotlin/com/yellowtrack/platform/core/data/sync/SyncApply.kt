package com.yellowtrack.platform.core.data.sync

import com.yellowtrack.platform.core.database.YellowTrackDatabase
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientContactLink
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.sync.SyncConflict
import kotlinx.serialization.json.Json

/**
 * Writes a row that arrived from the server.
 *
 * Deliberately *not* routed through the repositories. Those enqueue to the outbox, and a
 * pulled row queued straight back for upload would have two devices pushing the same row at
 * each other for as long as both were running.
 *
 * Each of these is an insert-or-update rather than a merge. Reconciliation has already
 * happened on the server, which is the only party that saw both versions; the device's job
 * at this point is to agree, not to have a second opinion.
 *
 * Every one writes `deleted_at` as it arrived, so a tombstone lands as a tombstone. A
 * delete that failed to travel is a row that comes back from the dead on the next sync.
 */
internal suspend fun YellowTrackDatabase.applyClient(client: Client) {
    clientQueries.insertOrIgnore(
        id = client.id.value,
        studio_id = client.studioId.value,
        account_name = client.accountName,
        account_type = client.accountType.name,
        notes = client.notes,
        tags = syncJson.encodeToString(client.tags),
        created_at = client.audit.createdAt.toEpochMilliseconds(),
        updated_at = client.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = client.audit.deletedAt?.toEpochMilliseconds(),
        version = client.audit.version.toLong(),
    )

    clientQueries.update(
        accountName = client.accountName,
        accountType = client.accountType.name,
        notes = client.notes,
        tags = syncJson.encodeToString(client.tags),
        updatedAt = client.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = client.audit.deletedAt?.toEpochMilliseconds(),
        version = client.audit.version.toLong(),
        id = client.id.value,
    )
}

internal suspend fun YellowTrackDatabase.applyContact(contact: Contact) {
    contactQueries.insertOrIgnore(
        id = contact.id.value,
        studio_id = contact.studioId.value,
        first_name = contact.firstName,
        last_name = contact.lastName,
        company = contact.company,
        job_title = contact.jobTitle,
        emails = syncJson.encodeToString(contact.emails),
        phones = syncJson.encodeToString(contact.phones),
        notes = contact.notes,
        created_at = contact.audit.createdAt.toEpochMilliseconds(),
        updated_at = contact.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = contact.audit.deletedAt?.toEpochMilliseconds(),
        version = contact.audit.version.toLong(),
    )

    contactQueries.update(
        firstName = contact.firstName,
        lastName = contact.lastName,
        company = contact.company,
        jobTitle = contact.jobTitle,
        emails = syncJson.encodeToString(contact.emails),
        phones = syncJson.encodeToString(contact.phones),
        notes = contact.notes,
        updatedAt = contact.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = contact.audit.deletedAt?.toEpochMilliseconds(),
        version = contact.audit.version.toLong(),
        id = contact.id.value,
    )
}

/**
 * The attachment of a person to an account.
 *
 * Written after its client and its contact — see the apply order in `SyncEngine`. The row
 * carries a foreign key to both, and a studio's first sync is exactly when all three are new.
 */
internal suspend fun YellowTrackDatabase.applyClientContactLink(link: ClientContactLink) {
    clientQueries.insertOrIgnoreClientContact(
        id = link.id.value,
        studio_id = link.studioId.value,
        client_id = link.clientId.value,
        contact_id = link.contactId.value,
        role = link.role.name,
        created_at = link.audit.createdAt.toEpochMilliseconds(),
        updated_at = link.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = link.audit.deletedAt?.toEpochMilliseconds(),
        version = link.audit.version.toLong(),
    )

    clientQueries.updateClientContact(
        role = link.role.name,
        updatedAt = link.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = link.audit.deletedAt?.toEpochMilliseconds(),
        version = link.audit.version.toLong(),
        id = link.id.value,
    )
}

internal suspend fun YellowTrackDatabase.applyProject(project: Project) {
    projectQueries.insertOrIgnore(
        id = project.id.value,
        studio_id = project.studioId.value,
        client_id = project.clientId.value,
        name = project.name,
        service_line = project.serviceLine.name,
        status = project.status.name,
        service_template_id = project.serviceTemplateId?.value,
        contract_value_minor = project.contractValue?.minorUnits,
        contract_currency = project.contractValue?.currency?.code,
        enquired_at = project.enquiredAt?.toEpochMilliseconds(),
        booked_at = project.bookedAt?.toEpochMilliseconds(),
        notes = project.notes,
        created_at = project.audit.createdAt.toEpochMilliseconds(),
        updated_at = project.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = project.audit.deletedAt?.toEpochMilliseconds(),
        version = project.audit.version.toLong(),
    )

    projectQueries.update(
        clientId = project.clientId.value,
        name = project.name,
        serviceLine = project.serviceLine.name,
        status = project.status.name,
        serviceTemplateId = project.serviceTemplateId?.value,
        contractValueMinor = project.contractValue?.minorUnits,
        contractCurrency = project.contractValue?.currency?.code,
        enquiredAt = project.enquiredAt?.toEpochMilliseconds(),
        bookedAt = project.bookedAt?.toEpochMilliseconds(),
        notes = project.notes,
        updatedAt = project.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = project.audit.deletedAt?.toEpochMilliseconds(),
        version = project.audit.version.toLong(),
        id = project.id.value,
    )
}

internal suspend fun YellowTrackDatabase.applySession(session: Session) {
    sessionQueries.insertOrIgnore(
        id = session.id.value,
        studio_id = session.studioId.value,
        project_id = session.projectId.value,
        title = session.title,
        kind = session.kind.name,
        status = session.status.name,
        starts_at = session.startsAt.toEpochMilliseconds(),
        ends_at = session.endsAt.toEpochMilliseconds(),
        time_zone_id = session.timeZoneId,
        location_name = session.locationName,
        location_address = session.locationAddress,
        call_time = session.callTime?.toEpochMilliseconds(),
        notes = session.notes,
        created_at = session.audit.createdAt.toEpochMilliseconds(),
        updated_at = session.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = session.audit.deletedAt?.toEpochMilliseconds(),
        version = session.audit.version.toLong(),
        latitude = session.coordinates?.latitude,
        longitude = session.coordinates?.longitude,
    )

    sessionQueries.update(
        projectId = session.projectId.value,
        title = session.title,
        kind = session.kind.name,
        status = session.status.name,
        startsAt = session.startsAt.toEpochMilliseconds(),
        endsAt = session.endsAt.toEpochMilliseconds(),
        timeZoneId = session.timeZoneId,
        locationName = session.locationName,
        locationAddress = session.locationAddress,
        latitude = session.coordinates?.latitude,
        longitude = session.coordinates?.longitude,
        callTime = session.callTime?.toEpochMilliseconds(),
        notes = session.notes,
        updatedAt = session.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = session.audit.deletedAt?.toEpochMilliseconds(),
        version = session.audit.version.toLong(),
        id = session.id.value,
    )
}

/**
 * Records work reconciliation discarded.
 *
 * Insert-or-ignore rather than upsert: a conflict is a thing that happened at a moment,
 * and the same one arriving twice is the same event rather than a newer version of it.
 * Overwriting would also wipe a `resolved_at` set locally by somebody who had already dealt
 * with it.
 */
internal suspend fun YellowTrackDatabase.applyConflict(conflict: SyncConflict) {
    syncQueries.insertOrIgnoreConflict(
        id = conflict.id.value,
        studio_id = conflict.studioId.value,
        entity_table = conflict.entityTable,
        entity_id = conflict.entityId,
        losing_payload = conflict.losingPayload,
        winning_payload = conflict.winningPayload,
        detected_at = conflict.detectedAt.toEpochMilliseconds(),
        resolved_at = conflict.resolvedAt?.toEpochMilliseconds(),
        created_at = conflict.audit.createdAt.toEpochMilliseconds(),
        updated_at = conflict.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = conflict.audit.deletedAt?.toEpochMilliseconds(),
        version = conflict.audit.version.toLong(),
    )
}

/** Matches how the repositories already store list columns, so a synced row reads the same. */
private val syncJson = Json

/**
 * An invoice, without its payments — those arrive as their own rows.
 *
 * `lines` is written as it arrived, because it is a JSON column on the invoice rather than
 * rows of its own, and so reconciles with the document rather than by union.
 */
internal suspend fun YellowTrackDatabase.applyInvoice(invoice: Invoice) {
    invoiceQueries.insertOrIgnore(
        id = invoice.id.value,
        studio_id = invoice.studioId.value,
        project_id = invoice.projectId.value,
        number = invoice.number,
        kind = invoice.kind.name,
        status = invoice.status.name,
        currency = invoice.currency.code,
        lines = syncJson.encodeToString(invoice.lines),
        issued_at = invoice.issuedAt?.toEpochMilliseconds(),
        due_at = invoice.dueAt?.toEpochMilliseconds(),
        notes = invoice.notes,
        created_at = invoice.audit.createdAt.toEpochMilliseconds(),
        updated_at = invoice.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = invoice.audit.deletedAt?.toEpochMilliseconds(),
        version = invoice.audit.version.toLong(),
    )

    invoiceQueries.update(
        projectId = invoice.projectId.value,
        number = invoice.number,
        kind = invoice.kind.name,
        status = invoice.status.name,
        currency = invoice.currency.code,
        lines = syncJson.encodeToString(invoice.lines),
        issuedAt = invoice.issuedAt?.toEpochMilliseconds(),
        dueAt = invoice.dueAt?.toEpochMilliseconds(),
        notes = invoice.notes,
        updatedAt = invoice.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = invoice.audit.deletedAt?.toEpochMilliseconds(),
        version = invoice.audit.version.toLong(),
        id = invoice.id.value,
    )
}

/** Money received. Written after its invoice — see the apply order in `SyncEngine`. */
internal suspend fun YellowTrackDatabase.applyPayment(payment: Payment) {
    invoiceQueries.insertOrIgnorePayment(
        id = payment.id.value,
        studio_id = payment.studioId.value,
        invoice_id = payment.invoiceId.value,
        amount_minor = payment.amount.minorUnits,
        amount_currency = payment.amount.currency.code,
        paid_at = payment.paidAt.toEpochMilliseconds(),
        method = payment.method.name,
        reference = payment.reference,
        notes = payment.notes,
        created_at = payment.audit.createdAt.toEpochMilliseconds(),
        updated_at = payment.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = payment.audit.deletedAt?.toEpochMilliseconds(),
        version = payment.audit.version.toLong(),
    )

    invoiceQueries.updatePayment(
        amountMinor = payment.amount.minorUnits,
        amountCurrency = payment.amount.currency.code,
        paidAt = payment.paidAt.toEpochMilliseconds(),
        method = payment.method.name,
        reference = payment.reference,
        notes = payment.notes,
        updatedAt = payment.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = payment.audit.deletedAt?.toEpochMilliseconds(),
        version = payment.audit.version.toLong(),
        id = payment.id.value,
    )
}
