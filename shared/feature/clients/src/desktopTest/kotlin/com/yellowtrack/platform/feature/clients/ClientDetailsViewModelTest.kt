package com.yellowtrack.platform.feature.clients

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.contact.ContactMethod
import com.yellowtrack.platform.core.model.contact.ContactMethodLabel
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.testing.TestData
import com.yellowtrack.platform.feature.clients.presentation.details.ClientDetailsViewModel
import com.yellowtrack.platform.feature.clients.presentation.details.model.NewProject
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Opening a booking against a client — the step that makes every money-layer form usable,
 * since a quote, invoice, and contract all attach to one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientDetailsViewModelTest {
    private val usd = CurrencyCode.USD
    private val clock = TestAppClock()
    private val client = TestData.couple()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        projects: FakeProjectRepository = FakeProjectRepository(),
        clients: FakeClientRepository = FakeClientRepository(listOf(client)),
    ) = ClientDetailsViewModel(
        clientId = client.id,
        clientRepository = clients,
        projectRepository = projects,
        sessionRepository = FakeSessionRepository(),
        studioProfileRepository = FakeStudioProfileRepository(),
        studioContext = LocalStudioContext(),
        clock = clock,
    )

    private fun newProject(
        name: String = "Johnson Wedding",
        serviceLine: ServiceLine = ServiceLine.Wedding,
        status: ProjectStatus = ProjectStatus.Enquiry,
        contractValue: String = "",
        notes: String = "",
    ) = NewProject(
        name = name,
        serviceLine = serviceLine,
        status = status,
        contractValue = contractValue,
        notes = notes,
    )

    @Test
    fun `a booking is opened against the client whose page it was opened from`() =
        runTest {
            val projects = FakeProjectRepository()

            viewModel(projects).addProject(newProject(contractValue = "4500"))

            val stored =
                projects
                    .observeProjects()
                    .first()
                    .single()
            assertEquals(client.id, stored.clientId)
            assertEquals("Johnson Wedding", stored.name)
            assertEquals(ServiceLine.Wedding, stored.serviceLine)
            assertEquals(Money.ofMajor(4_500, usd), stored.contractValue)
        }

    @Test
    fun `an enquiry is stamped as enquired and not as booked`() =
        runTest {
            val projects = FakeProjectRepository()

            viewModel(projects).addProject(newProject(status = ProjectStatus.Enquiry))

            val stored =
                projects
                    .observeProjects()
                    .first()
                    .single()
            assertEquals(TestAppClock.DEFAULT_NOW, stored.enquiredAt)
            assertNull(stored.bookedAt, "nothing is held until a date is actually taken")
        }

    @Test
    fun `a booking entered as booked records when the date was taken`() =
        runTest {
            val projects = FakeProjectRepository()

            viewModel(projects).addProject(newProject(status = ProjectStatus.Booked))

            val stored =
                projects
                    .observeProjects()
                    .first()
                    .single()
            assertEquals(
                TestAppClock.DEFAULT_NOW,
                stored.bookedAt,
                "a booking with no booked date cannot say when the date was taken",
            )
            assertNotNull(stored.enquiredAt, "even a job entered already booked was asked about first")
        }

    @Test
    fun `a booking with no agreed value yet is still worth opening`() =
        runTest {
            val projects = FakeProjectRepository()

            viewModel(projects).addProject(newProject(contractValue = ""))

            assertNull(
                projects
                    .observeProjects()
                    .first()
                    .single()
                    .contractValue,
            )
        }

    @Test
    fun `a booking with an unreadable value is not stored`() =
        runTest {
            val projects = FakeProjectRepository()

            viewModel(projects).addProject(newProject(contractValue = "four thousand"))

            assertTrue(
                projects
                    .observeProjects()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `a booking with no name is not stored`() =
        runTest {
            val projects = FakeProjectRepository()

            viewModel(projects).addProject(newProject(name = "  "))

            assertTrue(
                projects
                    .observeProjects()
                    .first()
                    .isEmpty(),
            )
        }

    // --- Correcting an account ---------------------------------------------------------

    @Test
    fun `editing writes back the account and the person it shows`() =
        runTest {
            val clients = FakeClientRepository(listOf(client))

            viewModel(clients = clients).updateClient(
                editedClient(
                    accountName = "Sarah & Michael Johnson-Reid",
                    firstName = "Sarah",
                    lastName = "Johnson-Reid",
                    email = "sarah@newdomain.com",
                ),
            )

            val stored =
                clients
                    .observeClients()
                    .first()
                    .single()
            assertEquals("Sarah & Michael Johnson-Reid", stored.accountName)
            assertEquals("Sarah Johnson-Reid", assertNotNull(stored.primaryContact).displayName)
            assertEquals("sarah@newdomain.com", stored.primaryContact?.primaryEmail)
            assertEquals(client.id, stored.id, "editing must not mint a new account")
        }

    @Test
    fun `editing keeps every contact the form does not show`() =
        runTest {
            val planner =
                ClientContact(
                    contact =
                        Contact(
                            id = ContactId.new(),
                            studioId = client.studioId,
                            firstName = "Priya",
                            lastName = "Shah",
                            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
                        ),
                    role = ClientContactRole.Planner,
                )
            val withPlanner = client.copy(contacts = client.contacts + planner)
            val clients = FakeClientRepository(listOf(withPlanner))

            viewModel(clients = clients).updateClient(editedClient(firstName = "Sarah"))

            val stored =
                clients
                    .observeClients()
                    .first()
                    .single()
            assertTrue(
                stored.contacts.any { it.role == ClientContactRole.Planner },
                "the form shows one contact; rebuilding the list from it would delete the rest",
            )
        }

    @Test
    fun `clearing the email leaves the person's other numbers alone`() =
        runTest {
            val contact =
                Contact(
                    id = ContactId.new(),
                    studioId = client.studioId,
                    firstName = "Sarah",
                    lastName = "Johnson",
                    emails = listOf(ContactMethod("sarah@example.com")),
                    phones =
                        listOf(
                            ContactMethod("07700 900123"),
                            ContactMethod("0208 555 0100", ContactMethodLabel.Work),
                        ),
                    audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
                )
            val clients =
                FakeClientRepository(
                    listOf(
                        client.copy(
                            contacts = listOf(ClientContact(contact, ClientContactRole.Primary)),
                        ),
                    ),
                )

            viewModel(clients = clients).updateClient(
                editedClient(firstName = "Sarah", lastName = "Johnson", email = "", phone = "07700 900123"),
            )

            val stored =
                assertNotNull(
                    clients
                        .observeClients()
                        .first()
                        .single()
                        .primaryContact,
                )
            assertTrue(stored.emails.isEmpty(), "the email was cleared deliberately")
            assertEquals(2, stored.phones.size, "clearing one field must not discard the others")
        }

    @Test
    fun `an edit that removes every name is refused`() =
        runTest {
            val clients = FakeClientRepository(listOf(client))

            viewModel(clients = clients).updateClient(editedClient(accountName = ""))

            assertEquals(
                client.accountName,
                clients
                    .observeClients()
                    .first()
                    .single()
                    .accountName,
            )
        }

    private fun editedClient(
        accountName: String = client.accountName,
        firstName: String = "",
        lastName: String = "",
        company: String = "",
        email: String = "",
        phone: String = "",
        notes: String = "",
    ) = NewClient(
        accountName = accountName,
        accountType = client.accountType,
        contactFirstName = firstName,
        contactLastName = lastName,
        company = company,
        email = email,
        phone = phone,
        notes = notes,
    )
}
