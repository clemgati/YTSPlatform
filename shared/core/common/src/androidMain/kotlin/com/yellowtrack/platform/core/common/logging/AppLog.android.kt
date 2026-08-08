package com.yellowtrack.platform.core.common.logging

import android.util.Log

/** `adb logcat -s yellowtrack` — which is how the missing permission was found. */
actual fun logFailure(
    where: String,
    error: Throwable,
) {
    Log.e("yellowtrack", where, error)
}
