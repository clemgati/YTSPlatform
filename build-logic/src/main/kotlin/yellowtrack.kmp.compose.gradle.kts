/**
 * Convention for a Kotlin Multiplatform module that renders UI.
 *
 * Adds Compose Multiplatform on top of [yellowtrack.kmp.library] without assuming
 * anything about the design system, so that `core:designsystem` itself can use it.
 */

plugins {
    id("yellowtrack.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

kotlin {
    // The `compose` accessor is not generated for precompiled script plugins. The plugin
    // registers its dependency helpers on the Kotlin extension, which is where this
    // resolves them from.
    val composeDependencies = the<org.jetbrains.compose.ComposePlugin.Dependencies>()

    androidLibrary {
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
        }

        // Skiko's native binary, so a desktop test can rasterise a screen to a PNG
        // without opening a window — the only way to look at this UI on a machine where
        // screen recording is unavailable. Test-only; it ships with no target.
        getByName("desktopTest").dependencies {
            implementation(composeDependencies.desktop.currentOs)
        }
    }
}

// Required by the Android Studio preview renderer. Must sit outside `kotlin { }`.
dependencies {
    "androidRuntimeClasspath"(libs.compose.uiTooling)
}
