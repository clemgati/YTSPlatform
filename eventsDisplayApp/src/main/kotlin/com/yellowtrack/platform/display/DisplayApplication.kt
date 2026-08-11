package com.yellowtrack.platform.display

import android.app.Application
import com.yellowtrack.platform.core.di.initKoinAndroid
import com.yellowtrack.platform.feature.display.displayModule

/**
 * The companion display, as its own application.
 *
 * Separate from the studio application rather than a screen inside it, because the two are
 * installed on different devices and used by different people. The studio application is a
 * till: it holds a business, and somebody signed in is looking at it. This is a sign — it
 * lives on a table in a room full of strangers, and the only thing it should be able to do
 * is show one code.
 *
 * Everything below the screen is shared: the same Koin wiring, the same session store, the
 * same server URL from the same build property. A second copy of that would be a second
 * place for the server to be wrong.
 */
class DisplayApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoinAndroid(this, extraModules = listOf(displayModule))
    }
}
