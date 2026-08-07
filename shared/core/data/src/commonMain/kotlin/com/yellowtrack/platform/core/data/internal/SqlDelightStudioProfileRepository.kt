package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.StudioProfileRepository
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.studio.StudioProfile
import com.yellowtrack.platform.core.model.studio.StudioProfileId
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yellowtrack.platform.core.database.Studio_profile as StudioProfileRow

internal class SqlDelightStudioProfileRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
    private val remote: RemoteWriter,
) : DatabaseBackedRepository(provider),
    StudioProfileRepository {
    override fun observeProfile(): Flow<StudioProfile?> =
        observing { db ->
            db.studioProfileQueries
                .selectForStudio(studioContext.studioId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }

    override suspend fun getProfile(): StudioProfile? = observeProfile().first()

    override suspend fun saveProfile(profile: StudioProfile) {
        val db = database()
        val now = clock.now().toEpochMillis()

        remote.write(SyncPushRequest(studioProfiles = listOf(profile)))

        db.transaction {
            db.studioProfileQueries.insertOrIgnore(
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
                currency = profile.currency.code,
                created_at = profile.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = profile.audit.deletedAt.toEpochMillisOrNull(),
                version = profile.audit.version.toLong(),
            )

            db.studioProfileQueries.update(
                name = profile.name,
                currency = profile.currency.code,
                address = profile.address,
                email = profile.email,
                phone = profile.phone,
                website = profile.website,
                taxNumber = profile.taxNumber,
                paymentInstructions = profile.paymentInstructions,
                documentFooter = profile.documentFooter,
                updatedAt = now,
                deletedAt = profile.audit.deletedAt.toEpochMillisOrNull(),
                version = profile.audit.version.toLong(),
                id = profile.id.value,
            )
        }
    }
}

internal fun StudioProfileRow.toDomain(): StudioProfile =
    StudioProfile(
        id = StudioProfileId(id),
        studioId = StudioId(studio_id),
        name = name,
        address = address,
        email = email,
        phone = phone,
        website = website,
        taxNumber = tax_number,
        paymentInstructions = payment_instructions,
        documentFooter = document_footer,
        // An unreadable code falls back to dollars rather than throwing: CurrencyCode
        // rejects anything that is not three letters, and a corrupt row must not make the
        // whole studio unopenable.
        currency = runCatching { CurrencyCode(currency) }.getOrDefault(CurrencyCode.USD),
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
