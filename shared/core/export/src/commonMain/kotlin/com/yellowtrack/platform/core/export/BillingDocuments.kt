package com.yellowtrack.platform.core.export

import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.money.formatted
import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.PaymentState
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import com.yellowtrack.platform.core.model.studio.StudioProfile
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * An invoice, as the client receives it.
 *
 * [studio] is required rather than optional, unlike on a call sheet. A call sheet with no
 * studio name still works — it goes to people who know who booked them. An invoice with no
 * name on it is not an invoice: the client cannot tell who it is from, cannot enter it in
 * their books, and does not pay it. Callers check [StudioProfile.canIssueDocuments] first
 * and say so rather than sending one.
 *
 * Every figure is taken from the `Invoice` itself, which computes them from its own lines
 * and payments. Nothing is recomputed here: a document that arrives at a different total
 * from the screen it was sent from is the worst possible bug in this application.
 */
fun buildInvoice(
    invoice: Invoice,
    project: Project?,
    client: Client?,
    studio: StudioProfile,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Sheet {
    val state = invoice.paymentState(now)

    return Sheet(
        title = "Invoice ${invoice.number}",
        subtitle = studio.name,
        footer = studio.documentFooter,
        sections =
            listOfNotNull(
                studio.fromSection(),
                billToSection(client, project),
                SheetSection(
                    heading = "This invoice",
                    blocks =
                        listOf(
                            SheetBlock.Facts(
                                listOfNotNull(
                                    SheetFact("Number", invoice.number),
                                    invoice.issuedAt?.let { SheetFact("Issued", DateFormats.fullDate(it, zone)) },
                                    invoice.dueAt?.let {
                                        SheetFact(
                                            label = "Due",
                                            value = DateFormats.fullDate(it, zone),
                                            // Emphasised only when it is still owed. Bolding
                                            // the date on a paid invoice is a demand for money
                                            // that has already arrived.
                                            isEmphasised = state.isOutstanding,
                                        )
                                    },
                                    state.note()?.let { SheetFact("Status", it) },
                                ),
                            ),
                        ),
                ),
                invoice.lines.workSection(),
                invoice.totalsSection(state),
                studio.paymentSection(state),
                invoice.notes.notesSection(),
            ),
    )
}

/**
 * A quote, as the client receives it.
 *
 * The date it stops being valid is the figure that matters and is emphasised: a quote with
 * no expiry is a price a studio has offered forever, and one whose expiry has passed
 * without being marked is a price it is still being held to.
 */
fun buildQuote(
    quote: Quote,
    project: Project?,
    client: Client?,
    studio: StudioProfile,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Sheet =
    Sheet(
        title = "Quote ${quote.number}",
        subtitle = studio.name,
        footer = studio.documentFooter,
        sections =
            listOfNotNull(
                studio.fromSection(),
                billToSection(client, project),
                SheetSection(
                    heading = "This quote",
                    blocks =
                        listOf(
                            SheetBlock.Facts(
                                listOfNotNull(
                                    SheetFact("Number", quote.number),
                                    quote.issuedAt?.let { SheetFact("Issued", DateFormats.fullDate(it, zone)) },
                                    quote.validUntil?.let {
                                        SheetFact(
                                            label = if (quote.isExpired(now)) "Expired" else "Valid until",
                                            value = DateFormats.fullDate(it, zone),
                                            isEmphasised = true,
                                        )
                                    },
                                    quote.effectiveStatus(now).note()?.let { SheetFact("Status", it) },
                                ),
                            ),
                        ),
                ),
                quote.lines.workSection(),
                SheetSection(
                    heading = "Total",
                    blocks =
                        listOf(
                            SheetBlock.Facts(
                                listOfNotNull(
                                    SheetFact("Subtotal", quote.subtotal.formatted()),
                                    quote.tax.takeIf { !it.isZero }?.let { SheetFact("Tax", it.formatted()) },
                                    SheetFact("Total", quote.total.formatted(), isEmphasised = true),
                                ),
                            ),
                        ),
                ),
                quote.terms.notesSection(heading = "Terms"),
                quote.notes.notesSection(),
            ),
    )

// --- Shared between the two ------------------------------------------------------------

private fun StudioProfile.fromSection(): SheetSection =
    SheetSection(
        heading = "From",
        blocks =
            listOf(
                SheetBlock.Lines(
                    listOfNotNull(
                        name,
                        address,
                        email,
                        phone,
                        website,
                        taxNumber?.let { "Tax registration $it" },
                    ).flatMap { it.lines() },
                ),
            ),
    )

/**
 * Who the document is addressed to.
 *
 * The billing contact rather than the primary one: at a company those are routinely
 * different people, and an invoice sent to the brief-giver sits in their inbox unpaid.
 */
private fun billToSection(
    client: Client?,
    project: Project?,
): SheetSection? {
    val lines =
        listOfNotNull(
            client?.displayName?.takeIf { it.isNotBlank() },
            client?.billingContact?.displayName?.takeIf { it.isNotBlank() && it != client.displayName },
            client?.billingContact?.primaryEmail,
            project?.name?.let { "For: $it" },
        )

    return if (lines.isEmpty()) null else SheetSection("To", listOf(SheetBlock.Lines(lines)))
}

/**
 * The lines being charged for.
 *
 * Quantity and unit price are on the detail line rather than in columns: aligned columns
 * survive a desktop mail client and fall apart on a phone, and the phone is where a client
 * opens this.
 */
private fun List<LineItem>.workSection(): SheetSection? {
    if (isEmpty()) return null

    return SheetSection(
        heading = "Work",
        blocks =
            listOf(
                SheetBlock.Entries(
                    map { line ->
                        SheetEntry(
                            name = line.description,
                            detail = line.detail(),
                            trailing = line.total.formatted(),
                        )
                    },
                ),
            ),
    )
}

private fun LineItem.detail(): String? =
    listOfNotNull(
        // A quantity of one adds nothing: "1 × $4,000.00" beside a total of $4,000.00 is
        // a line a reader has to check before discarding.
        "$quantity × ${unitPrice.formatted()}".takeIf { quantity != 1 },
        "includes ${tax.formatted()} tax".takeIf { !tax.isZero },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

private fun Invoice.totalsSection(state: PaymentState): SheetSection =
    SheetSection(
        heading = "Total",
        blocks =
            listOf(
                SheetBlock.Facts(
                    listOfNotNull(
                        SheetFact("Subtotal", subtotal.formatted()),
                        tax.takeIf { !it.isZero }?.let { SheetFact("Tax", it.formatted()) },
                        SheetFact("Total", total.formatted()),
                        amountPaid.takeIf { it.isPositive }?.let { SheetFact("Paid", it.formatted()) },
                        // The one figure the client is looking for, so it is the one in bold.
                        SheetFact(balanceDue.balanceLabel(), balanceDue.owed().formatted(), isEmphasised = true),
                    ),
                ),
            ),
    )

/** An overpayment is a refund owed, not a negative balance, and saying so avoids a dispute. */
private fun Money.balanceLabel(): String = if (isNegative) "Overpaid" else "Balance due"

private fun Money.owed(): Money = if (isNegative) -this else this

/**
 * How to be paid, printed only while there is something to pay.
 *
 * Bank details on a settled invoice invite a second payment, which is a refund, an apology
 * and an afternoon.
 */
private fun StudioProfile.paymentSection(state: PaymentState): SheetSection? {
    if (!state.isOutstanding) return null
    val instructions = paymentInstructions?.takeIf { it.isNotBlank() } ?: return null

    return SheetSection("How to pay", listOf(SheetBlock.Lines(instructions.lines())))
}

private fun String?.notesSection(heading: String = "Notes"): SheetSection? {
    val lines = this?.lines().orEmpty().filter(String::isNotBlank)

    return if (lines.isEmpty()) null else SheetSection(heading, listOf(SheetBlock.Paragraphs(lines)))
}

/**
 * The state worth stating on the page.
 *
 * A plain unpaid invoice says nothing: the balance already says it, and a status line
 * repeating it teaches the reader to skip the block.
 */
private fun PaymentState.note(): String? =
    when (this) {
        PaymentState.Draft -> "Draft — not yet issued"
        PaymentState.AwaitingPayment -> null
        PaymentState.PartiallyPaid -> "Part paid"
        PaymentState.Paid -> "Paid in full — thank you"
        PaymentState.Overdue -> "Overdue"
        PaymentState.Void -> "Cancelled"
    }

private fun QuoteStatus.note(): String? =
    when (this) {
        QuoteStatus.Draft -> "Draft — not yet sent"
        QuoteStatus.Sent -> null
        QuoteStatus.Accepted -> "Accepted"
        QuoteStatus.Declined -> "Declined"
        QuoteStatus.Expired -> "This quote has expired"
    }
