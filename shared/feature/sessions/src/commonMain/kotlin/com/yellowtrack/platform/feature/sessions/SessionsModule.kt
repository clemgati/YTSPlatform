package com.yellowtrack.platform.feature.sessions

import com.yellowtrack.platform.feature.sessions.presentation.SessionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sessionsModule =
    module {
        viewModel { SessionsViewModel(get(), get(), get(), get()) }
    }
