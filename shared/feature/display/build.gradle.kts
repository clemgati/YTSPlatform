plugins {
    id("yellowtrack.kmp.feature")
}

kotlin {
    sourceSets {
        getByName("desktopTest").dependencies {
            // Only to read the code back off the rendered screen. This is the one screen
            // nobody is looking at while it works, so the test does what a guest's phone
            // does rather than asserting that a composable was called.
            implementation(libs.zxing.core)
        }
    }
}
