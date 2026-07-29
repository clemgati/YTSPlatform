package com.yellowtrack.platform.feature.studio

import com.yellowtrack.platform.feature.studio.presentation.StudioViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val studioModule =
    module {
        viewModel {
            StudioViewModel(
                gearRepository = get(),
                recipeRepository = get(),
                studioContext = get(),
                clock = get(),
            )
        }
    }
