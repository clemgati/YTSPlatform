package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.internal.SqlDelightClientRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightContractRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightInvoiceRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightProjectRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightQuoteRepository
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.ContractStatus
import com.yellowtrack.platform.core.model.contract.LicenseMedium
import com.yellowtrack.platform.core.model.contract.UsageLicense
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import com.yellowtrack.platform.core.model.quote.accepted
import com.yellowtrack.platform.core.model.quote.declined
import com.yellowtrack.platform.core.model.quote.sent
import com.yellowtrack.platform.core.model.quote.toInvoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ProposalRepositoryTest {
    private val usd = CurrencyCode.USD
    private val clock = AppClock { TEST_NOW }

    private class Harness(
        provider: DatabaseProvider = testDatabaseProvider(),
        clock: AppClock,
    ) {
        val clients = SqlDelightClientRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined)
        val projects = SqlDelightProjectRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined)
        val quotes = SqlDelightQuoteRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined)
        val contracts = SqlDelightContractRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined)
        val invoices = SqlDelightInvoiceRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined)

        suspend fun seedProject(): ProjectId {
            val client = Fixtures.client()
            clients.saveClient(client)
            val project = Fixtures.project(clientId = client.id)
            projects.saveProject(project)
            return project.id
        }
    }

    private fun harness() = Harness(clock = clock)

    private fun quote(
        projectId: ProjectId,
        number: String = "QUO-001",
        status: QuoteStatus = QuoteStatus.Draft,
        lines: List<LineItem> =
            listOf(
                LineItem("Wedding coverage, eight hours", Money.ofMajor(4_000, CurrencyCode.USD)),
                LineItem("Fine art album", Money.ofMajor(600, CurrencyCode.USD), taxRateBasisPoints = 825),
            ),
        issuedAt: kotlin.time.Instant? = null,
        validUntil: kotlin.time.Instant? = null,
    ) = Quote(
        id = QuoteId.new(),
        studioId = TEST_STUDIO_ID,
        projectId = projectId,
        number = number,
        status = status,
        currency = CurrencyCode.USD,
        lines = lines,
        issuedAt = issuedAt,
        validUntil = validUntil,
        terms = "Fifty per cent retainer secures the date.",
        audit = AuditMetadata.createdAt(TEST_NOW),
    )

    private fun contract(
        projectId: ProjectId,
        title: String = "Johnson Wedding Agreement",
        status: ContractStatus = ContractStatus.Draft,
        retainer: Money? = Money.ofMajor(2_000, CurrencyCode.USD),
        license: UsageLicense? = null,
        sentAt: kotlin.time.Instant? = null,
    ) = Contract(
        id = ContractId.new(),
        studioId = TEST_STUDIO_ID,
        projectId = projectId,
        title = title,
        status = status,
        sentAt = sentAt,
        signerName = "Sarah Johnson",
        signerEmail = "sarah@example.com",
        retainerAmount = retainer,
        turnaroundDays = 42,
        revisionRounds = 2,
        weatherClause = "Outdoor coverage may be rescheduled once without charge.",
        usageLicense = license,
        audit = AuditMetadata.createdAt(TEST_NOW),
    )

    // --- Quotes ------------------------------------------------------------------------

    @Test
    fun `a stored quote keeps its lines and their per-line tax`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val original = quote(projectId)

            harness.quotes.saveQuote(original)

            val stored = assertNotNull(harness.quotes.getQuote(original.id))
            assertEquals(original.lines, stored.lines)
            assertEquals(Money.ofMajor(4_600, usd), stored.subtotal)
            // Tax applies to the album alone: 8.25% of $600.
            assertEquals(Money(4_950, usd), stored.tax)
            assertEquals(Money(464_950, usd), stored.total)
        }

    @Test
    fun `only sent quotes wait on a decision, oldest first`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()

            harness.quotes.saveQuote(quote(projectId, number = "QUO-DRAFT"))
            harness.quotes.saveQuote(
                quote(projectId, number = "QUO-RECENT", status = QuoteStatus.Sent, issuedAt = TEST_NOW - 2.days),
            )
            harness.quotes.saveQuote(
                quote(projectId, number = "QUO-OLDEST", status = QuoteStatus.Sent, issuedAt = TEST_NOW - 20.days),
            )
            harness.quotes.saveQuote(
                quote(projectId, number = "QUO-DONE", status = QuoteStatus.Accepted),
            )

            assertEquals(
                listOf("QUO-OLDEST", "QUO-RECENT"),
                harness.quotes
                    .observeAwaitingDecision()
                    .first()
                    .map(Quote::number),
            )
        }

    @Test
    fun `a lapsed quote reads as expired but is still stored as sent`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val lapsed =
                quote(projectId, status = QuoteStatus.Sent, issuedAt = TEST_NOW - 40.days)
                    .copy(validUntil = TEST_NOW - 10.days)

            harness.quotes.saveQuote(lapsed)

            val stored = assertNotNull(harness.quotes.getQuote(lapsed.id))
            assertEquals(QuoteStatus.Sent, stored.status)
            assertEquals(QuoteStatus.Expired, stored.effectiveStatus(TEST_NOW))
            assertTrue(
                harness.quotes
                    .observeAwaitingDecision()
                    .first()
                    .isNotEmpty(),
                "an expired quote is still the studio's to chase",
            )
        }

    @Test
    fun `saving a quote read back as expired does not freeze it as expired`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val lapsed =
                quote(projectId, status = QuoteStatus.Sent, issuedAt = TEST_NOW - 40.days)
                    .copy(validUntil = TEST_NOW - 10.days)
            harness.quotes.saveQuote(lapsed)

            // The screen shows Expired; saving that back must not lose the real status,
            // or extending the validity date would leave the quote permanently expired.
            val asShown = assertNotNull(harness.quotes.getQuote(lapsed.id))
            harness.quotes.saveQuote(asShown.copy(status = asShown.effectiveStatus(TEST_NOW)))

            val extended =
                assertNotNull(harness.quotes.getQuote(lapsed.id)).copy(validUntil = TEST_NOW + 14.days)
            harness.quotes.saveQuote(extended)

            val reread = assertNotNull(harness.quotes.getQuote(lapsed.id))
            assertEquals(QuoteStatus.Sent, reread.effectiveStatus(TEST_NOW))
        }

    @Test
    fun `sending stamps the issue date and its own expiry`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val draft = quote(projectId)
            harness.quotes.saveQuote(draft)

            harness.quotes.saveQuote(draft.sent(at = TEST_NOW, validFor = 30.days))

            val stored = assertNotNull(harness.quotes.getQuote(draft.id))
            assertEquals(QuoteStatus.Sent, stored.status)
            assertEquals(TEST_NOW, stored.issuedAt)
            assertEquals(TEST_NOW + 30.days, stored.validUntil)
        }

    @Test
    fun `accepting and declining clear each other's stamp`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val proposal = quote(projectId, status = QuoteStatus.Sent, issuedAt = TEST_NOW)
            harness.quotes.saveQuote(proposal)

            harness.quotes.saveQuote(proposal.accepted(at = TEST_NOW + 1.days))
            val acceptedRow = assertNotNull(harness.quotes.getQuote(proposal.id))
            assertEquals(TEST_NOW + 1.days, acceptedRow.acceptedAt)
            assertNull(acceptedRow.declinedAt)

            harness.quotes.saveQuote(acceptedRow.declined(at = TEST_NOW + 2.days, reason = "Went elsewhere"))
            val declinedRow = assertNotNull(harness.quotes.getQuote(proposal.id))
            assertEquals(QuoteStatus.Declined, declinedRow.status)
            assertEquals(TEST_NOW + 2.days, declinedRow.declinedAt)
            assertNull(declinedRow.acceptedAt, "a declined quote must not still claim it was accepted")
        }

    @Test
    fun `an accepted quote becomes an invoice for exactly the agreed figure`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val agreed = quote(projectId, status = QuoteStatus.Sent, issuedAt = TEST_NOW).accepted(TEST_NOW)
            harness.quotes.saveQuote(agreed)

            val raised = agreed.toInvoice(number = "INV-001", now = TEST_NOW, dueAt = TEST_NOW + 14.days)
            harness.invoices.saveInvoice(raised)

            val stored = assertNotNull(harness.invoices.getInvoice(raised.id))
            assertEquals(agreed.lines, stored.lines)
            assertEquals(agreed.total, stored.total)
            assertEquals(agreed.projectId, stored.projectId)
        }

    @Test
    fun `an invoice raised from a quote owes nothing until it is sent`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val agreed = quote(projectId, status = QuoteStatus.Sent, issuedAt = TEST_NOW).accepted(TEST_NOW)

            val raised = agreed.toInvoice(number = "INV-001", now = TEST_NOW, dueAt = TEST_NOW - 1.days)
            harness.invoices.saveInvoice(raised)

            val stored = assertNotNull(harness.invoices.getInvoice(raised.id))
            assertTrue(
                stored.outstanding(TEST_NOW).isZero,
                "a draft raised from a quote must not appear in money owed, nor overdue",
            )
        }

    @Test
    fun `a deleted quote leaves the awaiting list`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val proposal = quote(projectId, status = QuoteStatus.Sent, issuedAt = TEST_NOW)
            harness.quotes.saveQuote(proposal)

            harness.quotes.deleteQuote(proposal.id)

            assertNull(harness.quotes.getQuote(proposal.id))
            assertTrue(
                harness.quotes
                    .observeAwaitingDecision()
                    .first()
                    .isEmpty(),
            )
        }

    // --- Contracts ---------------------------------------------------------------------

    @Test
    fun `a stored contract keeps the terms that settle arguments`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val agreement = contract(projectId)

            harness.contracts.saveContract(agreement)

            val stored = assertNotNull(harness.contracts.getContract(agreement.id))
            assertEquals(42, stored.turnaroundDays)
            assertEquals(2, stored.revisionRounds)
            assertEquals(Money.ofMajor(2_000, usd), stored.retainerAmount)
            assertEquals(agreement.weatherClause, stored.weatherClause)
            assertTrue(!stored.isRetainerRefundable, "retainers default to non-refundable")
        }

    @Test
    fun `a usage licence survives storage with its renewal date intact`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val licensed =
                contract(
                    projectId,
                    license =
                        UsageLicense(
                            media = listOf(LicenseMedium.Web, LicenseMedium.PaidSocial),
                            territory = "United Kingdom",
                            durationMonths = 12,
                            isExclusive = true,
                            startsOn = LocalDate(2026, 3, 1),
                        ),
                )

            harness.contracts.saveContract(licensed)

            val stored = assertNotNull(harness.contracts.getContract(licensed.id)).usageLicense
            assertNotNull(stored)
            assertEquals(listOf(LicenseMedium.Web, LicenseMedium.PaidSocial), stored.media)
            assertTrue(stored.isExclusive)
            assertEquals(LocalDate(2027, 3, 1), stored.expiresOn())
            assertTrue(stored.isExpired(LocalDate(2027, 4, 1)))
        }

    @Test
    fun `a contract without a licence stores none rather than an empty one`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val agreement = contract(projectId, license = null)

            harness.contracts.saveContract(agreement)

            assertNull(assertNotNull(harness.contracts.getContract(agreement.id)).usageLicense)
        }

    @Test
    fun `only sent contracts wait on a signature, oldest first`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()

            harness.contracts.saveContract(contract(projectId, title = "Draft"))
            harness.contracts.saveContract(
                contract(projectId, title = "Recent", status = ContractStatus.Sent, sentAt = TEST_NOW - 1.days),
            )
            harness.contracts.saveContract(
                contract(projectId, title = "Oldest", status = ContractStatus.Sent, sentAt = TEST_NOW - 9.days),
            )
            harness.contracts.saveContract(contract(projectId, title = "Signed", status = ContractStatus.Signed))

            assertEquals(
                listOf("Oldest", "Recent"),
                harness.contracts
                    .observeAwaitingSignature()
                    .first()
                    .map(Contract::title),
            )
        }

    @Test
    fun `a date is only held once the contract is signed and the retainer is paid`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val signed =
                contract(projectId, status = ContractStatus.Signed)
                    .copy(signedAt = TEST_NOW)
            harness.contracts.saveContract(signed)

            val stored = assertNotNull(harness.contracts.getContract(signed.id))
            assertTrue(stored.isSigned)
            assertTrue(!stored.isBindingWith(retainerPaid = false), "a signature alone does not hold a date")
            assertTrue(stored.isBindingWith(retainerPaid = true))
        }

    @Test
    fun `a signed contract with no retainer binds on the signature alone`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val signed =
                contract(projectId, status = ContractStatus.Signed, retainer = null)
                    .copy(signedAt = TEST_NOW)
            harness.contracts.saveContract(signed)

            val stored = assertNotNull(harness.contracts.getContract(signed.id))
            assertNull(stored.retainerAmount)
            assertTrue(stored.isBindingWith(retainerPaid = false))
        }

    @Test
    fun `proposals are found by the booking they belong to`() =
        runTest {
            val harness = harness()
            val theirs = harness.seedProject()
            val others = harness.seedProject()

            harness.quotes.saveQuote(quote(theirs, number = "QUO-THEIRS"))
            harness.quotes.saveQuote(quote(others, number = "QUO-OTHERS"))
            harness.contracts.saveContract(contract(theirs, title = "Theirs"))
            harness.contracts.saveContract(contract(others, title = "Others"))

            assertEquals(
                listOf("QUO-THEIRS"),
                harness.quotes
                    .observeQuotesForProject(theirs)
                    .first()
                    .map(Quote::number),
            )
            assertEquals(
                listOf("Theirs"),
                harness.contracts
                    .observeContractsForProject(theirs)
                    .first()
                    .map(Contract::title),
            )
        }
}
