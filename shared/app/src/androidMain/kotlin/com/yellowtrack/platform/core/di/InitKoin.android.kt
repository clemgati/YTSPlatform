package com.yellowtrack.platform.core.di

import android.content.Context
import com.yellowtrack.platform.core.common.time.ensureTimeZonesLoaded
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * @param extraModules what a host application adds on top of the studio application's own.
 *   The companion display is a second Android application over the same data layer, and it
 *   composes one extra feature; everything below it — the client, the session store, the
 *   clock — is the same wiring and should not be a second copy of it.
 */
fun initKoinAndroid(
    context: Context,
    extraModules: List<Module> = emptyList(),
) {
    ensureTimeZonesLoaded()

    startKoin {
        androidContext(context)

        modules(
            listOf(appModule, platformModule()) + extraModules,
        )
    }
}
