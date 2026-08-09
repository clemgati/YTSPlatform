package com.yellowtrack.platform.feature.events

import com.yellowtrack.platform.feature.events.presentation.EventsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val eventsModule =
    module {
        viewModel { EventsViewModel(api = get()) }
    }
