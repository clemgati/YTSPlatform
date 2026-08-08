package com.yellowtrack.platform.core.data.sync

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

/**
 * `UIApplicationDidBecomeActiveNotification`, which is the moment somebody is looking again.
 *
 * `DidBecomeActive` rather than `WillEnterForeground`: the latter does not fire on first
 * launch, which sounds convenient here, but it also does not fire when the application comes
 * back from a merely *inactive* state — the app switcher, a notification shade pulled down,
 * an incoming call declined. Those are all cases where somebody is looking at the screen
 * again and the data on it may be an hour old.
 *
 * The cost of that choice is that `DidBecomeActive` **does** fire on first launch, so the
 * first one is dropped: sign-in reconciles anyway and a device that synced on launch and
 * again immediately afterwards would do the same work twice.
 *
 * Observed on the main queue, which is where UIKit posts it. The handler does nothing but
 * offer to a channel.
 */
class IosAppVisibility : AppVisibility {
    override val foregrounded: Flow<Unit> =
        callbackFlow {
            var everActive = false

            val observer =
                NSNotificationCenter.defaultCenter.addObserverForName(
                    name = UIApplicationDidBecomeActiveNotification,
                    `object` = null,
                    queue = NSOperationQueue.mainQueue,
                ) {
                    if (everActive) trySend(Unit)
                    everActive = true
                }

            awaitClose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
        }
}
