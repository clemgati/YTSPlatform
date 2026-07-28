plugins {
    `kotlin-dsl`
}

// Plugin markers, rather than the plugin implementation artifacts, so the
// coordinates stay stable across Android Gradle Plugin releases.
dependencies {
    // Puts the generated `LibrariesForLibs` accessors on the compile classpath of the
    // precompiled script plugins, which do not receive the `libs` accessor automatically.
    compileOnly(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(
        "org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:" +
            libs.versions.kotlin.get(),
    )
    implementation(
        "com.android.kotlin.multiplatform.library:" +
            "com.android.kotlin.multiplatform.library.gradle.plugin:" +
            libs.versions.agp.get(),
    )
    implementation(
        "org.jetbrains.compose:org.jetbrains.compose.gradle.plugin:" +
            libs.versions.composeMultiplatform.get(),
    )
    implementation(
        "org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:" +
            libs.versions.kotlin.get(),
    )
}
