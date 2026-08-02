package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.studio.StudioProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * Who the studio is, on paper. One profile per studio, so there is no identifier to pass.
 */
interface StudioProfileRepository {
    fun observeProfile(): Flow<StudioProfile?>

    suspend fun getProfile(): StudioProfile?

    suspend fun saveProfile(profile: StudioProfile)
}

/**
 * Fills in the name the studio already gave, on first use of a device.
 *
 * A studio types its name once, when it signs up. That name goes to the server, and the
 * profile every document is built from stayed empty — so Settings opened on a new device
 * demanding, in red, a name the studio had already provided, and any invoice raised before
 * it was retyped carried none.
 *
 * Only when there is no profile at all, and only the name. A studio that has since renamed
 * itself on paper, or set anything else, has said something this must not overwrite; and
 * the profile does not synchronise yet, so a second device would otherwise start blank.
 */
suspend fun StudioProfileRepository.adoptStudioName(
    studioName: String,
    studioId: StudioId,
    now: Instant,
) {
    if (studioName.isBlank() || getProfile() != null) return

    saveProfile(
        StudioProfile
            .empty(studioId, AuditMetadata.createdAt(now))
            .copy(name = studioName.trim()),
    )
}

/**
 * What the studio charges in.
 *
 * Dollars until it has said otherwise — one fallback, in one place, rather than a default
 * argument repeated in every ViewModel, which is exactly how this became a global constant
 * the first time.
 */
suspend fun StudioProfileRepository.currency(): CurrencyCode = getProfile()?.currency ?: DEFAULT_CURRENCY

fun StudioProfileRepository.observeCurrency(): Flow<CurrencyCode> =
    observeProfile().map { it?.currency ?: DEFAULT_CURRENCY }

private val DEFAULT_CURRENCY = CurrencyCode.USD
