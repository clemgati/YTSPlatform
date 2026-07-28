rootProject.name = "yellow-track-platform"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":webApp")

include(":shared:app")

include(":shared:core:designsystem")
include(":shared:core:model")
include(":shared:core:navigation")

include(":shared:feature:dashboard")
include(":shared:feature:clients")
include(":shared:feature:settings")
include(":shared:feature:sessions")
include(":shared:feature:studio")

include(":shared:core:ui")
