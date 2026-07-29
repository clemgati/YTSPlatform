package com.yellowtrack.platform.feature.ledger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.feature.ledger.presentation.LedgerScreen
import com.yellowtrack.platform.feature.ledger.presentation.LedgerViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LedgerRoute(modifier: Modifier = Modifier) {
    val viewModel: LedgerViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LedgerScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onSavePricingBasis = viewModel::savePricingBasis,
        onAddExpense = viewModel::addExpense,
        onRecordPayment = viewModel::recordPayment,
        onAddQuote = viewModel::addQuote,
        onAddInvoice = viewModel::addInvoice,
        onAddContract = viewModel::addContract,
        onAcceptQuote = viewModel::acceptQuote,
        onDeclineQuote = viewModel::declineQuote,
        onSendContract = viewModel::sendContract,
        onSignContract = viewModel::signContract,
        onSendInvoice = viewModel::sendInvoice,
        onVoidInvoice = viewModel::voidInvoice,
        onDeleteInvoice = viewModel::deleteInvoice,
        modifier = modifier,
    )
}
