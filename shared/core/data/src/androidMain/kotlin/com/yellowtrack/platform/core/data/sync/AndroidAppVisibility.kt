package com.yellowtrack.platform.core.data.sync

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The phone coming back out of a pocket, which is the case this whole signal exists for.
 *
 * Counted from `Application.registerActivityLifecycleCallbacks` rather than taken from
 * `ProcessLifecycleOwner`. The latter reads better and would mean adding
 * `androidx.lifecycle:lifecycle-process` for one boolean; the framework already reports every
 * edge needed, and this module deliberately carries almost no Android dependencies.
 *
 * ## Counting, rather than watching one activity
 *
 * `onActivityStarted` fires for every activity, so acting on it directly would report a
 * foreground transition on a rotation and on every move between screens — many times a minute
 * in normal use, which would make this a second timer with a worse trigger. What matters is
 * the count going from none to one: that is the process becoming visible, and it happens once
 * per return to the application however many activities are involved.
 *
 * A configuration change stops one activity and starts another, so the count dips and rises —
 * but never to zero, because the new activity starts before the old one stops. That ordering
 * is what makes counting correct rather than merely usually right.
 *
 * The first start after launch is not reported. Sign-in reconciles anyway, and a device that
 * synced on launch and again on its first foreground would do the same work twice.
 */
class AndroidAppVisibility(
    private val application: Application,
) : AppVisibility {
    override val foregrounded: Flow<Unit> =
        callbackFlow {
            var started = 0
            var everStarted = false

            val callbacks =
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityStarted(activity: Activity) {
                        started += 1
                        if (started == 1) {
                            // Skipped once: the first start is the launch, not a return to it.
                            if (everStarted) trySend(Unit)
                            everStarted = true
                        }
                    }

                    override fun onActivityStopped(activity: Activity) {
                        started = maxOf(0, started - 1)
                    }

                    override fun onActivityCreated(
                        activity: Activity,
                        savedInstanceState: Bundle?,
                    ) = Unit

                    override fun onActivityResumed(activity: Activity) = Unit

                    override fun onActivityPaused(activity: Activity) = Unit

                    override fun onActivitySaveInstanceState(
                        activity: Activity,
                        outState: Bundle,
                    ) = Unit

                    override fun onActivityDestroyed(activity: Activity) = Unit
                }

            application.registerActivityLifecycleCallbacks(callbacks)
            awaitClose { application.unregisterActivityLifecycleCallbacks(callbacks) }
        }
}
