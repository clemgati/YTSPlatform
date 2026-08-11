package com.yellowtrack.platform.core.di

import android.content.Context
import com.yellowtrack.platform.core.common.time.ensureTimeZonesLoaded
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

fun initKoinAndroid(context: Context) {
    ensureTimeZonesLoaded()

    startKoin {
        androidContext(context)

        modules(
            appModule,
            platformModule(),
        )
    }
}
