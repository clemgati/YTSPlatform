package com.yellowtrack.platform

import android.app.Application
import com.yellowtrack.platform.core.di.initKoinAndroid

class YellowTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoinAndroid(this)
    }
}
