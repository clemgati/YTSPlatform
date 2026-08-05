plugins {
    id("yellowtrack.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The studio's own export is written out through the same sink a call sheet is,
            // so "where has my file gone" has one answer per platform rather than two.
            implementation(project(":shared:core:export"))
        }

        getByName("desktopTest").dependencies {
            implementation(project(":shared:core:export"))
        }
    }
}
