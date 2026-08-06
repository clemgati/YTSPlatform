package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.QuoteRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class SqlDelightQuoteRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    QuoteRepository {
    private val studioId get() = studioContext.studioId.value

    override fun observeQuotes(): Flow<List<Quote>> =
        observing { db ->
            db.quoteQueries
                .selectAll(studioId)
                .asListFlow(dispatcher)
                .map { rows -> rows.map { it.toDomain() } }
        }

    override fun observeQuote(quoteId: QuoteId): Flow<Quote?> =
        observing { db ->
            db.quoteQueries
                .selectById(quoteId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }

    override fun observeQuotesForProject(projectId: ProjectId): Flow<List<Quote>> =
        observing { db ->
            db.quoteQueries
                .selectByProject(projectId.value)
                .asListFlow(dispatcher)
                .map { rows -> rows.map { it.toDomain() } }
        }

    /**
     * Sorted by when the quote went out, not when the row was written, so the one the
     * client has been sitting on longest is first.
     */
    override fun observeAwaitingDecision(): Flow<List<Quote>> =
        observeQuotes().map { quotes ->
            quotes
                .filter { it.status.isAwaitingDecision }
                .sortedBy { it.issuedAt ?: it.audit.createdAt }
        }

    override suspend fun getQuote(quoteId: QuoteId): Quote? = observeQuote(quoteId).first()

    override suspend fun saveQuote(quote: Quote) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // Expired is derived from the validity date and never written back, or a quote
        // would be frozen as expired the moment its date is extended.
        val storedStatus = if (quote.status == QuoteStatus.Expired) QuoteStatus.Sent else quote.status

        db.transaction {
            db.quoteQueries.insertOrIgnore(
                id = quote.id.value,
                studio_id = quote.studioId.value,
                project_id = quote.projectId.value,
                number = quote.number,
                status = storedStatus.name,
                currency = quote.currency.code,
                lines = encodeLines(quote.lines),
                issued_at = quote.issuedAt.toEpochMillisOrNull(),
                valid_until = quote.validUntil.toEpochMillisOrNull(),
                accepted_at = quote.acceptedAt.toEpochMillisOrNull(),
                declined_at = quote.declinedAt.toEpochMillisOrNull(),
                notes = quote.notes,
                terms = quote.terms,
                created_at = quote.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = quote.audit.deletedAt.toEpochMillisOrNull(),
                version = quote.audit.version.toLong(),
                last_emailed_at = quote.lastEmailedAt.toEpochMillisOrNull(),
                last_emailed_to = quote.lastEmailedTo,
            )

            db.quoteQueries.update(
                projectId = quote.projectId.value,
                number = quote.number,
                status = storedStatus.name,
                currency = quote.currency.code,
                lines = encodeLines(quote.lines),
                issuedAt = quote.issuedAt.toEpochMillisOrNull(),
                validUntil = quote.validUntil.toEpochMillisOrNull(),
                acceptedAt = quote.acceptedAt.toEpochMillisOrNull(),
                declinedAt = quote.declinedAt.toEpochMillisOrNull(),
                notes = quote.notes,
                terms = quote.terms,
                updatedAt = now,
                deletedAt = quote.audit.deletedAt.toEpochMillisOrNull(),
                version = quote.audit.version.toLong(),
                lastEmailedAt = quote.lastEmailedAt.toEpochMillisOrNull(),
                lastEmailedTo = quote.lastEmailedTo,
                id = quote.id.value,
            )

            db.enqueueForSync(studioId, SyncTables.QUOTE, quote.id.value, OutboxOperation.Upsert, now)
        }
    }

    override suspend fun deleteQuote(quoteId: QuoteId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.quoteQueries.softDelete(deletedAt = now, id = quoteId.value)
            db.enqueueForSync(studioId, SyncTables.QUOTE, quoteId.value, OutboxOperation.Delete, now)
        }
    }

    override suspend fun recordQuoteEmailed(
        quoteId: QuoteId,
        to: String,
    ) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.quoteQueries.recordEmailed(emailedAt = now, emailedTo = to, id = quoteId.value)

            // Queued like any other change, so the studio's other devices learn it was sent
            // without anybody having to remember which one did the sending.
            db.enqueueForSync(studioId, SyncTables.QUOTE, quoteId.value, OutboxOperation.Upsert, now)
        }
    }
}
