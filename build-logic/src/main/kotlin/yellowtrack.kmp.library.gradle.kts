import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

/**
 * Base convention for a non-UI Kotlin Multiplatform library module.
 *
 * Declares the four targets the platform ships on — Android, iOS, desktop (JVM),
 * and web (wasmJs) — so that individual modules do not repeat them.
 */

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

// Version-catalog accessors are not injected into precompiled script plugins;
// this resolves the generated type explicitly.
val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

/**
 * Whether this machine can build for Apple platforms.
 *
 * Overridable with -Pyellowtrack.appleTargets so a Mac can reproduce the Linux half of CI
 * without anyone having to find a Linux machine to debug it on.
 */
val appleTargetsSupported: Boolean =
    providers.gradleProperty("yellowtrack.appleTargets").orNull?.toBooleanStrictOrNull()
        ?: System.getProperty("os.name").orEmpty().startsWith("Mac")

kotlin {
    androidLibrary {
        // ":shared:core:model" -> "com.yellowtrack.platform.core.model"
        namespace = "com.yellowtrack.platform" + project.path.removePrefix(":shared").replace(":", ".")

        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }

    jvm("desktop")

    // Apple targets are declared only where they can actually be compiled. Kotlin/Native
    // cannot build them without the Apple SDKs, so on Linux their tasks would exist only
    // to fail — which is what stopped CI from splitting the cheap targets onto a cheaper,
    // faster runner. Declaring them per host means `build` keeps meaning "everything this
    // machine can build" rather than becoming a hand-maintained list of task names.
    if (appleTargetsSupported) {
        iosArm64()
        iosSimulatorArm64()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
