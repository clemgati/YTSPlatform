package com.yellowtrack.platform.feature.auth

import com.yellowtrack.platform.feature.auth.presentation.SignInViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authFeatureModule =
    module {
        viewModel { SignInViewModel(auth = get()) }
    }
