plugins {
    id("yellowtrack.kmp.library")
}

// Depends on `core:model` and nothing else. A document is built from the domain and
// rendered to a string; keeping Compose and SQLDelight out means the same renderer can
// run on the Ktor server when documents are mailed rather than saved.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            // FileProvider: handing another application a file:// URI has thrown since
            // Android 7.
            implementation(libs.androidx.core.ktx)
        }

        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}
