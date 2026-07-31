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
import com.yellowtrack.platform.core.testing.FakeLeadRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.FakeSyncConflictRepository
import com.yellowtrack.platform.core.testing.TestAppClock
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

    private fun viewModel(
        leads: FakeLeadRepository,
        conflicts: FakeSyncConflictRepository = FakeSyncConflictRepository(),
    ) = DashboardViewModel(
        clientRepository = FakeClientRepository(),
        projectRepository = FakeProjectRepository(),
        sessionRepository = FakeSessionRepository(),
        leadRepository = leads,
        studioProfileRepository = FakeStudioProfileRepository(),
        conflictRepository = conflicts,
        studioContext = LocalStudioContext(),
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

            viewModel(leads).addEnquiry(
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

            viewModel(leads).addEnquiry(
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

            viewModel(leads).addEnquiry(
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
