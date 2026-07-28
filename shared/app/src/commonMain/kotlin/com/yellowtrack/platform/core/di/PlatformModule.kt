package com.yellowtrack.platform.core.di

import org.koin.core.module.Module

/**
 * Supplies dependencies that require platform-specific implementations.
 */
expect fun platformModule(): Module
