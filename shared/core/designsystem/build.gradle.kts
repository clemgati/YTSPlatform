plugins {
    id("yellowtrack.kmp.compose")
}

// The design system must never know about business concepts, so it deliberately depends
// on no model or data module.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.icons.tabler.outline)
        }

        getByName("desktopTest").dependencies {
            // Only to read a code back off the canvas. A generator that paints a plausible
            // grid no phone can decode is the failure worth testing for, and the only way to
            // test it is to decode what was actually painted.
            implementation(libs.zxing.core)
        }
    }
}
