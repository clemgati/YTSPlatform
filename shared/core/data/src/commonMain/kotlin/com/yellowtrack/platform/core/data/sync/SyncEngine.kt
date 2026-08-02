package com.yellowtrack.platform.core.data.sync

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.internal.SyncTables
import com.yellowtrack.platform.core.data.internal.toDomain
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.database.YellowTrackDatabase
import com.yellowtrack.platform.core.model.sync.SyncPushOutcome
import com.yellowtrack.platform.core.model.sync.SyncPushRequest

/** What one reconciliation did. */
data class SyncReport(
    val uploaded: Int,
    val downloaded: Int,
    /** Rows the server took, having discarded a version to do it. */
    val conflicted: Int,
    val rejected: Int,
    val cursor: Long,
) {
    val isQuiet: Boolean get() = uploaded == 0 && downloaded == 0
}

/**
 * The device half of reconciliation.
 *
 * Drains the outbox, applies what comes back, and remembers how far it has got. Everything
 * it does is designed around one fact from `docs/adr/0008-synchronisation-semantics.md`:
 * this is the only part of the application whose bugs are invisible. A row that fails to
 * upload does not throw, a row that fails to arrive is simply absent, and neither is
 * noticed on the device where the work was done.
 *
 * ## Push before pull
 *
 * Uploading first means a conflict is detected while this device still holds the version
 * that will lose, so the server can keep it. Pulling first would overwrite that version
 * locally and then upload the row the server already had, and the studio's work would be
 * gone before anything noticed it was in danger.
 */
class SyncEngine(
    private val provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val transport: SyncTransport,
    private val clients: ClientRepository,
    private val projects: ProjectRepository,
    private val sessions: SessionRepository,
    private val clock: AppClock,
) {
    private val studioId get() = studioContext.studioId.value

    suspend fun sync(): SyncReport {
        val database = provider.database()

        val pushed = drain(database)
        val pulled = apply(database)

        return SyncReport(
            uploaded = pushed.uploaded,
            downloaded = pulled.downloaded,
            conflicted = pushed.conflicted,
            rejected = pushed.rejected,
            cursor = pulled.cursor,
        )
    }

    // -- Uploading -----------------------------------------------------------------------

    private data class PushSummary(
        val uploaded: Int,
        val conflicted: Int,
        val rejected: Int,
    )

    /**
     * Uploads what the outbox says has changed.
     *
     * Entries are collapsed by identity first. Three edits to one booking queue three
     * entries and there is one row to send, so sending it three times would be three
     * chances to conflict with the other device over work that is already superseded.
     *
     * Rows are then **re-read** rather than taken from the queue (ADR 0008 decision 6). The
     * consequence is worth knowing before debugging it: an entity deleted after its entry
     * was queued uploads as a tombstone, not as its last live state. That is correct, and
     * it is surprising the first time.
     */
    private suspend fun drain(database: YellowTrackDatabase): PushSummary {
        val pending = database.outboxQueries.selectPending(studioId, BATCH.toLong()).awaitAsList()
        if (pending.isEmpty()) return PushSummary(0, 0, 0)

        val wanted = pending.map { it.entity_table to it.entity_id }.distinct()

        // Read straight from the tables, not through the repositories.
        //
        // Every repository read filters `deleted_at IS NULL`, which is right for a screen and
        // wrong here: a tombstone is exactly what has to be uploaded. Going through them meant
        // a deleted row came back null, was counted as one that had never existed, and had its
        // outbox entry quietly dropped — so no delete has ever reached the server. Proved by
        // `a deleted client is uploaded as a tombstone`, which failed with nothing sent at all.
        val changes =
            SyncPushRequest(
                clients =
                    wanted.forTable(SyncTables.CLIENT).mapNotNull {
                        database.clientQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain(emptyList())
                    },
                contacts =
                    wanted.forTable(SyncTables.CONTACT).mapNotNull {
                        database.contactQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                clientContactLinks =
                    wanted.forTable(SyncTables.CLIENT_CONTACT).mapNotNull {
                        database.clientQueries
                            .selectClientContactLinkById(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                projects =
                    wanted.forTable(SyncTables.PROJECT).mapNotNull {
                        database.projectQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                sessions =
                    wanted.forTable(SyncTables.SESSION).mapNotNull {
                        database.sessionQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                invoices =
                    wanted.forTable(SyncTables.INVOICE).mapNotNull {
                        database.invoiceQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain(emptyList())
                    },
                payments =
                    wanted.forTable(SyncTables.PAYMENT).mapNotNull {
                        database.invoiceQueries
                            .selectPaymentByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                crewMembers =
                    wanted.forTable(SyncTables.CREW_MEMBER).mapNotNull {
                        database.crewMemberQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                deliverables =
                    wanted.forTable(SyncTables.DELIVERABLE).mapNotNull {
                        database.deliverableQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                gearItems =
                    wanted.forTable(SyncTables.GEAR_ITEM).mapNotNull {
                        database.gearQueries
                            .selectGearByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                packingEntries =
                    wanted.forTable(SyncTables.PACKING_ENTRY).mapNotNull {
                        database.gearQueries
                            .selectPackingByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                storageVolumes =
                    wanted.forTable(SyncTables.STORAGE_VOLUME).mapNotNull {
                        database.storageVolumeQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                mediaCopies =
                    wanted.forTable(SyncTables.MEDIA_COPY).mapNotNull {
                        database.mediaCopyQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                leads =
                    wanted.forTable(SyncTables.LEAD).mapNotNull {
                        database.leadQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                expenses =
                    wanted.forTable(SyncTables.EXPENSE).mapNotNull {
                        database.expenseQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                mileages =
                    wanted.forTable(SyncTables.MILEAGE).mapNotNull {
                        database.expenseQueries
                            .selectMileageByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                quotes =
                    wanted.forTable(SyncTables.QUOTE).mapNotNull {
                        database.quoteQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                contracts =
                    wanted.forTable(SyncTables.CONTRACT).mapNotNull {
                        database.contractQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                shots =
                    wanted.forTable(SyncTables.SHOT).mapNotNull {
                        database.shotQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                postTasks =
                    wanted.forTable(SyncTables.POST_TASK).mapNotNull {
                        database.postTaskQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                talentReleases =
                    wanted.forTable(SyncTables.TALENT_RELEASE).mapNotNull {
                        database.talentReleaseQueries
                            .selectByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
                lightingRecipes =
                    wanted.forTable(SyncTables.LIGHTING_RECIPE).mapNotNull {
                        database.gearQueries
                            .selectRecipeByIdForSync(it)
                            .awaitAsOneOrNull()
                            ?.toDomain()
                    },
            )

        // A queued row that no longer exists at all — never uploaded, then hard-deleted —
        // has nothing to send and nothing to keep asking about.
        val vanished = wanted - changes.identities()
        val acks = if (changes.isEmpty) emptyList() else transport.push(changes)

        val byIdentity = acks.associateBy { it.entityTable to it.entityId }

        database.transaction {
            pending.forEach { entry ->
                val identity = entry.entity_table to entry.entity_id
                val ack = byIdentity[identity]

                when {
                    // Queued, never uploaded, then hard-deleted. Nothing to send, and
                    // nothing to keep asking about.
                    ack == null && identity in vanished -> database.outboxQueries.delete(entry.id)

                    // The server said nothing about this row. Not an outcome it reports —
                    // a conclusion drawn from silence, which a dropped connection produces
                    // too. Kept, because the alternative discards the studio's work.
                    ack == null ->
                        database.outboxQueries.recordFailure("the server did not answer about this row", entry.id)

                    // Conflicted still means stored: the server took this version and kept
                    // the one it displaced. The entry has done its job.
                    ack.outcome == SyncPushOutcome.Applied || ack.outcome == SyncPushOutcome.Conflicted ->
                        database.outboxQueries.delete(entry.id)

                    // A rejection is a bug to look at rather than work to throw away.
                    else -> database.outboxQueries.recordFailure(ack.detail, entry.id)
                }
            }
        }

        return PushSummary(
            uploaded =
                acks.count {
                    it.outcome == SyncPushOutcome.Applied || it.outcome == SyncPushOutcome.Conflicted
                },
            conflicted = acks.count { it.outcome == SyncPushOutcome.Conflicted },
            rejected = acks.count { it.outcome == SyncPushOutcome.Rejected },
        )
    }

    // -- Downloading ---------------------------------------------------------------------

    private data class PullSummary(
        val downloaded: Int,
        val cursor: Long,
    )

    /**
     * Applies everything past the cursor, a page at a time.
     *
     * The cursor is only advanced **after** the page it describes has been written. A
     * cursor saved first and rows written second would, on a crash in between, skip those
     * rows permanently — they are past the cursor and will never be offered again.
     *
     * Rows are written straight to the tables rather than through the repositories,
     * because the repositories enqueue to the outbox. Applying a pulled row through them
     * would queue it straight back for upload, and the two devices would push the same row
     * at each other indefinitely.
     */
    private suspend fun apply(database: YellowTrackDatabase): PullSummary {
        var cursor = currentCursor(database)
        var downloaded = 0
        var pages = 0

        while (pages < MAX_PAGES) {
            val page = transport.pull(since = cursor, limit = BATCH)
            val arrived =
                page.clients.size + page.contacts.size + page.clientContactLinks.size +
                    page.projects.size + page.sessions.size +
                    page.invoices.size + page.payments.size +
                    page.crewMembers.size + page.deliverables.size +
                    page.gearItems.size + page.packingEntries.size +
                    page.storageVolumes.size + page.mediaCopies.size +
                    page.leads.size + page.expenses.size + page.mileages.size +
                    page.quotes.size + page.contracts.size +
                    page.shots.size + page.postTasks.size +
                    page.talentReleases.size + page.lightingRecipes.size + page.conflicts.size

            database.transaction {
                // Parents before children. A link references a client and a contact, and a
                // studio's first sync is when all three are new — server_seq cannot be relied
                // on for this, because an edit to the parent moves it after its own child.
                page.clients.forEach { database.applyClient(it) }
                page.contacts.forEach { database.applyContact(it) }
                page.clientContactLinks.forEach { database.applyClientContactLink(it) }
                page.projects.forEach { database.applyProject(it) }
                page.sessions.forEach { database.applySession(it) }
                page.invoices.forEach { database.applyInvoice(it) }
                page.payments.forEach { database.applyPayment(it) }
                page.crewMembers.forEach { database.applyCrewMember(it) }
                page.deliverables.forEach { database.applyDeliverable(it) }
                page.gearItems.forEach { database.applyGearItem(it) }
                page.storageVolumes.forEach { database.applyStorageVolume(it) }
                page.packingEntries.forEach { database.applyPackingEntry(it) }
                page.mediaCopies.forEach { database.applyMediaCopy(it) }
                page.leads.forEach { database.applyLead(it) }
                page.expenses.forEach { database.applyExpense(it) }
                page.mileages.forEach { database.applyMileage(it) }
                page.quotes.forEach { database.applyQuote(it) }
                page.contracts.forEach { database.applyContract(it) }
                page.shots.forEach { database.applyShot(it) }
                page.postTasks.forEach { database.applyPostTask(it) }
                page.talentReleases.forEach { database.applyTalentRelease(it) }
                page.lightingRecipes.forEach { database.applyLightingRecipe(it) }
                page.conflicts.forEach { database.applyConflict(it) }

                database.syncQueries.rememberCursor(studioId, page.cursor, clock.now().toEpochMilliseconds())
            }

            downloaded += arrived
            cursor = page.cursor
            pages++

            if (!page.hasMore) break
        }

        return PullSummary(downloaded, cursor)
    }

    private suspend fun currentCursor(database: YellowTrackDatabase): Long =
        database.syncQueries
            .selectCursor(studioId)
            .awaitAsOneOrNull()
            ?.last_server_seq
            ?: 0L

    private fun List<Pair<String, String>>.forTable(table: String) = filter { it.first == table }.map { it.second }

    private fun SyncPushRequest.identities(): Set<Pair<String, String>> =
        buildSet {
            clients.forEach { add(SyncTables.CLIENT to it.id.value) }
            contacts.forEach { add(SyncTables.CONTACT to it.id.value) }
            clientContactLinks.forEach { add(SyncTables.CLIENT_CONTACT to it.id.value) }
            projects.forEach { add(SyncTables.PROJECT to it.id.value) }
            sessions.forEach { add(SyncTables.SESSION to it.id.value) }
            invoices.forEach { add(SyncTables.INVOICE to it.id.value) }
            payments.forEach { add(SyncTables.PAYMENT to it.id.value) }
            crewMembers.forEach { add(SyncTables.CREW_MEMBER to it.id.value) }
            deliverables.forEach { add(SyncTables.DELIVERABLE to it.id.value) }
            gearItems.forEach { add(SyncTables.GEAR_ITEM to it.id.value) }
            packingEntries.forEach { add(SyncTables.PACKING_ENTRY to it.id.value) }
            storageVolumes.forEach { add(SyncTables.STORAGE_VOLUME to it.id.value) }
            mediaCopies.forEach { add(SyncTables.MEDIA_COPY to it.id.value) }
            leads.forEach { add(SyncTables.LEAD to it.id.value) }
            expenses.forEach { add(SyncTables.EXPENSE to it.id.value) }
            mileages.forEach { add(SyncTables.MILEAGE to it.id.value) }
            quotes.forEach { add(SyncTables.QUOTE to it.id.value) }
            contracts.forEach { add(SyncTables.CONTRACT to it.id.value) }
            shots.forEach { add(SyncTables.SHOT to it.id.value) }
            postTasks.forEach { add(SyncTables.POST_TASK to it.id.value) }
            talentReleases.forEach { add(SyncTables.TALENT_RELEASE to it.id.value) }
            lightingRecipes.forEach { add(SyncTables.LIGHTING_RECIPE to it.id.value) }
        }

    private companion object {
        /** One page, and one drain. Small enough that a phone on a bad connection finishes it. */
        const val BATCH = 200

        /**
         * A backstop, not a limit anyone should reach. Pulling stops when the server says
         * there is no more; this is what stops a server that always says otherwise from
         * spinning here forever.
         */
        const val MAX_PAGES = 500
    }
}
