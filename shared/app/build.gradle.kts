import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Release frameworks are linked with full LLVM optimisation, which on a three-core CI
    // runner takes three quarters of an hour for one architecture and barely parallelises.
    // Nothing on CI consumes them: Xcode links its own through
    // `embedAndSignAppleFrameworkForXcode`, and no artefact is published. So CI opts out
    // with -Pyellowtrack.iosReleaseFrameworks=false while every other build — including an
    // archive from Xcode — keeps both build types and is unaffected.
    val iosBuildTypes =
        if (providers.gradleProperty("yellowtrack.iosReleaseFrameworks").orNull == "false") {
            listOf(NativeBuildType.DEBUG)
        } else {
            listOf(NativeBuildType.DEBUG, NativeBuildType.RELEASE)
        }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework(iosBuildTypes) {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    android {
        namespace = "com.yellowtrack.platform.app"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // ...
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:common"))
            implementation(project(":shared:core:data"))
            implementation(project(":shared:core:database"))
            implementation(project(":shared:core:export"))
            implementation(project(":shared:core:navigation"))

            implementation(project(":shared:feature:dashboard"))
            implementation(project(":shared:feature:clients"))
            implementation(project(":shared:feature:ledger"))
            implementation(project(":shared:feature:sessions"))
            implementation(project(":shared:feature:studio"))
            implementation(project(":shared:feature:settings"))

            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.core)
            // Add only when shared Compose code injects dependencies directly.
            implementation(libs.koin.compose)
            implementation(libs.koin.coreViewmodel)
            implementation(libs.koin.composeViewmodel)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        /*wasmJsMain.dependencies {
            // Wasm-specific dependencies

           // wasmJsMain.dependencies {
                implementation(":shared")

                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
           // }
        }*/
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
