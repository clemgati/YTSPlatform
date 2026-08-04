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

            // Each platform wants its own container for the same mark. Only macOS is
            // generated here, because .icns is built with a macOS tool; Windows will need
            // an .ico adding when there is a machine to build one on, and Linux takes the
            // PNG directly.
            macOS { iconFile.set(project.file("icons/yellowtrack.icns")) }
            linux { iconFile.set(project.file("icons/yellowtrack.png")) }
        }
    }
}
