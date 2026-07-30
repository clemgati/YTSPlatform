package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.model.studio.StudioProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Who the studio is, on paper. One profile per studio, so there is no identifier to pass.
 */
interface StudioProfileRepository {
    fun observeProfile(): Flow<StudioProfile?>

    suspend fun getProfile(): StudioProfile?

    suspend fun saveProfile(profile: StudioProfile)
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
