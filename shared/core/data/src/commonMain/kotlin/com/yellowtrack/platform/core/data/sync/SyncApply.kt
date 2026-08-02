package com.yellowtrack.platform.core.data.sync

import com.yellowtrack.platform.core.database.YellowTrackDatabase
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientContactLink
import com.yellowtrack.platform.core.model.codb.CodbProfile
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.gear.PackingEntry
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.release.TalentRelease
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.model.studio.StudioProfile
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

/** Crew on a shoot day. Written after its session. */
internal suspend fun YellowTrackDatabase.applyCrewMember(member: CrewMember) {
    crewMemberQueries.insertOrIgnore(
        id = member.id.value,
        studio_id = member.studioId.value,
        session_id = member.sessionId.value,
        name = member.name,
        role = member.role.name,
        phone = member.phone,
        call_time = member.callTime?.toEpochMilliseconds(),
        notes = member.notes,
        created_at = member.audit.createdAt.toEpochMilliseconds(),
        updated_at = member.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = member.audit.deletedAt?.toEpochMilliseconds(),
        version = member.audit.version.toLong(),
    )

    crewMemberQueries.update(
        sessionId = member.sessionId.value,
        name = member.name,
        role = member.role.name,
        phone = member.phone,
        callTime = member.callTime?.toEpochMilliseconds(),
        notes = member.notes,
        updatedAt = member.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = member.audit.deletedAt?.toEpochMilliseconds(),
        version = member.audit.version.toLong(),
        id = member.id.value,
    )
}

/** What a client is owed. Written after its project. */
internal suspend fun YellowTrackDatabase.applyDeliverable(deliverable: Deliverable) {
    deliverableQueries.insertOrIgnore(
        id = deliverable.id.value,
        studio_id = deliverable.studioId.value,
        project_id = deliverable.projectId.value,
        name = deliverable.name,
        kind = deliverable.kind.name,
        status = deliverable.status.name,
        due_at = deliverable.dueAt?.toEpochMilliseconds(),
        delivered_at = deliverable.deliveredAt?.toEpochMilliseconds(),
        approved_at = deliverable.approvedAt?.toEpochMilliseconds(),
        revisions_used = deliverable.revisionsUsed.toLong(),
        notes = deliverable.notes,
        created_at = deliverable.audit.createdAt.toEpochMilliseconds(),
        updated_at = deliverable.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = deliverable.audit.deletedAt?.toEpochMilliseconds(),
        version = deliverable.audit.version.toLong(),
    )

    deliverableQueries.update(
        projectId = deliverable.projectId.value,
        name = deliverable.name,
        kind = deliverable.kind.name,
        status = deliverable.status.name,
        dueAt = deliverable.dueAt?.toEpochMilliseconds(),
        deliveredAt = deliverable.deliveredAt?.toEpochMilliseconds(),
        approvedAt = deliverable.approvedAt?.toEpochMilliseconds(),
        revisionsUsed = deliverable.revisionsUsed.toLong(),
        notes = deliverable.notes,
        updatedAt = deliverable.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = deliverable.audit.deletedAt?.toEpochMilliseconds(),
        version = deliverable.audit.version.toLong(),
        id = deliverable.id.value,
    )
}

/** Kit the studio owns. No parent, so it can be written in any order. */
internal suspend fun YellowTrackDatabase.applyGearItem(item: GearItem) {
    gearQueries.insertGearOrIgnore(
        id = item.id.value,
        studio_id = item.studioId.value,
        name = item.name,
        category = item.category.name,
        status = item.status.name,
        serial_number = item.serialNumber,
        purchase_price_minor = item.purchasePrice?.minorUnits,
        purchase_currency = item.purchasePrice?.currency?.code,
        purchased_on = item.purchasedOn?.toString(),
        last_serviced_at = item.lastServicedAt?.toEpochMilliseconds(),
        notes = item.notes,
        created_at = item.audit.createdAt.toEpochMilliseconds(),
        updated_at = item.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = item.audit.deletedAt?.toEpochMilliseconds(),
        version = item.audit.version.toLong(),
    )

    gearQueries.updateGear(
        name = item.name,
        category = item.category.name,
        status = item.status.name,
        serialNumber = item.serialNumber,
        purchasePriceMinor = item.purchasePrice?.minorUnits,
        purchaseCurrency = item.purchasePrice?.currency?.code,
        purchasedOn = item.purchasedOn?.toString(),
        lastServicedAt = item.lastServicedAt?.toEpochMilliseconds(),
        notes = item.notes,
        updatedAt = item.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = item.audit.deletedAt?.toEpochMilliseconds(),
        version = item.audit.version.toLong(),
        id = item.id.value,
    )
}

/** What went in the bag. Written after its session and its gear item. */
internal suspend fun YellowTrackDatabase.applyPackingEntry(entry: PackingEntry) {
    gearQueries.insertPackingOrIgnore(
        id = entry.id.value,
        studio_id = entry.studioId.value,
        session_id = entry.sessionId.value,
        gear_item_id = entry.gearItemId.value,
        is_packed = if (entry.isPacked) 1L else 0L,
        is_returned = if (entry.isReturned) 1L else 0L,
        created_at = entry.audit.createdAt.toEpochMilliseconds(),
        updated_at = entry.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = entry.audit.deletedAt?.toEpochMilliseconds(),
        version = entry.audit.version.toLong(),
    )

    gearQueries.updatePacking(
        isPacked = if (entry.isPacked) 1L else 0L,
        isReturned = if (entry.isReturned) 1L else 0L,
        updatedAt = entry.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = entry.audit.deletedAt?.toEpochMilliseconds(),
        version = entry.audit.version.toLong(),
        id = entry.id.value,
    )
}

/** A disk or card. No parent. */
internal suspend fun YellowTrackDatabase.applyStorageVolume(volume: StorageVolume) {
    storageVolumeQueries.insertOrIgnore(
        id = volume.id.value,
        studio_id = volume.studioId.value,
        label = volume.label,
        kind = volume.kind.name,
        status = volume.status.name,
        is_offsite = if (volume.isOffsite) 1L else 0L,
        last_checked_at = volume.lastCheckedAt?.toEpochMilliseconds(),
        notes = volume.notes,
        created_at = volume.audit.createdAt.toEpochMilliseconds(),
        updated_at = volume.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = volume.audit.deletedAt?.toEpochMilliseconds(),
        version = volume.audit.version.toLong(),
    )

    storageVolumeQueries.update(
        label = volume.label,
        kind = volume.kind.name,
        status = volume.status.name,
        isOffsite = if (volume.isOffsite) 1L else 0L,
        lastCheckedAt = volume.lastCheckedAt?.toEpochMilliseconds(),
        notes = volume.notes,
        updatedAt = volume.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = volume.audit.deletedAt?.toEpochMilliseconds(),
        version = volume.audit.version.toLong(),
        id = volume.id.value,
    )
}

/** Where the footage went. Written after its session, and after its volume when it names one. */
internal suspend fun YellowTrackDatabase.applyMediaCopy(copy: MediaCopy) {
    mediaCopyQueries.insertOrIgnore(
        id = copy.id.value,
        studio_id = copy.studioId.value,
        session_id = copy.sessionId.value,
        volume_name = copy.volumeName,
        kind = copy.kind.name,
        is_offsite = if (copy.isOffsite) 1L else 0L,
        copied_at = copy.copiedAt?.toEpochMilliseconds(),
        verified_at = copy.verifiedAt?.toEpochMilliseconds(),
        notes = copy.notes,
        created_at = copy.audit.createdAt.toEpochMilliseconds(),
        updated_at = copy.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = copy.audit.deletedAt?.toEpochMilliseconds(),
        version = copy.audit.version.toLong(),
        volume_id = copy.volumeId?.value,
        path = copy.path,
        verified_file_count = copy.verifiedFileCount?.toLong(),
        verified_bytes = copy.verifiedBytes,
    )

    mediaCopyQueries.update(
        sessionId = copy.sessionId.value,
        volumeId = copy.volumeId?.value,
        volumeName = copy.volumeName,
        kind = copy.kind.name,
        isOffsite = if (copy.isOffsite) 1L else 0L,
        copiedAt = copy.copiedAt?.toEpochMilliseconds(),
        verifiedAt = copy.verifiedAt?.toEpochMilliseconds(),
        path = copy.path,
        verifiedFileCount = copy.verifiedFileCount?.toLong(),
        verifiedBytes = copy.verifiedBytes,
        notes = copy.notes,
        updatedAt = copy.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = copy.audit.deletedAt?.toEpochMilliseconds(),
        version = copy.audit.version.toLong(),
        id = copy.id.value,
    )
}

/** An enquiry. Its converted project and client, when it has them, are written before it. */
internal suspend fun YellowTrackDatabase.applyLead(lead: Lead) {
    leadQueries.insertOrIgnore(
        id = lead.id.value,
        studio_id = lead.studioId.value,
        name = lead.name,
        source = lead.source.name,
        status = lead.status.name,
        received_at = lead.receivedAt.toEpochMilliseconds(),
        email = lead.email,
        phone = lead.phone,
        first_response_at = lead.firstResponseAt?.toEpochMilliseconds(),
        service_line = lead.serviceLine?.name,
        desired_date = lead.desiredDate?.toString(),
        budget_low_minor = lead.budgetLow?.minorUnits,
        budget_high_minor = lead.budgetHigh?.minorUnits,
        budget_currency = (lead.budgetLow ?: lead.budgetHigh)?.currency?.code,
        referred_by = lead.referredBy,
        lost_reason = lead.lostReason,
        converted_project_id = lead.convertedProjectId?.value,
        converted_client_id = lead.convertedClientId?.value,
        notes = lead.notes,
        created_at = lead.audit.createdAt.toEpochMilliseconds(),
        updated_at = lead.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = lead.audit.deletedAt?.toEpochMilliseconds(),
        version = lead.audit.version.toLong(),
    )

    leadQueries.update(
        name = lead.name,
        source = lead.source.name,
        status = lead.status.name,
        receivedAt = lead.receivedAt.toEpochMilliseconds(),
        email = lead.email,
        phone = lead.phone,
        firstResponseAt = lead.firstResponseAt?.toEpochMilliseconds(),
        serviceLine = lead.serviceLine?.name,
        desiredDate = lead.desiredDate?.toString(),
        budgetLowMinor = lead.budgetLow?.minorUnits,
        budgetHighMinor = lead.budgetHigh?.minorUnits,
        budgetCurrency = (lead.budgetLow ?: lead.budgetHigh)?.currency?.code,
        referredBy = lead.referredBy,
        lostReason = lead.lostReason,
        convertedProjectId = lead.convertedProjectId?.value,
        convertedClientId = lead.convertedClientId?.value,
        notes = lead.notes,
        updatedAt = lead.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = lead.audit.deletedAt?.toEpochMilliseconds(),
        version = lead.audit.version.toLong(),
        id = lead.id.value,
    )
}

/** Money out. */
internal suspend fun YellowTrackDatabase.applyExpense(expense: Expense) {
    expenseQueries.insertOrIgnore(
        id = expense.id.value,
        studio_id = expense.studioId.value,
        category = expense.category.name,
        description = expense.description,
        amount_minor = expense.amount.minorUnits,
        amount_currency = expense.amount.currency.code,
        incurred_on = expense.incurredOn.toString(),
        project_id = expense.projectId?.value,
        vendor = expense.vendor,
        is_tax_deductible = if (expense.isTaxDeductible) 1L else 0L,
        receipt_reference = expense.receiptReference,
        notes = expense.notes,
        created_at = expense.audit.createdAt.toEpochMilliseconds(),
        updated_at = expense.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = expense.audit.deletedAt?.toEpochMilliseconds(),
        version = expense.audit.version.toLong(),
    )

    expenseQueries.update(
        category = expense.category.name,
        description = expense.description,
        amountMinor = expense.amount.minorUnits,
        amountCurrency = expense.amount.currency.code,
        incurredOn = expense.incurredOn.toString(),
        projectId = expense.projectId?.value,
        vendor = expense.vendor,
        isTaxDeductible = if (expense.isTaxDeductible) 1L else 0L,
        receiptReference = expense.receiptReference,
        notes = expense.notes,
        updatedAt = expense.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = expense.audit.deletedAt?.toEpochMilliseconds(),
        version = expense.audit.version.toLong(),
        id = expense.id.value,
    )
}

/** Miles driven. */
internal suspend fun YellowTrackDatabase.applyMileage(mileage: Mileage) {
    expenseQueries.insertOrIgnoreMileage(
        id = mileage.id.value,
        studio_id = mileage.studioId.value,
        travelled_on = mileage.travelledOn.toString(),
        distance = mileage.distance,
        unit = mileage.unit.name,
        rate_minor = mileage.ratePerUnit.minorUnits,
        rate_currency = mileage.ratePerUnit.currency.code,
        project_id = mileage.projectId?.value,
        purpose = mileage.purpose,
        from_location = mileage.fromLocation,
        to_location = mileage.toLocation,
        created_at = mileage.audit.createdAt.toEpochMilliseconds(),
        updated_at = mileage.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = mileage.audit.deletedAt?.toEpochMilliseconds(),
        version = mileage.audit.version.toLong(),
    )

    expenseQueries.updateMileage(
        travelledOn = mileage.travelledOn.toString(),
        distance = mileage.distance,
        unit = mileage.unit.name,
        rateMinor = mileage.ratePerUnit.minorUnits,
        rateCurrency = mileage.ratePerUnit.currency.code,
        projectId = mileage.projectId?.value,
        purpose = mileage.purpose,
        fromLocation = mileage.fromLocation,
        toLocation = mileage.toLocation,
        updatedAt = mileage.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = mileage.audit.deletedAt?.toEpochMilliseconds(),
        version = mileage.audit.version.toLong(),
        id = mileage.id.value,
    )
}

/** A price offered. Its lines travel with it, in a JSON column, as an invoice's do. */
internal suspend fun YellowTrackDatabase.applyQuote(quote: Quote) {
    quoteQueries.insertOrIgnore(
        id = quote.id.value,
        studio_id = quote.studioId.value,
        project_id = quote.projectId.value,
        number = quote.number,
        status = quote.status.name,
        currency = quote.currency.code,
        lines = syncJson.encodeToString(quote.lines),
        issued_at = quote.issuedAt?.toEpochMilliseconds(),
        valid_until = quote.validUntil?.toEpochMilliseconds(),
        accepted_at = quote.acceptedAt?.toEpochMilliseconds(),
        declined_at = quote.declinedAt?.toEpochMilliseconds(),
        notes = quote.notes,
        terms = quote.terms,
        created_at = quote.audit.createdAt.toEpochMilliseconds(),
        updated_at = quote.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = quote.audit.deletedAt?.toEpochMilliseconds(),
        version = quote.audit.version.toLong(),
    )

    quoteQueries.update(
        projectId = quote.projectId.value,
        number = quote.number,
        status = quote.status.name,
        currency = quote.currency.code,
        lines = syncJson.encodeToString(quote.lines),
        issuedAt = quote.issuedAt?.toEpochMilliseconds(),
        validUntil = quote.validUntil?.toEpochMilliseconds(),
        acceptedAt = quote.acceptedAt?.toEpochMilliseconds(),
        declinedAt = quote.declinedAt?.toEpochMilliseconds(),
        notes = quote.notes,
        terms = quote.terms,
        updatedAt = quote.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = quote.audit.deletedAt?.toEpochMilliseconds(),
        version = quote.audit.version.toLong(),
        id = quote.id.value,
    )
}

/** What was agreed. The usage licence is a document held in one column. */
internal suspend fun YellowTrackDatabase.applyContract(contract: Contract) {
    val licence = contract.usageLicense?.let { syncJson.encodeToString(it) }

    contractQueries.insertOrIgnore(
        id = contract.id.value,
        studio_id = contract.studioId.value,
        project_id = contract.projectId.value,
        title = contract.title,
        status = contract.status.name,
        sent_at = contract.sentAt?.toEpochMilliseconds(),
        signed_at = contract.signedAt?.toEpochMilliseconds(),
        signer_name = contract.signerName,
        signer_email = contract.signerEmail,
        retainer_minor = contract.retainerAmount?.minorUnits,
        retainer_currency = contract.retainerAmount?.currency?.code,
        is_retainer_refundable = if (contract.isRetainerRefundable) 1L else 0L,
        turnaround_days = contract.turnaroundDays?.toLong(),
        revision_rounds = contract.revisionRounds?.toLong(),
        cancellation_terms = contract.cancellationTerms,
        reschedule_terms = contract.rescheduleTerms,
        weather_clause = contract.weatherClause,
        usage_license = licence,
        document_reference = contract.documentReference,
        notes = contract.notes,
        created_at = contract.audit.createdAt.toEpochMilliseconds(),
        updated_at = contract.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = contract.audit.deletedAt?.toEpochMilliseconds(),
        version = contract.audit.version.toLong(),
    )

    contractQueries.update(
        projectId = contract.projectId.value,
        title = contract.title,
        status = contract.status.name,
        sentAt = contract.sentAt?.toEpochMilliseconds(),
        signedAt = contract.signedAt?.toEpochMilliseconds(),
        signerName = contract.signerName,
        signerEmail = contract.signerEmail,
        retainerMinor = contract.retainerAmount?.minorUnits,
        retainerCurrency = contract.retainerAmount?.currency?.code,
        isRetainerRefundable = if (contract.isRetainerRefundable) 1L else 0L,
        turnaroundDays = contract.turnaroundDays?.toLong(),
        revisionRounds = contract.revisionRounds?.toLong(),
        cancellationTerms = contract.cancellationTerms,
        rescheduleTerms = contract.rescheduleTerms,
        weatherClause = contract.weatherClause,
        usageLicense = licence,
        documentReference = contract.documentReference,
        notes = contract.notes,
        updatedAt = contract.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = contract.audit.deletedAt?.toEpochMilliseconds(),
        version = contract.audit.version.toLong(),
        id = contract.id.value,
    )
}

/** One frame on the list. Written after its session. */
internal suspend fun YellowTrackDatabase.applyShot(shot: Shot) {
    shotQueries.insertOrIgnore(
        id = shot.id.value,
        studio_id = shot.studioId.value,
        session_id = shot.sessionId.value,
        description = shot.description,
        group_name = shot.group,
        people = shot.people,
        position = shot.position.toLong(),
        is_captured = if (shot.isCaptured) 1L else 0L,
        captured_at = shot.capturedAt?.toEpochMilliseconds(),
        notes = shot.notes,
        created_at = shot.audit.createdAt.toEpochMilliseconds(),
        updated_at = shot.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = shot.audit.deletedAt?.toEpochMilliseconds(),
        version = shot.audit.version.toLong(),
    )

    shotQueries.update(
        sessionId = shot.sessionId.value,
        description = shot.description,
        groupName = shot.group,
        people = shot.people,
        position = shot.position.toLong(),
        isCaptured = if (shot.isCaptured) 1L else 0L,
        capturedAt = shot.capturedAt?.toEpochMilliseconds(),
        notes = shot.notes,
        updatedAt = shot.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = shot.audit.deletedAt?.toEpochMilliseconds(),
        version = shot.audit.version.toLong(),
        id = shot.id.value,
    )
}

/** Work after the shoot. Written after its project. */
internal suspend fun YellowTrackDatabase.applyPostTask(task: PostProductionTask) {
    postTaskQueries.insertOrIgnore(
        id = task.id.value,
        studio_id = task.studioId.value,
        project_id = task.projectId.value,
        name = task.name,
        kind = task.kind.name,
        status = task.status.name,
        estimated_hours = task.estimatedHours,
        actual_hours = task.actualHours,
        completed_at = task.completedAt?.toEpochMilliseconds(),
        notes = task.notes,
        created_at = task.audit.createdAt.toEpochMilliseconds(),
        updated_at = task.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = task.audit.deletedAt?.toEpochMilliseconds(),
        version = task.audit.version.toLong(),
    )

    postTaskQueries.update(
        projectId = task.projectId.value,
        name = task.name,
        kind = task.kind.name,
        status = task.status.name,
        estimatedHours = task.estimatedHours,
        actualHours = task.actualHours,
        completedAt = task.completedAt?.toEpochMilliseconds(),
        notes = task.notes,
        updatedAt = task.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = task.audit.deletedAt?.toEpochMilliseconds(),
        version = task.audit.version.toLong(),
        id = task.id.value,
    )
}

/** Permission from the person photographed. Written after its session. */
internal suspend fun YellowTrackDatabase.applyTalentRelease(release: TalentRelease) {
    talentReleaseQueries.insertOrIgnore(
        id = release.id.value,
        studio_id = release.studioId.value,
        session_id = release.sessionId.value,
        person_name = release.personName,
        kind = release.kind.name,
        status = release.status.name,
        signed_at = release.signedAt?.toEpochMilliseconds(),
        guardian_name = release.guardianName,
        email = release.email,
        document_reference = release.documentReference,
        notes = release.notes,
        created_at = release.audit.createdAt.toEpochMilliseconds(),
        updated_at = release.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = release.audit.deletedAt?.toEpochMilliseconds(),
        version = release.audit.version.toLong(),
    )

    talentReleaseQueries.update(
        sessionId = release.sessionId.value,
        personName = release.personName,
        kind = release.kind.name,
        status = release.status.name,
        signedAt = release.signedAt?.toEpochMilliseconds(),
        guardianName = release.guardianName,
        email = release.email,
        documentReference = release.documentReference,
        notes = release.notes,
        updatedAt = release.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = release.audit.deletedAt?.toEpochMilliseconds(),
        version = release.audit.version.toLong(),
        id = release.id.value,
    )
}

/** A remembered set-up. Its lights are a document in one column. */
internal suspend fun YellowTrackDatabase.applyLightingRecipe(recipe: LightingRecipe) {
    gearQueries.insertRecipeOrIgnore(
        id = recipe.id.value,
        studio_id = recipe.studioId.value,
        name = recipe.name,
        lights = syncJson.encodeToString(recipe.lights),
        notes = recipe.notes,
        created_at = recipe.audit.createdAt.toEpochMilliseconds(),
        updated_at = recipe.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = recipe.audit.deletedAt?.toEpochMilliseconds(),
        version = recipe.audit.version.toLong(),
    )

    gearQueries.updateRecipe(
        name = recipe.name,
        lights = syncJson.encodeToString(recipe.lights),
        notes = recipe.notes,
        updatedAt = recipe.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = recipe.audit.deletedAt?.toEpochMilliseconds(),
        version = recipe.audit.version.toLong(),
        id = recipe.id.value,
    )
}

/** Who the studio is, on paper. One row per studio, keyed by the studio. */
internal suspend fun YellowTrackDatabase.applyStudioProfile(profile: StudioProfile) {
    studioProfileQueries.insertOrIgnore(
        id = profile.id.value,
        studio_id = profile.studioId.value,
        name = profile.name,
        address = profile.address,
        email = profile.email,
        phone = profile.phone,
        website = profile.website,
        tax_number = profile.taxNumber,
        payment_instructions = profile.paymentInstructions,
        document_footer = profile.documentFooter,
        created_at = profile.audit.createdAt.toEpochMilliseconds(),
        updated_at = profile.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = profile.audit.deletedAt?.toEpochMilliseconds(),
        version = profile.audit.version.toLong(),
        currency = profile.currency.code,
    )

    studioProfileQueries.update(
        name = profile.name,
        currency = profile.currency.code,
        address = profile.address,
        email = profile.email,
        phone = profile.phone,
        website = profile.website,
        taxNumber = profile.taxNumber,
        paymentInstructions = profile.paymentInstructions,
        documentFooter = profile.documentFooter,
        updatedAt = profile.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = profile.audit.deletedAt?.toEpochMilliseconds(),
        version = profile.audit.version.toLong(),
        id = profile.id.value,
    )
}

/** What a day has to earn. Keyed the same way, for the same reason. */
internal suspend fun YellowTrackDatabase.applyCodbProfile(profile: CodbProfile) {
    codbQueries.insertOrIgnore(
        id = profile.id.value,
        studio_id = profile.studioId.value,
        currency = profile.currency.code,
        target_annual_salary_minor = profile.targetAnnualSalary.minorUnits,
        billable_days_per_year = profile.billableDaysPerYear.toLong(),
        tax_rate_basis_points = profile.taxRateBasisPoints.toLong(),
        annual_overhead_minor = profile.annualOverheadOverride?.minorUnits,
        profit_margin_basis_points = profile.desiredProfitMarginBasisPoints.toLong(),
        created_at = profile.audit.createdAt.toEpochMilliseconds(),
        updated_at = profile.audit.updatedAt.toEpochMilliseconds(),
        deleted_at = profile.audit.deletedAt?.toEpochMilliseconds(),
        version = profile.audit.version.toLong(),
    )

    codbQueries.update(
        currency = profile.currency.code,
        targetAnnualSalaryMinor = profile.targetAnnualSalary.minorUnits,
        billableDaysPerYear = profile.billableDaysPerYear.toLong(),
        taxRateBasisPoints = profile.taxRateBasisPoints.toLong(),
        annualOverheadMinor = profile.annualOverheadOverride?.minorUnits,
        profitMarginBasisPoints = profile.desiredProfitMarginBasisPoints.toLong(),
        updatedAt = profile.audit.updatedAt.toEpochMilliseconds(),
        deletedAt = profile.audit.deletedAt?.toEpochMilliseconds(),
        version = profile.audit.version.toLong(),
        id = profile.id.value,
    )
}
