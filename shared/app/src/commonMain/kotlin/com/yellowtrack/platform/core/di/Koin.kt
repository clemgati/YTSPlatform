package com.yellowtrack.platform.core.di

import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(
            appModule,
            platformModule(),
        )
    }
}
