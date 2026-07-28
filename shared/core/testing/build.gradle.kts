plugins {
    id("yellowtrack.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            api(project(":shared:core:data"))
            api(project(":shared:core:common"))
            api(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
