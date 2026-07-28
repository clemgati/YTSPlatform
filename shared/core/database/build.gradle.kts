plugins {
    id("yellowtrack.kmp.library")
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:common"))
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutinesExtensions)
        }

        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
        }

        getByName("desktopMain").dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }

        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }

        wasmJsMain.dependencies {
            implementation(libs.sqldelight.webWorkerDriver)
            // w3c DOM bindings (Worker) are no longer part of the Kotlin/Wasm stdlib.
            implementation(libs.kotlinx.browser)
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }

        // Migration tests run on the JVM against the committed schema snapshots.
        getByName("desktopTest").dependencies {
            implementation(libs.sqldelight.sqliteDriver)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("YellowTrackDatabase") {
            packageName.set("com.yellowtrack.platform.core.database")

            // The web-worker driver is asynchronous, so the generated API must be too.
            // This makes every query suspend on all targets, which keeps one API shape
            // across Android, iOS, desktop, and web rather than forking the data layer.
            generateAsync.set(true)

            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
