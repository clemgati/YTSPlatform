plugins {
    id("yellowtrack.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The sign-up code is saved through the same sink a call sheet goes through, so
            // "where has my file gone" has one answer per platform rather than two.
            implementation(project(":shared:core:export"))
        }

        getByName("desktopTest").dependencies {
            implementation(project(":shared:core:export"))
        }
    }
}
