/**
 * Convention for a feature module.
 *
 * Everything [yellowtrack.kmp.compose] provides, plus the dependencies every feature
 * needs to follow the Route → ViewModel → UiState → Screen architecture: the design
 * system, shared UI patterns, the domain model, lifecycle, and dependency injection.
 *
 * Features never depend on other features. Shared concepts belong in `core`.
 */

plugins {
    id("yellowtrack.kmp.compose")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:ui"))
            implementation(project(":shared:core:common"))
            api(project(":shared:core:model"))
            implementation(project(":shared:core:data"))

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            api(libs.koin.coreViewmodel)
            implementation(libs.koin.composeViewmodel)

            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(project(":shared:core:testing"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
