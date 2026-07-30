package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.ContractStatus
import com.yellowtrack.platform.core.model.contract.LicenseMedium
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.invoice.PaymentMethod
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeCodbRepository
import com.yellowtrack.platform.core.testing.FakeContractRepository
import com.yellowtrack.platform.core.testing.FakeExpenseRepository
import com.yellowtrack.platform.core.testing.FakeInvoiceRepository
import com.yellowtrack.platform.core.testing.FakePostProductionRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeQuoteRepository
import com.yellowtrack.platform.core.testing.FakeServiceTemplateRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.RecordingDocumentSink
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.ledger.presentation.LedgerContent
import com.yellowtrack.platform.feature.ledger.presentation.LedgerViewModel
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractSignature
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractStage
import com.yellowtrack.platform.feature.ledger.presentation.model.NewContract
import com.yellowtrack.platform.feature.ledger.presentation.model.NewUsageLicense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * The contract lifecycle as it is driven from the Ledger: drawn up, sent, signed, and — the
 * part that actually holds a date — paid for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContractActionsTest {
    private val usd = CurrencyCode.USD
    private val studioId = LocalStudioContext.LOCAL_STUDIO_ID
    private val now = TestAppClock.DEFAULT_NOW

    private val clientId = ClientId.new()
    private val projectId = ProjectId.new()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Harness(
        val clock: TestAppClock,
        val contracts: FakeContractRepository,
        val invoices: FakeInvoiceRepository,
        val viewModel: LedgerViewModel,
    )

    private fun harness(
        contracts: FakeContractRepository = FakeContractRepository(),
        invoices: FakeInvoiceRepository = FakeInvoiceRepository(),
    ): Harness {
        val clock = TestAppClock()
        val expenses = FakeExpenseRepository()

        return Harness(
            clock = clock,
            contracts = contracts,
            invoices = invoices,
            viewModel =
                LedgerViewModel(
                    invoiceRepository = invoices,
                    quoteRepository = FakeQuoteRepository(),
                    contractRepository = contracts,
                    expenseRepository = expenses,
                    codbRepository = FakeCodbRepository(expenses = expenses),
                    serviceTemplateRepository = FakeServiceTemplateRepository(),
                    projectRepository = FakeProjectRepository(listOf(project())),
                    sessionRepository = FakeSessionRepository(),
                    postProductionRepository = FakePostProductionRepository(),
                    clientRepository = FakeClientRepository(listOf(client())),
                    studioProfileRepository = FakeStudioProfileRepository(),
                    documentSink = RecordingDocumentSink(),
                    studioContext = LocalStudioContext(),
                    clock = clock,
                    timeZone = TimeZone.UTC,
                ),
        )
    }

    private fun client() =
        Client(
            id = clientId,
            studioId = studioId,
            accountName = "Sarah & Michael Johnson",
            accountType = ClientAccountType.Couple,
            audit = AuditMetadata.createdAt(now),
        )

    private fun project() =
        Project(
            id = projectId,
            studioId = studioId,
            clientId = clientId,
            name = "Johnson Wedding",
            serviceLine = ServiceLine.Wedding,
            status = ProjectStatus.Enquiry,
            audit = AuditMetadata.createdAt(now),
        )

    private fun newContract(
        retainerAmount: String = "2000",
        turnaroundDays: String = "45",
        revisionRounds: String = "2",
        license: NewUsageLicense? = null,
        sendNow: Boolean = true,
    ) = NewContract(
        projectId = projectId,
        title = "Johnson Wedding Agreement",
        retainerAmount = retainerAmount,
        isRetainerRefundable = false,
        turnaroundDays = turnaroundDays,
        revisionRounds = revisionRounds,
        cancellationTerms = "The retainer is non-refundable.",
        rescheduleTerms = "One reschedule with 30 days' notice.",
        weatherClause = "Backup location, or one free reschedule.",
        license = license,
        sendNow = sendNow,
    )

    private fun newLicense(
        media: Set<LicenseMedium> = setOf(LicenseMedium.Web, LicenseMedium.Social),
        territory: String = "United Kingdom",
        durationMonths: String = "12",
        isExclusive: Boolean = false,
        startsOn: String = "2026-08-01",
    ) = NewUsageLicense(
        media = media,
        territory = territory,
        durationMonths = durationMonths,
        isExclusive = isExclusive,
        startsOn = startsOn,
    )

    private fun contract(
        status: ContractStatus = ContractStatus.Sent,
        retainerAmount: Money? = Money.ofMajor(2_000, CurrencyCode.USD),
        signedAt: kotlin.time.Instant? = null,
    ) = Contract(
        id = ContractId.new(),
        studioId = studioId,
        projectId = projectId,
        title = "Johnson Wedding Agreement",
        status = status,
        sentAt = TestAppClock.DEFAULT_NOW.takeIf { status != ContractStatus.Draft },
        signedAt = signedAt,
        retainerAmount = retainerAmount,
        audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
    )

    /**
     * A $2,000 retainer invoice, optionally part or fully paid.
     *
     * The invoice id is taken first and handed to the payment, because the repositories —
     * fake and real alike — detach payments on write and re-attach them by invoice. A
     * payment pointing at some other invoice is simply never seen.
     */
    private fun retainerInvoice(paid: Money? = null): Invoice {
        val id = InvoiceId.new()

        return Invoice(
            id = id,
            studioId = studioId,
            projectId = projectId,
            number = "INV-001",
            kind = InvoiceKind.Retainer,
            status = InvoiceStatus.Sent,
            currency = usd,
            lines = listOf(LineItem("Retainer", Money.ofMajor(2_000, usd))),
            payments =
                listOfNotNull(
                    paid?.let { amount ->
                        Payment(
                            id = PaymentId.new(),
                            studioId = studioId,
                            invoiceId = id,
                            amount = amount,
                            paidAt = now,
                            method = PaymentMethod.BankTransfer,
                            audit = AuditMetadata.createdAt(now),
                        )
                    },
                ),
            issuedAt = now,
            audit = AuditMetadata.createdAt(now),
        )
    }

    private suspend fun Harness.content(): LedgerContent {
        val state = viewModel.uiState.first { it.content is UiState.Success }
        return (state.content as UiState.Success).data
    }

    // --- Drawing up --------------------------------------------------------------------

    @Test
    fun `a contract sent now goes out with the terms that were typed`() =
        runTest {
            val harness = harness()

            harness.viewModel.addContract(newContract())

            val stored =
                harness.contracts
                    .observeContracts()
                    .first()
                    .single()
            assertEquals(ContractStatus.Sent, stored.status)
            assertEquals(now, stored.sentAt)
            assertEquals(Money.ofMajor(2_000, usd), stored.retainerAmount)
            assertFalse(stored.isRetainerRefundable)
            assertEquals(45, stored.turnaroundDays)
            assertEquals(2, stored.revisionRounds)
            assertNull(stored.signedAt, "drawing one up does not sign it")
        }

    @Test
    fun `a contract can be kept back as a draft`() =
        runTest {
            val harness = harness()

            harness.viewModel.addContract(newContract(sendNow = false))

            val stored =
                harness.contracts
                    .observeContracts()
                    .first()
                    .single()
            assertEquals(ContractStatus.Draft, stored.status)
            assertNull(stored.sentAt, "an unsent contract has not been put to anyone")
        }

    @Test
    fun `a contract with no retainer is stored without one rather than rejected`() =
        runTest {
            val harness = harness()

            harness.viewModel.addContract(newContract(retainerAmount = ""))

            assertNull(
                harness.contracts
                    .observeContracts()
                    .first()
                    .single()
                    .retainerAmount,
            )
        }

    @Test
    fun `a contract with an unreadable retainer is not stored`() =
        runTest {
            val harness = harness()

            harness.viewModel.addContract(newContract(retainerAmount = "two thousand"))

            assertTrue(
                harness.contracts
                    .observeContracts()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `an unreadable turnaround is rejected rather than dropped from the terms`() =
        runTest {
            val harness = harness()

            harness.viewModel.addContract(newContract(turnaroundDays = "six weeks"))

            assertTrue(
                harness.contracts
                    .observeContracts()
                    .first()
                    .isEmpty(),
                "saving without a term the studio believed it had agreed is worse than refusing",
            )
        }

    @Test
    fun `terms left unstated are simply absent`() =
        runTest {
            val harness = harness()

            harness.viewModel.addContract(newContract(turnaroundDays = "", revisionRounds = ""))

            val stored =
                harness.contracts
                    .observeContracts()
                    .first()
                    .single()
            assertNull(stored.turnaroundDays)
            assertNull(stored.revisionRounds)
        }

    // --- Licensing ---------------------------------------------------------------------

    @Test
    fun `a licence is stored with its media, territory, and renewal date`() =
        runTest {
            val harness = harness()

            harness.viewModel.addContract(newContract(license = newLicense()))

            val license =
                assertNotNull(
                    harness.contracts
                        .observeContracts()
                        .first()
                        .single()
                        .usageLicense,
                )
            assertEquals(listOf(LicenseMedium.Web, LicenseMedium.Social), license.media)
            assertEquals("United Kingdom", license.territory)
            assertFalse(license.isPerpetual)
            assertEquals(LocalDate(2027, 8, 1), license.expiresOn())
        }

    @Test
    fun `an unreadable duration never quietly becomes a perpetual grant`() =
        runTest {
            val harness = harness()

            harness.viewModel.addContract(newContract(license = newLicense(durationMonths = "a year")))

            assertTrue(
                harness.contracts
                    .observeContracts()
                    .first()
                    .isEmpty(),
                "a typo must not give away every future fee from this work",
            )
        }

    @Test
    fun `a blank duration is a deliberate perpetual licence`() =
        runTest {
            val harness = harness()

            harness.viewModel.addContract(newContract(license = newLicense(durationMonths = "")))

            val license =
                assertNotNull(
                    harness.contracts
                        .observeContracts()
                        .first()
                        .single()
                        .usageLicense,
                )
            assertTrue(license.isPerpetual)
            assertNull(license.expiresOn())
        }

    // --- Sending and signing -----------------------------------------------------------

    @Test
    fun `sending a draft stamps when it went out`() =
        runTest {
            val draft = contract(status = ContractStatus.Draft)
            val harness = harness(contracts = FakeContractRepository(listOf(draft)))
            harness.clock.advanceBy(3.days)

            harness.viewModel.sendContract(draft.id)

            val stored = assertNotNull(harness.contracts.getContract(draft.id))
            assertEquals(ContractStatus.Sent, stored.status)
            assertEquals(now + 3.days, stored.sentAt)
        }

    @Test
    fun `sending an already sent contract does not restart its clock`() =
        runTest {
            val sent = contract(status = ContractStatus.Sent)
            val harness = harness(contracts = FakeContractRepository(listOf(sent)))
            harness.clock.advanceBy(3.days)

            harness.viewModel.sendContract(sent.id)

            assertEquals(now, assertNotNull(harness.contracts.getContract(sent.id)).sentAt)
        }

    @Test
    fun `signing records who signed and the date they signed, not the date it was entered`() =
        runTest {
            val sent = contract()
            val harness = harness(contracts = FakeContractRepository(listOf(sent)))
            harness.clock.advanceBy(10.days)

            harness.viewModel.signContract(
                ContractSignature(
                    contractId = sent.id,
                    signerName = "Sarah Johnson",
                    signerEmail = "sarah@example.com",
                    signedOn = "2026-06-20",
                ),
            )

            val stored = assertNotNull(harness.contracts.getContract(sent.id))
            assertEquals(ContractStatus.Signed, stored.status)
            assertEquals("Sarah Johnson", stored.signerName)
            assertEquals("sarah@example.com", stored.signerEmail)
            assertEquals(LocalDate(2026, 6, 20).atStartOfDayIn(TimeZone.UTC), stored.signedAt)
            assertTrue(stored.isSigned)
        }

    @Test
    fun `a signature with no name is not recorded`() =
        runTest {
            val sent = contract()
            val harness = harness(contracts = FakeContractRepository(listOf(sent)))

            harness.viewModel.signContract(
                ContractSignature(
                    contractId = sent.id,
                    signerName = "  ",
                    signerEmail = null,
                    signedOn = "2026-06-20",
                ),
            )

            assertEquals(ContractStatus.Sent, assertNotNull(harness.contracts.getContract(sent.id)).status)
        }

    @Test
    fun `an unreadable signing date is not recorded`() =
        runTest {
            val sent = contract()
            val harness = harness(contracts = FakeContractRepository(listOf(sent)))

            harness.viewModel.signContract(
                ContractSignature(
                    contractId = sent.id,
                    signerName = "Sarah Johnson",
                    signerEmail = null,
                    signedOn = "last Tuesday",
                ),
            )

            assertNull(assertNotNull(harness.contracts.getContract(sent.id)).signedAt)
        }

    @Test
    fun `signing a contract that is already signed leaves the original date alone`() =
        runTest {
            val signedOn = LocalDate(2026, 6, 20).atStartOfDayIn(TimeZone.UTC)
            val already = contract(status = ContractStatus.Signed, signedAt = signedOn)
            val harness = harness(contracts = FakeContractRepository(listOf(already)))

            harness.viewModel.signContract(
                ContractSignature(
                    contractId = already.id,
                    signerName = "Someone Else",
                    signerEmail = null,
                    signedOn = "2026-07-01",
                ),
            )

            val stored = assertNotNull(harness.contracts.getContract(already.id))
            assertEquals(signedOn, stored.signedAt, "the date a client became bound does not move")
            assertNull(stored.signerName)
        }

    // --- What actually holds a date ----------------------------------------------------

    @Test
    fun `an unsent contract is listed as the studio's own outstanding step`() =
        runTest {
            val harness = harness(contracts = FakeContractRepository(listOf(contract(status = ContractStatus.Draft))))

            val item =
                harness
                    .content()
                    .proposals.datesNotHeld
                    .single()
            assertEquals(ContractStage.NotSent, item.stage)
            assertTrue(item.canSend)
            assertTrue(item.canSign, "a contract can be signed in person without ever being sent")
        }

    @Test
    fun `a signed contract still awaiting its retainer does not leave the list`() =
        runTest {
            val signed = contract(status = ContractStatus.Signed, signedAt = now)
            val harness =
                harness(
                    contracts = FakeContractRepository(listOf(signed)),
                    invoices = FakeInvoiceRepository(listOf(retainerInvoice())),
                )

            val item =
                harness
                    .content()
                    .proposals.datesNotHeld
                    .single()
            assertEquals(ContractStage.AwaitingRetainer, item.stage)
            assertFalse(item.canSign, "it is already signed; the missing step is the money")
        }

    @Test
    fun `a signed contract clears once its retainer is paid`() =
        runTest {
            val signed = contract(status = ContractStatus.Signed, signedAt = now)
            val paid = retainerInvoice(paid = Money.ofMajor(2_000, usd))
            val harness =
                harness(
                    contracts = FakeContractRepository(listOf(signed)),
                    invoices = FakeInvoiceRepository(listOf(paid)),
                )

            assertTrue(
                harness
                    .content()
                    .proposals.datesNotHeld
                    .isEmpty(),
                "signed and paid for is the one state in which the date is actually held",
            )
        }

    @Test
    fun `a part-paid retainer does not hold the date`() =
        runTest {
            val signed = contract(status = ContractStatus.Signed, signedAt = now)
            val partPaid = retainerInvoice(paid = Money.ofMajor(500, usd))
            val harness =
                harness(
                    contracts = FakeContractRepository(listOf(signed)),
                    invoices = FakeInvoiceRepository(listOf(partPaid)),
                )

            assertEquals(
                ContractStage.AwaitingRetainer,
                harness
                    .content()
                    .proposals.datesNotHeld
                    .single()
                    .stage,
            )
        }

    @Test
    fun `a signed contract with no retainer holds the date on its own`() =
        runTest {
            val signed = contract(status = ContractStatus.Signed, retainerAmount = null, signedAt = now)
            val harness = harness(contracts = FakeContractRepository(listOf(signed)))

            assertTrue(
                harness
                    .content()
                    .proposals.datesNotHeld
                    .isEmpty(),
                "there is no money outstanding to wait for",
            )
        }

    @Test
    fun `a declined contract is not something to chase`() =
        runTest {
            val declined = contract(status = ContractStatus.Declined)
            val harness = harness(contracts = FakeContractRepository(listOf(declined)))

            assertTrue(
                harness
                    .content()
                    .proposals.datesNotHeld
                    .isEmpty(),
            )
        }

    @Test
    fun `the list reads from the studio's own desk outwards`() =
        runTest {
            val harness =
                harness(
                    contracts =
                        FakeContractRepository(
                            listOf(
                                contract(status = ContractStatus.Signed, signedAt = now),
                                contract(status = ContractStatus.Sent),
                                contract(status = ContractStatus.Draft),
                            ),
                        ),
                    invoices = FakeInvoiceRepository(listOf(retainerInvoice())),
                )

            assertEquals(
                listOf(ContractStage.NotSent, ContractStage.AwaitingSignature, ContractStage.AwaitingRetainer),
                harness
                    .content()
                    .proposals.datesNotHeld
                    .map { it.stage },
            )
        }
}
