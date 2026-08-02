plugins {
    id("yellowtrack.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

// The client half of the wire. Depends on `core:data` for the `SyncTransport` contract it
// implements and on `core:model` for the envelopes — which are the same envelopes the
// server's routes are compiled against, so a change to either side is a build failure
// rather than a field that quietly stops crossing.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:data"))
            api(project(":shared:core:model"))

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.koin.core)
        }

        // One engine per target: Ktor has none that runs on all four. Each is the
        // platform's own stack rather than a bundled one, so TLS, proxies and certificate
        // pinning behave the way the rest of the device does.
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        getByName("desktopMain").dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
