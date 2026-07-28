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

    iosArm64()
    iosSimulatorArm64()

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
