package com.yellowtrack.platform.display

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The one activity: a device propped on a table, showing a code, for as long as the event
 * lasts.
 *
 * Three things here are about the device rather than the software, and none of them belong
 * in the shared screen.
 */
class DisplayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        /*
         * The screen does not go to sleep.
         *
         * This is the whole product. A sign that blanks itself after thirty seconds is a
         * blank tablet on a table, and the first person to find out is a guest standing in
         * front of it wondering what it is for. The flag is cleared with the window, so
         * nothing has to remember to undo it.
         */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        hideSystemBars()

        setContent {
            DisplayApp(onDisplayingChanged = ::pinToThisScreen)
        }
    }

    /**
     * Full screen, with the bars available on a swipe.
     *
     * A navigation bar along the bottom of a sign is both clutter and an invitation: it is
     * the shortest route from a curious guest to the event picker. Hidden rather than
     * disabled — Android does not allow the latter without a device owner, and pretending
     * otherwise would be a claim this cannot keep.
     */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * Screen pinning, for as long as a code is on the table.
     *
     * Without it the in-application password is worth less than it looks: the lock stops
     * somebody tapping their way back to the picker, and the Home button walks around it in
     * one press. Pinning is Android's answer, and on an ordinary installation it asks the
     * studio to confirm the first time and then holds until it is stopped.
     *
     * Attempted rather than required. Pinning can be refused — by policy, by the version, by
     * the studio declining the prompt — and a display that refused to show a code because it
     * could not pin the screen would be useless for the sake of a lock that was never
     * absolute. So a failure is logged and the code goes up anyway.
     */
    private fun pinToThisScreen(isDisplaying: Boolean) {
        runCatching {
            if (isDisplaying) startLockTask() else stopLockTask()
        }.onFailure { failure ->
            Log.w(TAG, "Could not ${if (isDisplaying) "pin" else "unpin"} the screen", failure)
        }
    }

    private companion object {
        const val TAG = "YellowTrackDisplay"
    }
}
