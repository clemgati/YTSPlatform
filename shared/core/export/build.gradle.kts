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

        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}
