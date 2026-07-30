package com.yellowtrack.platform.feature.ledger.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.ui.component.StatefulContent
import com.yellowtrack.platform.feature.ledger.presentation.component.ContractFormDialog
import com.yellowtrack.platform.feature.ledger.presentation.component.ContractSignatureDialog
import com.yellowtrack.platform.feature.ledger.presentation.component.ExpenseFormDialog
import com.yellowtrack.platform.feature.ledger.presentation.component.ExpenseSection
import com.yellowtrack.platform.feature.ledger.presentation.component.InvoiceFormDialog
import com.yellowtrack.platform.feature.ledger.presentation.component.MoneyOwedSection
import com.yellowtrack.platform.feature.ledger.presentation.component.PaymentFormDialog
import com.yellowtrack.platform.feature.ledger.presentation.component.PricingSection
import com.yellowtrack.platform.feature.ledger.presentation.component.ProposalsSection
import com.yellowtrack.platform.feature.ledger.presentation.component.QuoteFormDialog
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractItem
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractSignature
import com.yellowtrack.platform.feature.ledger.presentation.model.NewContract
import com.yellowtrack.platform.feature.ledger.presentation.model.NewExpense
import com.yellowtrack.platform.feature.ledger.presentation.model.NewInvoice
import com.yellowtrack.platform.feature.ledger.presentation.model.NewPayment
import com.yellowtrack.platform.feature.ledger.presentation.model.NewQuote
import com.yellowtrack.platform.feature.ledger.presentation.model.OutstandingInvoiceItem

@Composable
internal fun LedgerScreen(
    uiState: LedgerUiState,
    onRetry: () -> Unit,
    onSavePricingBasis: (salary: String, billableDays: String, taxRate: String) -> Unit,
    onAddExpense: (NewExpense) -> Unit,
    onRecordPayment: (NewPayment) -> Unit,
    onAddQuote: (NewQuote) -> Unit,
    onAddInvoice: (NewInvoice) -> Unit,
    onAddContract: (NewContract) -> Unit,
    onAcceptQuote: (QuoteId) -> Unit,
    onDeclineQuote: (QuoteId) -> Unit,
    onSendContract: (ContractId) -> Unit,
    onSignContract: (ContractSignature) -> Unit,
    onSendInvoice: (InvoiceId) -> Unit,
    onVoidInvoice: (InvoiceId) -> Unit,
    onDeleteInvoice: (InvoiceId) -> Unit,
    onSaveInvoice: (InvoiceId) -> Unit,
    onSaveQuote: (QuoteId) -> Unit,
    documentMessage: String?,
    modifier: Modifier = Modifier,
) {
    var showExpenseForm by remember { mutableStateOf(false) }
    var showQuoteForm by remember { mutableStateOf(false) }
    var showInvoiceForm by remember { mutableStateOf(false) }
    var showContractForm by remember { mutableStateOf(false) }
    var payingInvoice by remember { mutableStateOf<OutstandingInvoiceItem?>(null) }
    var signingContract by remember { mutableStateOf<ContractItem?>(null) }

    StatefulContent(
        state = uiState.content,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
    ) { content, contentModifier ->
        if (showExpenseForm) {
            ExpenseFormDialog(
                today = content.today,
                currency = content.currency,
                projects = content.projects,
                onSave = {
                    onAddExpense(it)
                    showExpenseForm = false
                },
                onDismiss = { showExpenseForm = false },
            )
        }

        if (showQuoteForm) {
            QuoteFormDialog(
                suggestedNumber = content.proposals.nextQuoteNumber,
                today = content.today,
                currency = content.currency,
                projects = content.projects,
                onSave = {
                    onAddQuote(it)
                    showQuoteForm = false
                },
                onDismiss = { showQuoteForm = false },
            )
        }

        if (showInvoiceForm) {
            InvoiceFormDialog(
                suggestedNumber = content.proposals.nextInvoiceNumber,
                today = content.today,
                currency = content.currency,
                projects = content.projects,
                onSave = {
                    onAddInvoice(it)
                    showInvoiceForm = false
                },
                onDismiss = { showInvoiceForm = false },
            )
        }

        if (showContractForm) {
            ContractFormDialog(
                today = content.today,
                currency = content.currency,
                projects = content.projects,
                onSave = {
                    onAddContract(it)
                    showContractForm = false
                },
                onDismiss = { showContractForm = false },
            )
        }

        signingContract?.let { contract ->
            ContractSignatureDialog(
                contract = contract,
                today = content.today,
                onSave = {
                    onSignContract(it)
                    signingContract = null
                },
                onDismiss = { signingContract = null },
            )
        }

        payingInvoice?.let { invoice ->
            PaymentFormDialog(
                invoice = invoice,
                today = content.today,
                currency = content.currency,
                // Prefilled with the balance, which is what is usually being paid.
                prefillAmount = invoice.balanceDuePlain,
                onSave = {
                    onRecordPayment(it)
                    payingInvoice = null
                },
                onDismiss = { payingInvoice = null },
            )
        }

        Column(
            modifier =
                contentModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(YTTheme.spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.large),
        ) {
            LedgerHeader(content)

            // Ordered by what a studio needs to act on: money owed first, then what has
            // been quoted and not answered, then whether the prices earning it are high
            // enough, then what it all costs to run.
            MoneyOwedSection(
                summary = content.moneyOwed,
                onRecordPayment = { payingInvoice = it },
                onVoidInvoice = { onVoidInvoice(it.id) },
                onSendDraft = { onSendInvoice(it.id) },
                onDeleteDraft = { onDeleteInvoice(it.id) },
                onSaveInvoice = { onSaveInvoice(it.id) },
            )

            ProposalsSection(
                summary = content.proposals,
                onNewQuote = { showQuoteForm = true },
                onNewInvoice = { showInvoiceForm = true },
                onNewContract = { showContractForm = true },
                onAcceptQuote = { onAcceptQuote(it.id) },
                onDeclineQuote = { onDeclineQuote(it.id) },
                onSendContract = { onSendContract(it.id) },
                onSignContract = { signingContract = it },
                onSaveQuote = { onSaveQuote(it.id) },
            )

            // Where the document went, or why it could not go. A file nobody can find
            // was not saved, and silence is how that happens.
            documentMessage?.let { message ->
                Text(
                    text = message,
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }

            PricingSection(
                pricing = content.pricing,
                basis = content.pricingBasis,
                onSaveBasis = onSavePricingBasis,
            )

            ExpenseSection(
                summary = content.expenses,
                onAddExpense = { showExpenseForm = true },
            )
        }
    }
}

@Composable
private fun LedgerHeader(
    content: LedgerContent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
    ) {
        val underpriced = content.pricing?.underpricedPackages?.size ?: 0

        // One badge, showing the most costly thing outstanding: money already earned and
        // unpaid beats a price that has lapsed, which beats a package sold below cost.
        YTBadge(
            text =
                when {
                    content.moneyOwed.hasOverdue -> "${content.moneyOwed.overdueCount} overdue"
                    content.proposals.hasExpired -> "${content.proposals.expiredCount} quotes expired"
                    underpriced > 0 -> "$underpriced below cost"
                    else -> "Up to date"
                },
        )

        Text(
            text = "Ledger",
            style = YTTheme.typography.headlineLarge,
            color = YTTheme.colors.onBackground,
        )

        Text(
            text = "What you are owed, what you must charge, and what the studio costs to run.",
            style = YTTheme.typography.bodyLarge,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}
