import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    // The same wiring the studio application uses — client, session store, clock. A second
    // copy of that would be a second place for the server URL to be wrong.
    implementation(project(":shared:app"))
    implementation(project(":shared:feature:display"))
    // Signing in is the same screen. A companion application with its own sign-in form would
    // be a second thing to keep correct about passwords, resets and deleted accounts.
    implementation(project(":shared:feature:auth"))
    implementation(project(":shared:core:designsystem"))
    implementation(project(":shared:core:data"))
    implementation(project(":shared:core:common"))

    implementation(libs.koin.android)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.runtime)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // Only to check the graph. A missing binding is invisible to the compiler and shows up
    // as a crash on a tablet in a venue, which is the worst place and time to find it.
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlin.testJunit)
}

android {
    namespace = "com.yellowtrack.platform.display"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        // A different identifier from the studio application, deliberately: a studio installs
        // both on the same tablet — the one it works from and the one on the table — and two
        // applications sharing an identifier cannot coexist.
        applicationId = "com.yellowtrack.platform.display"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()

        // Derived exactly as the studio application's is, from the same three numbers.
        versionCode =
            project.version
                .toString()
                .split(".")
                .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
                .let { (it + listOf(0, 0, 0)).take(3) }
                .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

        versionName = project.version.toString()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
