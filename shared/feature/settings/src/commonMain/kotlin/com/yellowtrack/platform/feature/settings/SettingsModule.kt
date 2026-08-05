package com.yellowtrack.platform.feature.settings

import com.yellowtrack.platform.feature.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule =
    module {
        viewModel {
            SettingsViewModel(
                profileRepository = get(),
                conflictRepository = get(),
                synchroniser = get(),
                auth = get(),
                documentSink = get(),
                studioContext = get(),
                clock = get(),
            )
        }
    }
