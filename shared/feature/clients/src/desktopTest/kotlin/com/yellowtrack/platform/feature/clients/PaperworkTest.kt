package com.yellowtrack.platform.feature.clients

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.ContractStatus
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.testing.TestData
import com.yellowtrack.platform.core.ui.removal.Removal
import com.yellowtrack.platform.feature.clients.presentation.project.mapper.toDetailsModel
import com.yellowtrack.platform.feature.clients.presentation.project.model.PaperworkItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A quote or a contract can be found after it stopped needing an answer.
 *
 * This is the payment case again, in two more places. The Ledger lists a quote only while
 * `isAwaitingDecision` and a contract only until it holds its date — so accepting a quote,
 * or signing a contract and taking the retainer, removes it from the only screen that ever
 * showed it. The record still exists and still holds its booking in place.
 *
 * That was survivable until bookings began refusing to be removed on account of them: the
 * studio is told a quote is in the way and has nowhere to go and look at it. So they are
 * listed on the booking they belong to, which is also where the refusal is read.
 */
class PaperworkTest {
    @Test
    fun `an accepted quote is still listed`() {
        val paperwork = paperwork(quotes = listOf(quote("q1", "2026-014", QuoteStatus.Accepted)))

        assertEquals(
            listOf("Quote 2026-014"),
            paperwork.map { it.title },
            "accepting a quote takes it off the Ledger; if it is nowhere else, the booking " +
                "it holds can never be cleared",
        )
        assertEquals("Accepted", paperwork.single().statusLabel)
    }

    @Test
    fun `a signed contract is still listed`() {
        val paperwork = paperwork(contracts = listOf(contract("c1", "Wedding coverage", ContractStatus.Signed)))

        assertEquals(listOf("Wedding coverage"), paperwork.map { it.title })
        assertEquals(
            "Signed",
            paperwork.single().statusLabel,
            "a contract that did its job is the one the Ledger stops showing",
        )
    }

    @Test
    fun `a declined quote is listed too`() {
        val paperwork = paperwork(quotes = listOf(quote("q1", "2026-014", QuoteStatus.Declined)))

        assertTrue(
            paperwork.isNotEmpty(),
            "a quote that was turned down is still a record holding the booking, and is " +
                "exactly the kind a studio would want rid of",
        )
    }

    @Test
    fun `quotes and contracts appear together, and say which is which`() {
        val paperwork =
            paperwork(
                quotes = listOf(quote("q1", "2026-014", QuoteStatus.Draft)),
                contracts = listOf(contract("c1", "Wedding coverage", ContractStatus.Sent)),
            )

        assertEquals(
            listOf(PaperworkItem.Kind.Quote, PaperworkItem.Kind.Contract),
            paperwork.map { it.kind },
            "the kind decides which repository a removal goes to, so the row has to carry it",
        )
    }

    @Test
    fun `a booking with no paperwork has an empty list rather than a heading`() {
        assertTrue(paperwork().isEmpty())
    }

    // -- Fixtures ----------------------------------------------------------------------------

    private fun paperwork(
        quotes: List<Quote> = emptyList(),
        contracts: List<Contract> = emptyList(),
    ) = project
        .toDetailsModel(
            client = client,
            sessions = emptyList(),
            tasks = emptyList(),
            deliverables = emptyList(),
            contract = null,
            quotes = quotes,
            contracts = contracts,
            now = NOW,
            removal = Removal.Available,
        ).paperwork

    private fun quote(
        id: String,
        number: String,
        status: QuoteStatus,
    ) = Quote(
        id = QuoteId(id),
        studioId = client.studioId,
        projectId = project.id,
        number = number,
        status = status,
        currency = CurrencyCode.GBP,
        lines =
            listOf(
                LineItem(
                    description = "Coverage",
                    unitPrice = Money(minorUnits = 450_000, currency = CurrencyCode.GBP),
                ),
            ),
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun contract(
        id: String,
        title: String,
        status: ContractStatus,
    ) = Contract(
        id = ContractId(id),
        studioId = client.studioId,
        projectId = project.id,
        title = title,
        status = status,
        audit = AuditMetadata.createdAt(NOW),
    )

    private val client: Client = TestData.couple()

    private val project =
        Project(
            id = ProjectId("project-1"),
            studioId = client.studioId,
            clientId = client.id,
            name = "Okafor — Wedding",
            serviceLine = ServiceLine.Wedding,
            status = ProjectStatus.Booked,
            audit = AuditMetadata.createdAt(NOW),
        )

    private companion object {
        val NOW: Instant = Instant.fromEpochMilliseconds(1_781_000_000_000)
    }
}
