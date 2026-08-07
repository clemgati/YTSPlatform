package com.yellowtrack.platform.core.data

import app.cash.turbine.test
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.internal.SqlDelightClientRepository
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientRepositoryTest {
    private fun repository() =
        SqlDelightClientRepository(
            provider = testDatabaseProvider(),
            studioContext = LocalStudioContext(),
            clock = AppClock { TEST_NOW },
            dispatcher = Dispatchers.Unconfined,
            remote = RemoteWriter(AcceptingTransport),
        )

    @Test
    fun `stores a couple as one account with two contacts`() =
        runTest {
            val repository = repository()
            val couple = Fixtures.couple()

            repository.saveClient(couple)

            val loaded = assertNotNull(repository.observeClient(couple.id).first())

            assertEquals("Sarah & Michael Johnson", loaded.accountName)
            assertEquals(ClientAccountType.Couple, loaded.accountType)
            assertEquals(2, loaded.contacts.size)
            assertEquals(
                setOf(ClientContactRole.Primary, ClientContactRole.Partner),
                loaded.contacts.map(ClientContact::role).toSet(),
            )
            assertEquals("Sarah Johnson", loaded.primaryContact?.displayName)
        }

    @Test
    fun `billing contact falls back to the primary when none is designated`() =
        runTest {
            val repository = repository()
            val couple = Fixtures.couple()

            repository.saveClient(couple)
            val loaded = assertNotNull(repository.observeClient(couple.id).first())

            assertEquals("Sarah Johnson", loaded.billingContact?.displayName)
        }

    @Test
    fun `a company account routes invoices to its billing contact`() =
        runTest {
            val repository = repository()

            val company =
                Fixtures.client(
                    accountName = "Harborline Coffee",
                    accountType = ClientAccountType.Company,
                    contacts =
                        listOf(
                            ClientContact(
                                Fixtures.contact(firstName = "Dana", lastName = "Reyes", company = "Harborline Coffee"),
                                ClientContactRole.Primary,
                            ),
                            ClientContact(
                                Fixtures.contact(
                                    firstName = "Accounts",
                                    lastName = "Payable",
                                    company = "Harborline Coffee",
                                ),
                                ClientContactRole.Billing,
                            ),
                        ),
                )

            repository.saveClient(company)
            val loaded = assertNotNull(repository.observeClient(company.id).first())

            assertEquals("Dana Reyes", loaded.primaryContact?.displayName)
            assertEquals("Accounts Payable", loaded.billingContact?.displayName)
        }

    @Test
    fun `preserves contact methods across a round trip`() =
        runTest {
            val repository = repository()
            val couple = Fixtures.couple()

            repository.saveClient(couple)
            val loaded = assertNotNull(repository.observeClient(couple.id).first())

            val sarah = assertNotNull(loaded.contacts.firstOrNull { it.contact.firstName == "Sarah" }).contact
            assertEquals("sarah@example.com", sarah.primaryEmail)
            assertEquals("+1 555 0100", sarah.primaryPhone)
        }

    @Test
    fun `emits again when a client is added`() =
        runTest {
            val repository = repository()

            repository.observeClients().test {
                assertEquals(emptyList(), awaitItem())

                repository.saveClient(Fixtures.client(accountName = "Acme Studios"))

                assertEquals(listOf("Acme Studios"), awaitItem().map { it.accountName })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `updating a client replaces its contact links rather than accumulating them`() =
        runTest {
            val repository = repository()
            val couple = Fixtures.couple()
            repository.saveClient(couple)

            val trimmed = couple.copy(contacts = couple.contacts.take(1))
            repository.saveClient(trimmed)

            val loaded = assertNotNull(repository.observeClient(couple.id).first())
            assertEquals(1, loaded.contacts.size)
            assertEquals(
                "Sarah",
                loaded.contacts
                    .single()
                    .contact.firstName,
            )
        }

    @Test
    fun `re-adding a previously removed contact revives the link`() =
        runTest {
            val repository = repository()
            val couple = Fixtures.couple()

            repository.saveClient(couple)
            repository.saveClient(couple.copy(contacts = couple.contacts.take(1)))
            repository.saveClient(couple)

            val loaded = assertNotNull(repository.observeClient(couple.id).first())
            assertEquals(2, loaded.contacts.size)
        }

    @Test
    fun `a deleted client disappears from queries`() =
        runTest {
            val repository = repository()

            val client = Fixtures.client(accountName = "Cancelled Client")
            repository.saveClient(client)
            repository.deleteClient(client.id)

            assertNull(repository.observeClient(client.id).first())
            assertTrue(repository.observeClients().first().isEmpty())
        }

    @Test
    fun `deleting tombstones the row rather than removing it`() =
        runTest {
            val repository = repository()

            val client = Fixtures.client(accountName = "Cancelled Client")
            repository.saveClient(client)
            repository.deleteClient(client.id)

            // Saving the same identifier again revives the original row. Had the delete
            // removed it, this would insert a fresh row and created_at would move to now.
            repository.saveClient(client.copy(accountName = "Restored Client"))

            val restored = assertNotNull(repository.observeClient(client.id).first())
            assertEquals("Restored Client", restored.accountName)
            assertEquals(
                client.audit.createdAt,
                restored.audit.createdAt,
                "created_at must survive a delete, proving the row was tombstoned rather than deleted",
            )
        }

    @Test
    fun `searches by account name and by contact name`() =
        runTest {
            val repository = repository()

            repository.saveClient(Fixtures.couple(accountName = "Sarah & Michael Johnson"))
            repository.saveClient(Fixtures.client(accountName = "Harborline Coffee", contacts = emptyList()))

            assertEquals(
                listOf("Harborline Coffee"),
                repository.searchClients("harbor").first().map { it.accountName },
            )

            // "Michael" appears only on a contact, not on the account name.
            assertEquals(
                listOf("Sarah & Michael Johnson"),
                repository.searchClients("Michael").first().map { it.accountName },
            )
        }

    @Test
    fun `treats LIKE wildcards in a search term as literal characters`() =
        runTest {
            val repository = repository()

            repository.saveClient(Fixtures.client(accountName = "Studio 100%"))
            repository.saveClient(Fixtures.client(accountName = "Unrelated"))

            assertEquals(
                listOf("Studio 100%"),
                repository.searchClients("100%").first().map { it.accountName },
            )
        }

    @Test
    fun `an empty search returns every client`() =
        runTest {
            val repository = repository()
            repository.saveClient(Fixtures.client(accountName = "One"))
            repository.saveClient(Fixtures.client(accountName = "Two"))

            assertEquals(2, repository.searchClients("   ").first().size)
        }
}
