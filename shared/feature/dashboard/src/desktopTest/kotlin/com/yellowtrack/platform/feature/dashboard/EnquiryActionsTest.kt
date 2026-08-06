package com.yellowtrack.platform.feature.dashboard

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.core.model.lead.LeadSource
import com.yellowtrack.platform.core.model.lead.LeadStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeGearRepository
import com.yellowtrack.platform.core.testing.FakeLeadRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.FakeStorageVolumeRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.FakeSyncConflictRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.dashboard.presentation.DashboardViewModel
import com.yellowtrack.platform.feature.dashboard.presentation.model.NewEnquiry
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
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class EnquiryActionsTest {
    private val clock = TestAppClock()
    private val studioId = LocalStudioContext.LOCAL_STUDIO_ID

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Removing an enquiry -----------------------------------------------------------------

    /**
     * The reachability half, which is the part that matters.
     *
     * The awaiting-reply list holds only what has never been answered, so replying to an
     * enquiry took it off the only screen in the application that showed leads at all. A
     * delete reachable solely from that list would leave exactly the enquiries a studio
     * most wants rid of — the spam it answered, the duplicate it marked lost — permanently
     * out of reach.
     */
    @Test
    fun `an answered enquiry is still listed`() =
        runTest {
            val answered = lead(firstResponseAt = TestAppClock.DEFAULT_NOW, status = LeadStatus.Contacted)
            val viewModel = viewModel(FakeLeadRepository(listOf(answered)))

            val summary = viewModel.summary()

            assertTrue(
                summary.enquiriesAwaitingReply.isEmpty(),
                "it has been answered, so it is correctly absent from what needs a reply",
            )
            assertEquals(
                listOf(answered.id),
                summary.allEnquiries.map { it.id },
                "and it must still be reachable somewhere, or it can never be removed",
            )
        }

    // -- Turning one into a client --------------------------------------------------------

    /**
     * `Lead.convertedClientId` has existed since the schema was first written and nothing
     * ever set it, so no studio could say which enquiries turned into work.
     */
    @Test
    fun `converting an enquiry creates the client it describes and links the two`() =
        runTest {
            val enquiry =
                lead(firstResponseAt = null, status = LeadStatus.Contacted)
                    .copy(name = "Ada Okafor", email = "ada@okafor.example", phone = "+44 7700 900123")
            val leads = FakeLeadRepository(listOf(enquiry))
            val clients = FakeClientRepository()
            val viewModel = viewModel(leads, clients = clients)

            viewModel.convertEnquiryToClient(enquiry.id)

            val client = assertNotNull(clients.observeClients().first().lastOrNull(), "no client was created")
            assertEquals("Ada Okafor", client.accountName)
            assertEquals(
                "ada@okafor.example",
                client.contacts
                    .single()
                    .contact.emails
                    .single()
                    .value,
            )

            val converted = assertNotNull(leads.getLead(enquiry.id))
            assertEquals(client.id, converted.convertedClientId, "the link is what makes the count possible")
            assertEquals(LeadStatus.Won, converted.status)
            assertNotNull(converted.firstResponseAt, "winning one is answering it")
        }

    /** A screen can be pressed twice, and two clients for one person is worse than no button. */
    @Test
    fun `converting twice does not make a second client`() =
        runTest {
            val enquiry = lead(firstResponseAt = null, status = LeadStatus.Contacted)
            val leads = FakeLeadRepository(listOf(enquiry))
            val clients = FakeClientRepository()
            val viewModel = viewModel(leads, clients = clients)

            viewModel.convertEnquiryToClient(enquiry.id)
            viewModel.convertEnquiryToClient(enquiry.id)

            assertEquals(1, clients.observeClients().first().size)
        }

    @Test
    fun `the dashboard says what became of them`() =
        runTest {
            val enquiry = lead(firstResponseAt = null, status = LeadStatus.Contacted)
            val lost = lead(firstResponseAt = TestAppClock.DEFAULT_NOW, status = LeadStatus.Lost)
            val leads = FakeLeadRepository(listOf(enquiry, lost))
            val viewModel = viewModel(leads)

            viewModel.convertEnquiryToClient(enquiry.id)

            val outcomes = assertNotNull(viewModel.summary().outcomes)
            assertEquals("50% of settled enquiries became clients", outcomes.headline)
            assertEquals("1 became clients, 1 went elsewhere, 0 still open.", outcomes.detail)
        }

    @Test
    fun `a won enquiry is still listed`() =
        runTest {
            val won = lead(firstResponseAt = TestAppClock.DEFAULT_NOW, status = LeadStatus.Won)
            val viewModel = viewModel(FakeLeadRepository(listOf(won)))

            val summary = viewModel.summary()

            assertEquals(listOf("Won"), summary.allEnquiries.map { it.statusLabel })
        }

    @Test
    fun `an enquiry says where it got to`() =
        runTest {
            val answered = lead(firstResponseAt = TestAppClock.DEFAULT_NOW, status = LeadStatus.ProposalSent)
            val viewModel = viewModel(FakeLeadRepository(listOf(answered)))

            assertEquals(
                listOf("Replied"),
                viewModel.summary().allEnquiries.map { it.statusLabel },
                "the working statuses matter while working the enquiry and not at all when " +
                    "looking for one to delete",
            )
        }

    @Test
    fun `an enquiry can be removed`() =
        runTest {
            val spam = lead(firstResponseAt = TestAppClock.DEFAULT_NOW, status = LeadStatus.Lost)
            val leads = FakeLeadRepository(listOf(spam))
            val viewModel = viewModel(leads)

            viewModel.deleteEnquiry(spam.id)

            assertTrue(
                leads.observeLeads().first().isEmpty(),
                "a lead is a leaf — nothing points at one — so there is nothing to hold it",
            )
        }

    /**
     * Correcting an enquiry must not disturb the clock it is measured by.
     *
     * The form shows a name, a source and a budget. It does not show when the enquiry
     * arrived or when it was first replied to — and those two are the entire basis of
     * response time, which the application calls the strongest predictor of whether an
     * enquiry books. Rebuilt from the form, correcting a misspelled name would reset both.
     */
    @Test
    fun `correcting an enquiry keeps when it arrived and when it was answered`() =
        runTest {
            val arrived = TestAppClock.DEFAULT_NOW
            val answered = TestAppClock.DEFAULT_NOW
            val original =
                lead(firstResponseAt = answered, status = LeadStatus.Contacted)
                    .copy(receivedAt = arrived)
            val leads = FakeLeadRepository(listOf(original))
            val viewModel = viewModel(leads)

            viewModel.saveEnquiry(
                NewEnquiry(
                    name = "Jamie King",
                    source = LeadSource.Instagram,
                    serviceLine = ServiceLine.Wedding,
                ),
                existingId = original.id,
            )

            val stored = leads.observeLeads().first().single()
            assertEquals(original.id, stored.id, "an edit corrects the enquiry rather than logging a second")
            assertEquals("Jamie King", stored.name)
            assertEquals(arrived, stored.receivedAt, "the clock starts when the message arrived")
            assertEquals(answered, stored.firstResponseAt, "and stops when it was first answered")
            assertEquals(LeadStatus.Contacted, stored.status, "how far it has got is not the form's business")
        }

    @Test
    fun `an enquiry with no name is not saved`() =
        runTest {
            val original = lead()
            val leads = FakeLeadRepository(listOf(original))
            val viewModel = viewModel(leads)

            viewModel.saveEnquiry(
                NewEnquiry(name = "   ", source = LeadSource.Other, serviceLine = ServiceLine.Wedding),
                existingId = original.id,
            )

            assertEquals(
                original.name,
                leads
                    .observeLeads()
                    .first()
                    .single()
                    .name,
                "a blank name leaves a row on the dashboard nobody can identify",
            )
        }

    private suspend fun DashboardViewModel.summary() =
        uiState
            .first { it.summary is UiState.Success }
            .summary
            .let { (it as UiState.Success).data }

    private fun viewModel(
        leads: FakeLeadRepository,
        conflicts: FakeSyncConflictRepository = FakeSyncConflictRepository(),
        clients: FakeClientRepository = FakeClientRepository(),
    ) = DashboardViewModel(
        clientRepository = clients,
        projectRepository = FakeProjectRepository(),
        sessionRepository = FakeSessionRepository(),
        leadRepository = leads,
        studioProfileRepository = FakeStudioProfileRepository(),
        conflictRepository = conflicts,
        studioContext = LocalStudioContext(),
        gearRepository = FakeGearRepository(),
        volumeRepository = FakeStorageVolumeRepository(),
        clock = clock,
    )

    private fun lead(
        firstResponseAt: kotlin.time.Instant? = null,
        status: LeadStatus = LeadStatus.New,
    ) = Lead(
        id = LeadId.new(),
        studioId = studioId,
        name = "Priya & Tom",
        source = LeadSource.ClientReferral,
        status = status,
        receivedAt = TestAppClock.DEFAULT_NOW,
        firstResponseAt = firstResponseAt,
        audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
    )

    @Test
    fun `marking replied stamps the response time and advances the status`() =
        runTest {
            val enquiry = lead()
            val leads = FakeLeadRepository(listOf(enquiry))
            clock.advanceBy(3.hours)

            viewModel(leads).markEnquiryReplied(enquiry.id)

            val stored = assertNotNull(leads.getLead(enquiry.id))
            assertEquals(3.hours, stored.responseTime)
            assertEquals(LeadStatus.Contacted, stored.status)
        }

    @Test
    fun `marking replied a second time does not overwrite the first response`() =
        runTest {
            val firstReply = TestAppClock.DEFAULT_NOW + 1.hours
            val enquiry = lead(firstResponseAt = firstReply, status = LeadStatus.Contacted)
            val leads = FakeLeadRepository(listOf(enquiry))
            clock.advanceBy(48.hours)

            viewModel(leads).markEnquiryReplied(enquiry.id)

            assertEquals(
                firstReply,
                assertNotNull(leads.getLead(enquiry.id)).firstResponseAt,
                "the figure that predicts bookings is time to FIRST response",
            )
        }

    @Test
    fun `a replied enquiry leaves the waiting queue`() =
        runTest {
            val enquiry = lead()
            val leads = FakeLeadRepository(listOf(enquiry))

            assertEquals(1, leads.observeAwaitingResponse().first().size)

            viewModel(leads).markEnquiryReplied(enquiry.id)

            assertTrue(leads.observeAwaitingResponse().first().isEmpty())
        }

    @Test
    fun `a logged enquiry starts unanswered so it appears in the queue`() =
        runTest {
            val leads = FakeLeadRepository()

            viewModel(leads).saveEnquiry(
                NewEnquiry(
                    name = "June wedding enquiry",
                    source = LeadSource.Instagram,
                    serviceLine = ServiceLine.Wedding,
                    email = "hello@example.com",
                ),
            )

            val stored = leads.observeAwaitingResponse().first().single()
            assertEquals("June wedding enquiry", stored.name)
            assertEquals(LeadStatus.New, stored.status)
            assertNull(stored.firstResponseAt)
            assertEquals(TestAppClock.DEFAULT_NOW, stored.receivedAt)
        }

    @Test
    fun `a logged enquiry parses its budget range`() =
        runTest {
            val leads = FakeLeadRepository()

            viewModel(leads).saveEnquiry(
                NewEnquiry(
                    name = "Brand film",
                    source = LeadSource.VendorReferral,
                    serviceLine = ServiceLine.Video,
                    budgetLow = "3,000",
                    budgetHigh = "5000.50",
                ),
            )

            val stored = leads.observeLeads().first().single()
            assertEquals(Money.ofMajor(3_000, CurrencyCode.USD), stored.budgetLow)
            assertEquals(Money(500_050, CurrencyCode.USD), stored.budgetHigh)
        }

    @Test
    fun `an enquiry with no budget stated is saved without one`() =
        runTest {
            val leads = FakeLeadRepository()

            viewModel(leads).saveEnquiry(
                NewEnquiry(
                    name = "No budget mentioned",
                    source = LeadSource.Website,
                    serviceLine = ServiceLine.Portrait,
                ),
            )

            val stored = leads.observeLeads().first().single()
            assertNull(stored.budgetLow)
            assertNull(stored.budgetHigh)
        }
}
