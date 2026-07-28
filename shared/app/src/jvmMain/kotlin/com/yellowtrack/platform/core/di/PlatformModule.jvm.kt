package com.yellowtrack.platform.core.di

import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        // iOS-only dependencies will be registered here.
    }
