package com.yellowtrack.platform

import com.yellowtrack.platform.core.di.initKoin

/**
 * Swift-friendly entry point. Call once from the iOS app initializer.
 */
fun initializeKoin() {
    initKoin()
}
