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
            // The web-worker driver runs SQLite (compiled to wasm by sql.js) inside a
            // dedicated worker. The worker script and sql.js both come from npm and are
            // bundled by webpack; the sql-wasm.wasm binary is copied to the served root
            // by webApp/webpack.config.d/sqljs.js. Keep the worker version pinned to the
            // SQLDelight version so the message protocol stays in sync.
            implementation(npm("@cashapp/sqldelight-sqljs-worker", libs.versions.sqldelight.get()))
            implementation(npm("sql.js", "1.13.0"))
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
