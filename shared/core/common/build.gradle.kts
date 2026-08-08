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

        // So logFailure can go through SLF4J rather than straight to standard error, which
        // is what puts it in the desktop build's log file as well as its console. The
        // binding is supplied by the application module; this is the interface only.
        getByName("desktopMain").dependencies {
            implementation(libs.slf4j.api)
        }

        // The volume inspector touches a real disk, so its test does too.
        getByName("desktopTest").dependencies {
            implementation(libs.kotlinx.coroutines.test)
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
