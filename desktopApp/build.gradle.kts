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

        // The release build minifies, and ProGuard fails on any reference it cannot
        // resolve. See proguard-rules.pro for what is deliberately unresolvable.
        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            // jpackage builds a trimmed JRE with jlink, and the default set does not
            // include java.sql — so the packaged application started, opened its database
            // and died on NoClassDefFoundError: java/sql/DriverManager, before drawing a
            // window.
            //
            // Everything, rather than naming java.sql and moving on. The modules an
            // application needs are discovered by executing the path that needs them, and
            // the paths that cannot be exercised here are the ones that matter most: TLS
            // needs jdk.crypto.ec to negotiate with Let's Encrypt, and that only fails at
            // the moment a studio signs in. Naming modules one at a time means finding the
            // next one in front of somebody.
            //
            // It costs about 40MB in the installer, which is a fair price for not
            // discovering a missing module in the field.
            includeAllModules = true
            // What a person sees: the installer, the application folder, the dock. The
            // bundle identifier below stays reverse-DNS; this does not.
            packageName = "Yellow Track"
            packageVersion = "1.0.0"

            // Each platform wants its own container for the same mark: an .icns for
            // macOS, a multi-size .ico for Windows, and a plain PNG for Linux. All three
            // are cut from the same artwork and committed, because the tools that build
            // them are not on every machine that builds this.
            macOS {
                // Stated rather than left to default from packageName, which is now a
                // display name with a space in it and would make an invalid identifier.
                bundleID = "com.yellowtrack.platform"
                iconFile.set(project.file("icons/yellowtrack.icns"))
            }
            windows { iconFile.set(project.file("icons/yellowtrack.ico")) }
            linux { iconFile.set(project.file("icons/yellowtrack.png")) }
        }
    }
}
