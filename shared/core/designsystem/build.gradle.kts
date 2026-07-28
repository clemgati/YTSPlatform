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
    }
}
