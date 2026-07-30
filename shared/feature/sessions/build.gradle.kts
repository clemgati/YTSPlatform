plugins {
    id("yellowtrack.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The call sheet is built from the domain and rendered to a string here, so
            // the screen only has to decide where to send it.
            implementation(project(":shared:core:export"))
        }

        getByName("desktopTest").dependencies {
            implementation(project(":shared:core:export"))
        }
    }
}
