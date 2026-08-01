package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.studio.StudioProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The name a studio gives when it signs up, on the profile its documents are built from.
 *
 * Found by deploying and signing up: Settings opened demanding a studio name, in red, that
 * had been typed into the sign-up form minutes earlier. Until it was retyped, every quote
 * and invoice would have gone out with no name on it.
 */
class AdoptStudioNameTest {
    /**
     * Local rather than the shared fake: `core:testing` depends on this module, so taking it
     * as a test dependency here would be a cycle.
     */
    private class InMemoryProfiles(
        initial: StudioProfile? = null,
    ) : StudioProfileRepository {
        private val state = MutableStateFlow(initial)

        override fun observeProfile(): Flow<StudioProfile?> = state

        override suspend fun getProfile(): StudioProfile? = state.value

        override suspend fun saveProfile(profile: StudioProfile) {
            state.value = profile
        }
    }

    private val studioId = LocalStudioContext.LOCAL_STUDIO_ID
    private val now = Instant.fromEpochMilliseconds(1_781_100_000_000)

    @Test
    fun `fills in the name on a device that has no profile yet`() =
        runTest {
            val repository = InMemoryProfiles()

            repository.adoptStudioName("Harbourline Photography", studioId, now)

            assertEquals("Harbourline Photography", repository.getProfile()?.name)
        }

    @Test
    fun `leaves a profile the studio has already filled in alone`() =
        runTest {
            val existing =
                StudioProfile
                    .empty(studioId, AuditMetadata.createdAt(now))
                    .copy(name = "Harbourline Photography Ltd", taxNumber = "GB123456789")
            val repository = InMemoryProfiles(existing)

            repository.adoptStudioName("Harbourline Photography", studioId, now)

            val profile = repository.getProfile()
            assertEquals(
                "Harbourline Photography Ltd",
                profile?.name,
                "a studio that has renamed itself on paper has said something this must not undo",
            )
            assertEquals("GB123456789", profile?.taxNumber, "and nothing else may be reset either")
        }

    @Test
    fun `a blank name creates nothing`() =
        runTest {
            val repository = InMemoryProfiles()

            repository.adoptStudioName("   ", studioId, now)

            assertNull(
                repository.getProfile(),
                "an empty profile is worse than none: canIssueDocuments would read true for a " +
                    "studio with no name",
            )
        }

    @Test
    fun `trims what was typed`() =
        runTest {
            val repository = InMemoryProfiles()

            repository.adoptStudioName("  Harbourline Photography  ", studioId, now)

            assertEquals("Harbourline Photography", repository.getProfile()?.name)
        }
}
