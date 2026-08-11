package com.yellowtrack.platform.feature.display

import com.yellowtrack.platform.feature.display.presentation.DisplayViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val displayModule =
    module {
        viewModel { DisplayViewModel(api = get(), auth = get()) }
    }
