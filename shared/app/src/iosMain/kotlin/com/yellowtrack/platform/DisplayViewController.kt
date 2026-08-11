package com.yellowtrack.platform

import androidx.compose.ui.window.ComposeUIViewController
import com.yellowtrack.platform.display.DisplayApp
import platform.UIKit.UIApplication

/**
 * The companion display, as something an iOS application can show.
 *
 * The Swift side of this is four lines, exactly as the studio application's is. Everything
 * that differs between the two companions is here rather than there, so the two Xcode
 * projects stay things that only launch a view controller.
 */
fun createDisplayViewController() =
    ComposeUIViewController {
        // Set here rather than in Swift because it is a fact about this screen: a sign that
        // dims after thirty seconds is a dark tablet on a table, and the first person to
        // notice is a guest standing in front of it.
        //
        // For the whole application rather than only while a code is up. Somebody is setting
        // the table when they sign in, and the screen going out under their hands while they
        // pick an event is the same annoyance for a smaller reason.
        UIApplication.sharedApplication.idleTimerDisabled = true

        /*
         * No `onDisplayingChanged`, and not by oversight.
         *
         * Android answers that signal by pinning the screen and swallowing Back. iOS has
         * neither. Guided Access is the equivalent, and it is started by a person holding
         * the device — triple-clicking the side button — with no API to request it. There is
         * no home gesture to intercept either.
         *
         * So on iOS the password is the whole of the in-application lock, and somebody who
         * swipes home and reopens the application reaches the picker. Left as it is rather
         * than papered over: a studio that needs the device proof against a curious guest
         * should turn on Guided Access, and that instruction is worth more than a lock this
         * could pretend to.
         */
        DisplayApp()
    }

/**
 * Swift-friendly entry point. Call once from the iOS application initializer.
 *
 * The same graph the studio application builds, because it is the same data layer, the same
 * session store and the same server. The display feature is composed into it like any other.
 */
fun initializeDisplayKoin() {
    initializeKoin()
}
