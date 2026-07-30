package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.studio.StudioProfile
import kotlinx.coroutines.flow.Flow

/**
 * Who the studio is, on paper. One profile per studio, so there is no identifier to pass.
 */
interface StudioProfileRepository {
    fun observeProfile(): Flow<StudioProfile?>

    suspend fun getProfile(): StudioProfile?

    suspend fun saveProfile(profile: StudioProfile)
}
