package com.yellowtrack.platform.feature.dashboard

import com.yellowtrack.platform.feature.dashboard.presentation.DashboardViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dashboardModule =
    module {
        viewModel {
            DashboardViewModel(
                clientRepository = get(),
                projectRepository = get(),
                sessionRepository = get(),
                leadRepository = get(),
                studioContext = get(),
                studioProfileRepository = get(),
                conflictRepository = get(),
                clock = get(),
            )
        }
    }
