package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.StudioProfileRepository
import com.yellowtrack.platform.core.model.studio.StudioProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class FakeStudioProfileRepository(
    initial: StudioProfile? = null,
) : StudioProfileRepository {
    private val state = MutableStateFlow(initial)

    override fun observeProfile(): Flow<StudioProfile?> = state

    override suspend fun getProfile(): StudioProfile? = state.first()

    override suspend fun saveProfile(profile: StudioProfile) {
        state.value = profile
    }
}
