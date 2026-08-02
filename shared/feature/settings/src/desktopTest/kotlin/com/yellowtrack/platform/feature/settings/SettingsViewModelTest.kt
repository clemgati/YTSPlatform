package com.yellowtrack.platform.feature.settings

import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.data.auth.AuthApi
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionState
import com.yellowtrack.platform.core.data.auth.SessionStore
import com.yellowtrack.platform.core.data.auth.StoredSession
import com.yellowtrack.platform.core.data.sync.Synchroniser
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.studio.StudioProfile
import com.yellowtrack.platform.core.model.studio.StudioProfileId
import com.yellowtrack.platform.core.model.sync.SyncConflict
import com.yellowtrack.platform.core.model.sync.SyncConflictId
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.FakeSyncConflictRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.settings.presentation.SettingsContent
import com.yellowtrack.platform.feature.settings.presentation.SettingsViewModel
import com.yellowtrack.platform.feature.settings.presentation.StudioProfileFields
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The studio's own details, which nothing has held until now.
 *
 * The behaviour worth pinning is what happens to a half-filled form: an empty field must
 * mean absent, not present-and-blank, because every document builder asks whether a field
 * is there and an empty string answers yes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val studioId = LocalStudioContext.LOCAL_STUDIO_ID

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- What synchronisation discarded -------------------------------------------------

    @Test
    fun `a conflict is shown in the studio's own words, not the table's`() =
        runTest {
            val harness = harness(conflicts = listOf(conflict(entityTable = "session")))

            val shown =
                harness.viewModel
                    .content()
                    .conflicts
                    .single()

            assertEquals("A shoot day", shown.what, "the studio never chose the table names")
            assertEquals("Title", shown.differences.single().label)
            assertEquals("Ceremony — 2pm", shown.differences.single().discarded)
        }

    @Test
    fun `an entity nobody thought to label still appears`() =
        runTest {
            val harness = harness(conflicts = listOf(conflict(entityTable = "invoice")))

            assertEquals(
                "A record",
                harness.viewModel
                    .content()
                    .conflicts
                    .single()
                    .what,
                "the eighteen entities not yet in the slice will arrive before their labels do, " +
                    "and a conflict with no label is still work somebody lost",
            )
        }

    @Test
    fun `dismissing one reaches the repository`() =
        runTest {
            val harness = harness(conflicts = listOf(conflict()))

            harness.viewModel.dismissConflict(SyncConflictId("conflict-1"))

            assertEquals(listOf(SyncConflictId("conflict-1")), harness.conflicts.resolved)
        }

    @Test
    fun `no conflicts means nothing to show`() =
        runTest {
            val harness = harness()

            assertTrue(
                harness.viewModel
                    .content()
                    .conflicts
                    .isEmpty(),
            )
        }

    private fun conflict(entityTable: String = "session") =
        SyncConflict(
            id = SyncConflictId("conflict-1"),
            studioId = LocalStudioContext().studioId,
            entityTable = entityTable,
            entityId = "session-1",
            losingPayload = """{"title":"Ceremony — 2pm"}""",
            winningPayload = """{"title":"Ceremony — 3pm"}""",
            detectedAt = CONFLICT_TIME,
            audit = AuditMetadata.createdAt(CONFLICT_TIME),
        )

    @Test
    fun `offers a way out, which the application did not have`() =
        runTest {
            val harness = harness()
            harness.auth.restore(now = 0)

            val account = assertNotNull(harness.viewModel.content().account, "nothing showed who was signed in")
            assertEquals("ada@harbourline.test", account.email)
            assertEquals("Harbourline Photography", account.studioName)

            harness.viewModel.signOut()

            assertTrue(
                harness.auth.session.value is SessionState.SignedOut,
                "signOut existed on the repository and only the synchroniser ever called it, so the " +
                    "sign-in screen's advice to sign out when finished could not be followed",
            )
        }

    private companion object {
        val CONFLICT_TIME: Instant = Instant.fromEpochMilliseconds(1_781_100_000_000)
    }

    private object UnusedAuthApi : AuthApi {
        override suspend fun signIn(
            email: String,
            password: String,
        ): StoredSession = error("unused")

        override suspend fun signUp(
            email: String,
            password: String,
            name: String,
            studioName: String,
        ): StoredSession = error("unused")

        override suspend fun signOut(token: String) = Unit

        override suspend fun requestPasswordReset(email: String) = error("unused")

        override suspend fun resetPassword(
            email: String,
            code: String,
            newPassword: String,
        ) = error("unused")
    }

    /** Starts out holding a session, so there is something to sign out of. */
    private class InMemorySessionStore : SessionStore {
        override val isHardwareBacked = false

        var session: StoredSession? =
            StoredSession(
                token = "a-token",
                expiresAt = Long.MAX_VALUE,
                accountId = "account-1",
                email = "ada@harbourline.test",
                name = "Ada Okafor",
                studioId = "studio-1",
                studioName = "Harbourline Photography",
            )

        override suspend fun read(): StoredSession? = session

        override suspend fun write(session: StoredSession) {
            this.session = session
        }

        override suspend fun clear() {
            session = null
        }
    }

    private class Harness(
        val viewModel: SettingsViewModel,
        val repository: FakeStudioProfileRepository,
        val conflicts: FakeSyncConflictRepository,
        val auth: AuthRepository,
    )

    private fun harness(
        existing: StudioProfile? = null,
        conflicts: List<SyncConflict> = emptyList(),
    ): Harness {
        val repository = FakeStudioProfileRepository(existing)
        val conflictRepository = FakeSyncConflictRepository(conflicts)
        val auth = AuthRepository(store = InMemorySessionStore(), api = UnusedAuthApi)

        return Harness(
            viewModel =
                SettingsViewModel(
                    profileRepository = repository,
                    conflictRepository = conflictRepository,
                    synchroniser =
                        Synchroniser(
                            reconcile = { error("the settings tests never reconcile, so this is unreachable") },
                            auth = auth,
                            scope = CoroutineScope(UnconfinedTestDispatcher()),
                        ),
                    auth = auth,
                    studioContext = LocalStudioContext(),
                    clock = TestAppClock(),
                ),
            repository = repository,
            conflicts = conflictRepository,
            auth = auth,
        )
    }

    private suspend fun SettingsViewModel.content(): SettingsContent {
        val state = uiState.first { it.content is UiState.Success }

        return (state.content as UiState.Success).data
    }

    private fun saved(name: String = "Yellow Track Studios") =
        StudioProfile(
            id = StudioProfileId.new(),
            studioId = studioId,
            name = name,
            address = "12 Harbour Road",
            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
        )

    // --- First use ---------------------------------------------------------------------

    @Test
    fun `a studio that has never filled this in gets an empty form rather than an error`() =
        runTest {
            val content = harness().viewModel.content()

            assertEquals("", content.profile.name)
            assertFalse(content.canIssueDocuments)
        }

    @Test
    fun `saving for the first time creates the profile`() =
        runTest {
            val harness = harness()

            harness.viewModel.save(StudioProfileFields(name = "Yellow Track Studios"))

            val stored = assertNotNull(harness.repository.getProfile())
            assertEquals("Yellow Track Studios", stored.name)
            assertEquals(studioId, stored.studioId)
        }

    @Test
    fun `saving again keeps the same profile rather than making a second`() =
        runTest {
            val existing = saved()
            val harness = harness(existing)

            harness.viewModel.save(StudioProfileFields(name = "Yellow Track Photography"))

            val stored = assertNotNull(harness.repository.getProfile())
            assertEquals(existing.id, stored.id, "a second profile would put two names on two invoices")
            assertEquals("Yellow Track Photography", stored.name)
        }

    // --- Blank means absent --------------------------------------------------------------

    @Test
    fun `a field left blank is stored as absent rather than as an empty string`() =
        runTest {
            val harness = harness()

            harness.viewModel.save(StudioProfileFields(name = "Yellow Track Studios", taxNumber = ""))

            val stored = assertNotNull(harness.repository.getProfile())
            assertNull(stored.taxNumber, "the document builders ask whether it is there; an empty string says yes")
        }

    @Test
    fun `surrounding whitespace is not part of a studio's name`() =
        runTest {
            val harness = harness()

            harness.viewModel.save(StudioProfileFields(name = "  Yellow Track Studios  "))

            assertEquals("Yellow Track Studios", assertNotNull(harness.repository.getProfile()).name)
        }

    @Test
    fun `an address keeps the lines it was typed on`() =
        runTest {
            val harness = harness()

            harness.viewModel.save(
                StudioProfileFields(name = "Yellow Track Studios", address = "12 Harbour Road\nFalmouth\nTR11 3AA"),
            )

            assertEquals(
                "12 Harbour Road\nFalmouth\nTR11 3AA",
                assertNotNull(harness.repository.getProfile()).address,
                "an address printed as one run-on line is not an address",
            )
        }

    // --- What the studio is told -----------------------------------------------------------

    @Test
    fun `saving without a name says plainly that nothing can be sent`() =
        runTest {
            val harness = harness()

            harness.viewModel.save(StudioProfileFields(name = ""))

            val content = harness.viewModel.content()
            assertEquals("Saved, but without a name nothing can be sent out.", content.savedNote)
            assertFalse(content.canIssueDocuments)
        }

    @Test
    fun `saving with a name confirms documents will carry it`() =
        runTest {
            val harness = harness()

            harness.viewModel.save(StudioProfileFields(name = "Yellow Track Studios"))

            val content = harness.viewModel.content()
            assertEquals("Saved. Your documents will carry these details.", content.savedNote)
            assertTrue(content.canIssueDocuments)
        }

    @Test
    fun `what a client will notice is missing is reported back`() =
        runTest {
            val harness = harness()

            harness.viewModel.save(StudioProfileFields(name = "Yellow Track Studios", email = "a@b.example"))

            val gaps = harness.viewModel.content().gaps
            assertTrue(gaps.contains("no payment instructions"))
            assertTrue(gaps.contains("no tax registration number"))
            assertFalse(gaps.contains("no way to reach you"), "an email is a way to reach someone")
        }

    @Test
    fun `the form opens showing what was already saved`() =
        runTest {
            val content = harness(saved()).viewModel.content()

            assertEquals("Yellow Track Studios", content.profile.name)
            assertEquals("12 Harbour Road", content.profile.address)
            assertTrue(content.canIssueDocuments)
        }
}
