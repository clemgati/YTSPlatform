plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        outputToConsole.set(true)
        ignoreFailures.set(false)

        filter {
            exclude { fileTreeElement ->
                val path = fileTreeElement.file.invariantSeparatorsPath

                path.contains("/build/") ||
                        path.contains("/generated/")
            }
        }
    }
}
// One place the version is declared, so the sidebar cannot go stale again. `AppInfo` was
// typed by hand and read three milestones behind twice — the second time found by running
// the application and looking at it. Generated from here now.
allprojects {
    version = "1.4.1"
}
