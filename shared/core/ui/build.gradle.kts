plugins {
    id("yellowtrack.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core:designsystem"))
        }
    }
}
