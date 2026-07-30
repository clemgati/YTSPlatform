plugins {
    id("yellowtrack.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }

        wasmJsMain.dependencies {
            // The browser has no IANA timezone database that kotlinx-datetime can read, so
            // `TimeZone.of("Europe/London")` throws without this. Every session carries a
            // zone id, which makes it the difference between the shoot day rendering and
            // the web build failing on any screen that shows a time.
            api(npm("@js-joda/timezone", "2.22.0"))
        }
    }
}
