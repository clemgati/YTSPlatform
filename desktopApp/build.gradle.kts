import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared:app"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.yellowtrack.platform.DesktopAppKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.yellowtrack.platform"
            packageVersion = "1.0.0"

            // Each platform wants its own container for the same mark: an .icns for
            // macOS, a multi-size .ico for Windows, and a plain PNG for Linux. All three
            // are cut from the same artwork and committed, because the tools that build
            // them are not on every machine that builds this.
            macOS { iconFile.set(project.file("icons/yellowtrack.icns")) }
            windows { iconFile.set(project.file("icons/yellowtrack.ico")) }
            linux { iconFile.set(project.file("icons/yellowtrack.png")) }
        }
    }
}
