plugins {
    id("yellowtrack.kmp.compose")
}

// The design system must never know about business concepts, so it deliberately depends
// on no model or data module.
// The mark is here rather than in the screen that first used it: it is now on two screens in
// two modules, and features never depend on each other. Public so they can both reach it.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.yellowtrack.platform.core.designsystem.resources"
}

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
