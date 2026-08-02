package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.database.YellowTrackDatabase
import kotlin.uuid.ExperimentalUuidApi

/** What happened to a row, for the record. See [enqueueForSync] on why it is not acted on. */
internal enum class OutboxOperation {
    Upsert,
    Delete,
}

/**
 * Notes that a row needs uploading.
 *
 * Must be called inside the same transaction as the write it describes. An outbox entry
 * without its row, or a row without its entry, is a change that either uploads something
 * that never happened or never uploads at all — and the second is silent.
 *
 * **The payload column stays null.** ADR 0008 decision 6: the drain re-reads the row at
 * upload time rather than uploading a photograph of it taken when it was queued. Three
 * edits made offline queue three entries and upload once, because the intermediate states
 * are of no interest to anybody.
 *
 * The operation is recorded for the audit trail and is deliberately *not* what the drain
 * acts on. A row's own `deleted_at` says whether it is a tombstone, and that is read fresh;
 * trusting a queued operation would mean an entity deleted after its Upsert was queued
 * would upload as alive.
 */
@OptIn(ExperimentalUuidApi::class)
internal suspend fun YellowTrackDatabase.enqueueForSync(
    studioId: String,
    table: String,
    entityId: String,
    operation: OutboxOperation,
    now: Long,
) {
    outboxQueries.enqueue(
        id = uuidV7().toString(),
        studio_id = studioId,
        entity_table = table,
        entity_id = entityId,
        operation = operation.name,
        payload = null,
        queued_at = now,
    )
}

/** The table names the server knows these entities by. They cross the wire, so they are fixed. */
internal object SyncTables {
    const val CLIENT = "client"
    const val CONTACT = "contact"
    const val CLIENT_CONTACT = "client_contact"
    const val PROJECT = "project"
    const val SESSION = "session"
    const val INVOICE = "invoice"
    const val PAYMENT = "payment"
    const val CREW_MEMBER = "crew_member"
    const val DELIVERABLE = "deliverable"
    const val GEAR_ITEM = "gear_item"
    const val PACKING_ENTRY = "packing_entry"
    const val STORAGE_VOLUME = "storage_volume"
    const val MEDIA_COPY = "media_copy"
    const val LEAD = "lead"
    const val EXPENSE = "expense"
    const val MILEAGE = "mileage"
}
