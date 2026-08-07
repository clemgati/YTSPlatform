package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.data.auth.SessionState
import com.yellowtrack.platform.core.data.auth.StoredSession
import com.yellowtrack.platform.core.data.sync.SyncReport
import com.yellowtrack.platform.core.data.sync.SyncStatus
import com.yellowtrack.platform.core.data.sync.WriteFailed
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import com.yellowtrack.platform.core.model.studio.StudioProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * When a device is allowed to decide what a studio's defaults are.
 *
 * The answer is: after the studio has told it, and not before. This exists because the
 * opposite shipped — an empty device seeded four packages and a profile at version 1 and
 * pushed them at a server holding version 42, and each one came back as a conflict for
 * somebody to resolve by hand.
 */
class StudioDefaultsTest {
    /**
     * The one that was broken. A device with an empty database and a signed-in session must
     * write nothing until it has heard back, because "I have no templates" and "this studio
     * has no templates" are different statements and only the second justifies seeding.
     */
    @Test
    fun `a signed-in device seeds nothing before its first sync`() =
        runTest {
            val templates = RecordingTemplates()
            val profiles = RecordingProfiles()

            // Never succeeds — the device is signed in and cannot reach the server.
            val job =
                launch {
                    fillStudioDefaults(signedIn, emptyFlow(), templates, profiles, STUDIO, NOW)
                }
            runCurrent()

            assertFalse(templates.seeded, "an empty device is not an empty studio")
            assertNull(profiles.saved, "and it does not get to invent the profile either")
            job.cancel()
        }

    @Test
    fun `and seeds once the studio has answered`() =
        runTest {
            val templates = RecordingTemplates()
            val profiles = RecordingProfiles()
            val status = MutableStateFlow<SyncStatus>(SyncStatus.Working)

            val job = launch { fillStudioDefaults(signedIn, status, templates, profiles, STUDIO, NOW) }
            runCurrent()
            assertFalse(templates.seeded, "still working, so still nothing")

            status.value = SyncStatus.Succeeded(at = 0L, report = report())
            job.join()

            assertTrue(templates.seeded)
            assertEquals("Clement's Photos", profiles.saved?.name, "the name the studio signed up with")
        }

    /**
     * A failed sync is not an answer. This is the browser's three-minute loop stated as a
     * rule: an empty in-memory database plus a failing connection must still write nothing.
     */
    @Test
    fun `a failed sync does not count as the studio answering`() =
        runTest {
            val templates = RecordingTemplates()
            val status = flowOf<SyncStatus>(SyncStatus.Failed(at = 0L, reason = "no connection"))

            val job = launch { fillStudioDefaults(signedIn, status, templates, RecordingProfiles(), STUDIO, NOW) }
            runCurrent()

            assertFalse(templates.seeded, "offline means unknown, and unknown is not empty")
            job.cancel()
        }

    /**
     * There is no studio to seed for, and no screen behind the sign-in form to show packages
     * on. Seeding here used to leave four templates under the placeholder studio id, which
     * are sitting in the database belonging to nobody.
     */
    @Test
    fun `a signed-out device seeds nothing for a studio that does not exist yet`() =
        runTest {
            val templates = RecordingTemplates()

            fillStudioDefaults(SessionState.SignedOut, emptyFlow(), templates, RecordingProfiles(), STUDIO, NOW)

            assertFalse(templates.seeded, "the placeholder studio is not a studio")
        }

    /**
     * Seeding writes through the server now, so it can fail on a connection that dropped
     * between the sync and here. Nobody is waiting on the result and the next launch tries
     * again — but an unhandled WriteFailed would take down the effect that draws the
     * application, which is a blank window instead of a missing default.
     */
    @Test
    fun `a connection lost after the sync does not bring the screen down`() =
        runTest {
            val templates = RecordingTemplates(failing = true)
            val status = MutableStateFlow<SyncStatus>(SyncStatus.Succeeded(at = 0L, report = report()))

            fillStudioDefaults(signedIn, status, templates, RecordingProfiles(), STUDIO, NOW)

            assertTrue(templates.seeded, "it tried")
        }

    @Test
    fun `a device that has not read its session yet does nothing at all`() =
        runTest {
            val templates = RecordingTemplates()
            val profiles = RecordingProfiles()

            fillStudioDefaults(SessionState.Unknown, emptyFlow(), templates, profiles, STUDIO, NOW)

            assertFalse(templates.seeded, "the session decides which of the two rules applies")
            assertNull(profiles.saved)
        }

    private fun report() = SyncReport(uploaded = 0, downloaded = 0, conflicted = 0, rejected = 0, cursor = 0L)

    // -- Fixtures ------------------------------------------------------------------------------

    private val signedIn =
        SessionState.SignedIn(
            StoredSession(
                token = "token",
                expiresAt = Long.MAX_VALUE,
                accountId = "account-1",
                email = "clement@yellowtrackstudios.com",
                name = "Clement",
                studioId = STUDIO.value,
                studioName = "Clement's Photos",
            ),
        )

    private class RecordingTemplates(
        private val failing: Boolean = false,
    ) : ServiceTemplateRepository {
        var seeded = false

        override fun observeTemplates(): Flow<List<ServiceTemplate>> = emptyFlow()

        override suspend fun getTemplate(id: ServiceTemplateId): ServiceTemplate? = null

        override suspend fun saveTemplate(template: ServiceTemplate) = Unit

        override suspend fun deleteTemplate(id: ServiceTemplateId) = Unit

        override suspend fun seedDefaultsIfEmpty() {
            seeded = true
            if (failing) throw WriteFailed.Offline
        }
    }

    private class RecordingProfiles : StudioProfileRepository {
        var saved: StudioProfile? = null

        override fun observeProfile(): Flow<StudioProfile?> = emptyFlow()

        // Empty, which is what an in-memory browser database looks like every three minutes.
        override suspend fun getProfile(): StudioProfile? = saved

        override suspend fun saveProfile(profile: StudioProfile) {
            saved = profile
        }
    }

    private companion object {
        val STUDIO = StudioId("b88a3183-d3ac-4fd7-ad82-37be0f3dd4ac")
        val NOW: Instant = Instant.fromEpochMilliseconds(1_786_090_014_135)
    }
}
