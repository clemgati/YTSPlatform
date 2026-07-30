package com.yellowtrack.platform.core.di

import com.yellowtrack.platform.core.common.time.ensureTimeZonesLoaded
import org.koin.core.context.startKoin

fun initKoin() {
    // Before anything can render a shoot day: on the web the zone database has to be
    // pulled in explicitly, and every session carries a zone id.
    ensureTimeZonesLoaded()

    startKoin {
        modules(
            appModule,
            platformModule(),
        )
    }
}
