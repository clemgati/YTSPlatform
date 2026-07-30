plugins {
    id("yellowtrack.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Invoices and quotes are rendered from the domain here, so the screen only
            // has to decide where to send them.
            implementation(project(":shared:core:export"))
        }

        getByName("desktopTest").dependencies {
            implementation(project(":shared:core:export"))
        }
    }
}
