plugins {
    id("yellowtrack.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

// Deliberately depends on neither Compose nor SQLDelight. That constraint is what allows
// the Ktor server to depend on this module, so that one definition of every entity is
// compiled into both the client and the server.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:common"))
        }
    }
}
