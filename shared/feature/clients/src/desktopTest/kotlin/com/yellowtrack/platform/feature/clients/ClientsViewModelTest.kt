package com.yellowtrack.platform.feature.clients

import app.cash.turbine.test
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.testing.TestData
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.list.ClientsViewModel
import com.yellowtrack.platform.feature.clients.presentation.list.model.ClientSummary
import com.yellowtrack.platform.feature.clients.presentation.model.NewClient
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class ClientsViewModelTest {
    private val clock = TestAppClock()

    @BeforeTest
    fun setUp() {
        // viewModelScope dispatches on Main, which does not exist in a plain JVM test.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        clients: FakeClientRepository = FakeClientRepository(),
        projects: FakeProjectRepository = FakeProjectRepository(),
        sessions: FakeSessionRepository = FakeSessionRepository(),
    ) = ClientsViewModel(clients, projects, sessions, LocalStudioContext(), clock)

    @Test
    fun `reports empty when the studio has no clients`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(UiState.Empty, awaitItem().clients)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `counts every session across a client's projects`() =
        runTest {
            val client = TestData.couple()
            val wedding = TestData.project(clientId = client.id)

            val viewModel =
                viewModel(
                    clients = FakeClientRepository(listOf(client)),
                    projects = FakeProjectRepository(listOf(wedding)),
                    sessions =
                        FakeSessionRepository(
                            listOf(
                                // A wedding is one project holding two sessions.
                                TestData.session(
                                    projectId = wedding.id,
                                    title = "Engagement",
                                    startsAt = TestAppClock.DEFAULT_NOW - 60.days,
                                ),
                                TestData.session(
                                    projectId = wedding.id,
                                    title = "Wedding Day",
                                    startsAt = TestAppClock.DEFAULT_NOW - 1.days,
                                ),
                            ),
                        ),
                )

            viewModel.uiState.test {
                val summaries = awaitItem().clients.successData()

                assertEquals(1, summaries.size, "two sessions on one project is still one client")
                assertEquals(2, summaries.single().sessionCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `derives initials from a couple's account name without the ampersand`() =
        runTest {
            val client = TestData.couple(accountName = "Sarah & Michael Johnson")

            viewModel(clients = FakeClientRepository(listOf(client))).uiState.test {
                assertEquals(
                    "SJ",
                    awaitItem()
                        .clients
                        .successData()
                        .single()
                        .initials,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a future session is not counted as the last session`() =
        runTest {
            val client = TestData.client()
            val project = TestData.project(clientId = client.id)

            val viewModel =
                viewModel(
                    clients = FakeClientRepository(listOf(client)),
                    projects = FakeProjectRepository(listOf(project)),
                    sessions =
                        FakeSessionRepository(
                            listOf(
                                TestData.session(
                                    projectId = project.id,
                                    startsAt = TestAppClock.DEFAULT_NOW + 30.days,
                                ),
                            ),
                        ),
                )

            viewModel.uiState.test {
                val summary = awaitItem().clients.successData().single()

                assertEquals(1, summary.sessionCount)
                assertEquals(null, summary.lastSession, "an upcoming shoot has not happened yet")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `searching narrows the list and flags that a search is active`() =
        runTest {
            val viewModel =
                viewModel(
                    clients =
                        FakeClientRepository(
                            listOf(
                                TestData.client(accountName = "Harborline Coffee"),
                                TestData.client(accountName = "Sarah & Michael Johnson"),
                            ),
                        ),
                )

            viewModel.uiState.test {
                assertEquals(2, awaitItem().clients.successData().size)

                viewModel.onQueryChange("harbor")

                val searched = awaitItem()
                assertTrue(searched.isSearching)
                assertEquals(listOf("Harborline Coffee"), searched.clients.successData().map { it.displayName })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a search matching nothing reports empty rather than an error`() =
        runTest {
            val viewModel =
                viewModel(clients = FakeClientRepository(listOf(TestData.client(accountName = "Harborline Coffee"))))

            viewModel.uiState.test {
                awaitItem()
                viewModel.onQueryChange("nothing matches this")

                val result = awaitItem()
                assertEquals(UiState.Empty, result.clients)
                assertTrue(result.isSearching, "the screen must be able to tell 'no matches' from 'no clients'")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `surfaces a repository failure as an error state`() =
        runTest {
            val clients = FakeClientRepository()
            clients.failure = IllegalStateException("database unavailable")

            viewModel(clients = clients).uiState.test {
                val state = awaitItem()
                assertTrue(state.clients is UiState.Error)
                assertEquals("database unavailable", (state.clients as UiState.Error).message)
                cancelAndIgnoreRemainingEvents()
            }
        }
    // --- Taking on a client ------------------------------------------------------------

    @Test
    fun `a client is stored with its first contact attached`() =
        runTest {
            val clients = FakeClientRepository()

            viewModel(clients = clients).addClient(
                newClient(
                    accountName = "Sarah & Michael Johnson",
                    accountType = ClientAccountType.Couple,
                    firstName = "Sarah",
                    lastName = "Johnson",
                    email = "sarah@example.com",
                    phone = "07700 900123",
                ),
            )

            val stored =
                clients
                    .observeClients()
                    .first()
                    .single()
            assertEquals("Sarah & Michael Johnson", stored.accountName)
            assertEquals(ClientAccountType.Couple, stored.accountType)

            val contact = assertNotNull(stored.primaryContact)
            assertEquals("Sarah Johnson", contact.displayName)
            assertEquals("sarah@example.com", contact.primaryEmail)
            assertEquals("07700 900123", contact.primaryPhone)
        }

    @Test
    fun `a company with nobody named yet is still worth taking on`() =
        runTest {
            val clients = FakeClientRepository()

            viewModel(clients = clients).addClient(
                newClient(accountName = "Harbourline Coffee", accountType = ClientAccountType.Company),
            )

            val stored =
                clients
                    .observeClients()
                    .first()
                    .single()
            assertEquals("Harbourline Coffee", stored.displayName)
            assertTrue(stored.contacts.isEmpty(), "an empty contact would look reachable while being nobody")
        }

    @Test
    fun `a person named with no account name is addressed by their own name`() =
        runTest {
            val clients = FakeClientRepository()

            viewModel(clients = clients).addClient(newClient(firstName = "Ada", lastName = "Okafor"))

            val stored =
                clients
                    .observeClients()
                    .first()
                    .single()
            assertEquals("", stored.accountName, "copying the name in would freeze it against a later rename")
            assertEquals("Ada Okafor", stored.displayName)
        }

    @Test
    fun `a client with no name at all is not stored`() =
        runTest {
            val clients = FakeClientRepository()

            viewModel(clients = clients).addClient(newClient(email = "someone@example.com"))

            assertTrue(
                clients
                    .observeClients()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `a saved client appears in the list it was added from`() =
        runTest {
            val clients = FakeClientRepository()
            val viewModel = viewModel(clients = clients)

            viewModel.uiState.test {
                assertEquals(UiState.Empty, awaitItem().clients)

                viewModel.addClient(newClient(accountName = "Harbourline Coffee"))

                assertEquals(
                    "Harbourline Coffee",
                    awaitItem()
                        .clients
                        .successData()
                        .single()
                        .displayName,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun newClient(
        accountName: String = "",
        accountType: ClientAccountType = ClientAccountType.Individual,
        firstName: String = "",
        lastName: String = "",
        company: String = "",
        email: String = "",
        phone: String = "",
        notes: String = "",
    ) = NewClient(
        accountName = accountName,
        accountType = accountType,
        contactFirstName = firstName,
        contactLastName = lastName,
        company = company,
        email = email,
        phone = phone,
        notes = notes,
    )
}

private fun UiState<List<ClientSummary>>.successData(): List<ClientSummary> =
    (this as? UiState.Success)?.data ?: error("expected a success state but was $this")
