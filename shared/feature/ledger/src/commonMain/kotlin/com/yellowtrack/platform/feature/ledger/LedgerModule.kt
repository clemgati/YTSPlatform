package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.feature.ledger.presentation.LedgerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val ledgerModule =
    module {
        viewModel {
            LedgerViewModel(
                invoiceRepository = get(),
                quoteRepository = get(),
                contractRepository = get(),
                expenseRepository = get(),
                codbRepository = get(),
                serviceTemplateRepository = get(),
                projectRepository = get(),
                sessionRepository = get(),
                postProductionRepository = get(),
                clientRepository = get(),
                studioContext = get(),
                clock = get(),
            )
        }
    }
