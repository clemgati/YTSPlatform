package com.yellowtrack.platform.feature.ledger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.feature.ledger.presentation.LedgerScreen
import com.yellowtrack.platform.feature.ledger.presentation.LedgerViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LedgerRoute(modifier: Modifier = Modifier) {
    val viewModel: LedgerViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var documentMessage by remember { mutableStateOf<String?>(null) }

    LedgerScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onSavePricingBasis = viewModel::savePricingBasis,
        onSaveExpense = viewModel::saveExpense,
        onSaveMileage = viewModel::saveMileage,
        onRemoveCost = viewModel::removeCost,
        onSavePackage = viewModel::saveServiceTemplate,
        onRemovePackage = viewModel::removeServiceTemplate,
        onRemovePayment = viewModel::removePayment,
        onRecordPayment = viewModel::recordPayment,
        onSaveQuote = viewModel::saveQuote,
        onSaveInvoice = viewModel::saveInvoice,
        onSaveContract = viewModel::saveContract,
        onAcceptQuote = viewModel::acceptQuote,
        onDeclineQuote = viewModel::declineQuote,
        onSendContract = viewModel::sendContract,
        onSignContract = viewModel::signContract,
        onSendInvoice = viewModel::sendInvoice,
        onVoidInvoice = viewModel::voidInvoice,
        onDeleteInvoice = viewModel::deleteInvoice,
        onExportInvoice = { invoiceId ->
            viewModel.saveDocument({ viewModel.invoiceSheet(invoiceId) }) { documentMessage = it }
        },
        onExportQuote = { quoteId ->
            viewModel.saveDocument({ viewModel.quoteSheet(quoteId) }) { documentMessage = it }
        },
        documentMessage = documentMessage,
        modifier = modifier,
    )
}
