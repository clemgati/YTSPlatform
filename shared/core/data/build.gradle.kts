plugins {
    id("yellowtrack.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            api(project(":shared:core:common"))
            implementation(project(":shared:core:database"))

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutinesExtensions)
            implementation(libs.koin.core)
        }

        // The session token is a credential rather than a business record, so each platform
        // keeps it where that platform keeps credentials. See `auth/SessionStore.kt` on why
        // these differ in kind and not merely in API.
        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
        }

        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        // Repository tests run against a real in-memory SQLite database on the JVM.
        // They exercise the actual SQL — the queries, the indices, the soft-delete
        // filtering — which a fake driver could not. Note that core:testing depends on
        // core:data, so these tests deliberately build their own fixtures rather than
        // reusing it, to avoid a project dependency cycle.
        getByName("desktopTest").dependencies {
            implementation(libs.sqldelight.sqliteDriver)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
